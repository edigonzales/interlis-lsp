package ch.so.agi.lsp.interlis.text;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.DiagnosticsMapper;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.model.ModelDiscoveryService;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.server.RuntimeDiagnostics;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class InterlisTextDocumentService implements TextDocumentService {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisTextDocumentService.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents = new DocumentTracker();
    private final CompilationCache compilationCache;
    private final InterlisDefinitionFinder definitionFinder;
    private final ModelDiscoveryService modelDiscoveryService;
    private final InterlisCompletionProvider completionProvider;
    private final InterlisRenameProvider renameProvider;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;

    public InterlisTextDocumentService(InterlisLanguageServer server) {
        this(server, new CompilationCache(), Ili2cUtil::compile);
    }

    public InterlisTextDocumentService(InterlisLanguageServer server,
                                       CompilationCache cache,
                                       BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler) {
        this.server = server;
        this.compilationCache = cache != null ? cache : new CompilationCache();
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
        this.definitionFinder = new InterlisDefinitionFinder(server, documents, this.compilationCache);
        this.modelDiscoveryService = new ModelDiscoveryService();
        this.completionProvider = new InterlisCompletionProvider(server, documents, this.compilationCache, this.compiler, this.modelDiscoveryService);
        this.renameProvider = new InterlisRenameProvider(server, documents, this.compilationCache, this.compiler);
    }

    public void onClientSettingsUpdated(ClientSettings settings) {
        try {
            modelDiscoveryService.ensureInitialized(settings);
        } catch (Exception ex) {
            LOG.warn("Model discovery refresh failed", ex);
        }
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.open(params.getTextDocument());
        compileAndPublish(uri, "didOpen");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        documents.applyChanges(params.getTextDocument(), params.getContentChanges());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.markSaved(uri);
        compileAndPublish(uri, "didSave");
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        server.publishDiagnostics(uri, Collections.emptyList());
        documents.close(uri);
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        List<? extends TextEdit> edits = Collections.emptyList();
        try {
            String uri = params.getTextDocument().getUri();
            String pathOrUri = toFilesystemPathIfPossible(uri);
            boolean tracked = documents.isTracked(uri);
            boolean dirty = tracked && documents.isDirty(uri);
            String originalText = documents.getText(uri);
            if (originalText == null) {
                originalText = readDocument(uri);
            }

            Ili2cUtil.CompilationOutcome outcome = resolveOutcomeForInteractiveFeature(uri, pathOrUri, "formatting-fallback");
            if (dirty) {
                // Formatting from a stale saved snapshot would overwrite unsaved user edits.
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            String formattedText = Ili2cUtil.prettyPrint(outcome != null ? outcome.getTransferDescription() : null, pathOrUri);
            if (formattedText == null) {
                publishFormattingDiagnostics(uri, outcome);
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            if (formattedText != null && !formattedText.equals(originalText)) {
                TextEdit edit = new TextEdit(fullDocumentRange(originalText), formattedText);
                edits = Collections.singletonList(edit);
            }
        } catch (Exception ex) {
            if (CancellationUtil.isCancellation(ex)) {
                LOG.debug("Formatting cancelled for {}", params.getTextDocument() != null
                        ? params.getTextDocument().getUri() : "<unknown>");
            } else {
                LOG.error("Formatting failed", ex);
            }
        }
        return CompletableFuture.completedFuture(edits);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                return completionProvider.complete(params);
            } catch (RuntimeException ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                throw ex;
            }
        });
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.getText(uri);

        List<TextEdit> edits = InterlisAutoCloser.computeEdits(text, params);
        return CompletableFuture.completedFuture(edits);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                WorkspaceEdit edit = renameProvider.rename(params);
                if (edit == null) {
                    WorkspaceEdit empty = new WorkspaceEdit();
                    empty.setChanges(Collections.emptyMap());
                    return empty;
                }
                if (edit.getChanges() == null) {
                    edit.setChanges(Collections.emptyMap());
                }
                return edit;
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.error("Rename failed", ex);
                WorkspaceEdit empty = new WorkspaceEdit();
                empty.setChanges(Collections.emptyMap());
                return empty;
            }
        });
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                return definitionFinder.findDefinition(params);
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.error("Definition lookup failed", ex);
                return Either.forLeft(Collections.emptyList());
            }
        });
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                if (params == null || params.getTextDocument() == null) {
                    return Collections.emptyList();
                }

                String uri = params.getTextDocument().getUri();
                if (uri == null || uri.isBlank()) {
                    return Collections.emptyList();
                }

                String documentText = documents.getText(uri);
                if (documentText == null) {
                    documentText = readDocument(uri);
                }
                if (documentText == null) {
                    documentText = "";
                }

                String pathOrUri = toFilesystemPathIfPossible(uri);
                if (pathOrUri == null || pathOrUri.isBlank()) {
                    return Collections.emptyList();
                }

                Ili2cUtil.CompilationOutcome outcome = resolveOutcomeForInteractiveFeature(uri, pathOrUri, "documentSymbol-fallback");

                if (outcome == null || outcome.getTransferDescription() == null) {
                    return Collections.emptyList();
                }

                InterlisDocumentSymbolCollector collector = new InterlisDocumentSymbolCollector(documentText);
                List<DocumentSymbol> symbols = collector.collect(outcome.getTransferDescription());
                List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>(symbols.size());
                for (DocumentSymbol symbol : symbols) {
                    result.add(Either.forRight(symbol));
                }
                return result;
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.error("Document symbol request failed", ex);
                return Collections.emptyList();
            }
        });
    }

    private void compileAndPublish(String documentUri, String source) {
        try {
            String pathOrUri = toFilesystemPathIfPossible(documentUri);
            ClientSettings cfg = server.getClientSettings();
            LOG.debug("Validating [{}] via {} with modelRepositories={}", pathOrUri, source,
                    cfg.getModelRepositoriesList());

            server.clearOutput();
            Ili2cUtil.CompilationOutcome outcome = RuntimeDiagnostics.compile(server, compiler, cfg, pathOrUri, source);
            if (outcome == null) {
                outcome = new Ili2cUtil.CompilationOutcome(null, "", Collections.emptyList());
            }
            recordAuthoritativeOutcome(pathOrUri, outcome);

            server.publishDiagnostics(documentUri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
            server.logToClient(outcome.getLogText());
            server.notifyCompileFinished(documentUri, outcome.getTransferDescription() != null);
        } catch (Exception ex) {
            server.notifyCompileFinished(documentUri, false);
            if (CancellationUtil.isCancellation(ex)) {
                LOG.debug("Validation cancelled for {} (source={})", documentUri, source);
                throw CancellationUtil.propagateCancellation(ex);
            }
            LOG.error("Validation failed for {} (source={})", documentUri, source, ex);
        }
    }

    public boolean isTrackedDocument(String uriOrPath) {
        return documents.isTracked(toDocumentUriIfPossible(uriOrPath));
    }

    public boolean isDocumentDirty(String uriOrPath) {
        return documents.isDirty(toDocumentUriIfPossible(uriOrPath));
    }

    public Ili2cUtil.CompilationOutcome getLastSuccessfulCompilation(String uriOrPath) {
        return compilationCache.getSuccessful(toFilesystemPathIfPossible(uriOrPath));
    }

    public Ili2cUtil.CompilationOutcome getLastSavedCompilationAttempt(String uriOrPath) {
        return compilationCache.getSavedAttempt(toFilesystemPathIfPossible(uriOrPath));
    }

    public void rememberSavedCompilationOutcome(String uriOrPath, Ili2cUtil.CompilationOutcome outcome) {
        recordAuthoritativeOutcome(toFilesystemPathIfPossible(uriOrPath), outcome);
    }

    public static String toFilesystemPathIfPossible(String uriOrPath) {
        if (uriOrPath == null) return null;
        if (uriOrPath.startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(uriOrPath));
                return p.toString();
            } catch (Exception ignored) { /* leave as-is */ }
        }
        return uriOrPath;
    }

    public static String toDocumentUriIfPossible(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.isBlank()) {
            return null;
        }
        if (uriOrPath.startsWith("file:")) {
            return uriOrPath;
        }
        try {
            return Paths.get(uriOrPath).toUri().toString();
        } catch (Exception ex) {
            return uriOrPath;
        }
    }

    public static String readDocument(String uriOrPath) throws Exception {
        if (uriOrPath == null) {
            return "";
        }
        if (uriOrPath.startsWith("file:")) {
            return Files.readString(Paths.get(URI.create(uriOrPath)));
        }
        return Files.readString(Paths.get(uriOrPath));
    }

    private static Range fullDocumentRange(String text) {
        int line = 0;
        int column = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\r') {
                continue;
            }
            if (ch == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        Position start = new Position(0, 0);
        Position end = new Position(line, column);
        return new Range(start, end);
    }

    private void publishFormattingDiagnostics(String documentUri, Ili2cUtil.CompilationOutcome outcome) {
        if (documentUri == null || outcome == null || outcome.getMessages() == null || outcome.getMessages().isEmpty()) {
            return;
        }

        try {
            server.publishDiagnostics(documentUri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
        } catch (Exception ex) {
            LOG.debug("Unable to publish formatting diagnostics for {}", documentUri, ex);
        }
    }

    private Ili2cUtil.CompilationOutcome resolveOutcomeForInteractiveFeature(String documentUri,
                                                                             String pathOrUri,
                                                                             String compileSource) {
        boolean tracked = documents.isTracked(documentUri);
        boolean dirty = tracked && documents.isDirty(documentUri);

        if (dirty) {
            return compilationCache.getSuccessful(pathOrUri);
        }

        Ili2cUtil.CompilationOutcome outcome = firstOutcome(
                compilationCache.getSavedAttempt(pathOrUri),
                compilationCache.getSuccessful(pathOrUri));
        if (outcome != null || tracked) {
            return outcome;
        }

        Ili2cUtil.CompilationOutcome compiled = RuntimeDiagnostics.compile(
                server,
                compiler,
                server.getClientSettings(),
                pathOrUri,
                compileSource);
        recordAuthoritativeOutcome(pathOrUri, compiled);
        return compiled;
    }

    private void recordAuthoritativeOutcome(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        if (pathOrUri == null || pathOrUri.isBlank() || outcome == null) {
            return;
        }
        compilationCache.putSavedAttempt(pathOrUri, outcome);
        compilationCache.putSuccessful(pathOrUri, outcome);
    }

    private static Ili2cUtil.CompilationOutcome firstOutcome(Ili2cUtil.CompilationOutcome primary,
                                                             Ili2cUtil.CompilationOutcome fallback) {
        return primary != null ? primary : fallback;
    }
}
