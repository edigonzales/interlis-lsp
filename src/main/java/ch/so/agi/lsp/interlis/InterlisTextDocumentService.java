package ch.so.agi.lsp.interlis;

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

    InterlisTextDocumentService(InterlisLanguageServer server,
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

    void onClientSettingsUpdated(ClientSettings settings) {
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
        String uri = params.getTextDocument().getUri();
        String pathOrUri = toFilesystemPathIfPossible(uri);
        // Any edit invalidates the transfer description cached for this document. Features
        // such as completion, rename or document symbols combine the live text tracked by
        // {@link DocumentTracker} with the cached {@link Ili2cUtil.CompilationOutcome}.
        // Keeping the old compilation result after a change would leave those features with
        // stale model information until the file is saved, so we drop the cache entry here
        // and let the next request trigger a fresh compile if it needs one.
        compilationCache.invalidate(pathOrUri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        // Often a good moment to do an authoritative compile based on on-disk state
        compileAndPublish(uri, "didSave", true);
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
            String originalText = readDocument(uri);

            ClientSettings cfg = server.getClientSettings();
            String formattedText = Ili2cUtil.prettyPrint(cfg, pathOrUri);

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

                Ili2cUtil.CompilationOutcome outcome = compilationCache.get(pathOrUri);
                if (outcome == null || outcome.getTransferDescription() == null) {
                    outcome = compiler.apply(server.getClientSettings(), pathOrUri);
                    compilationCache.put(pathOrUri, outcome);
                }

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
        compileAndPublish(documentUri, source, false);
    }

    private void compileAndPublish(String documentUri, String source, boolean forceRecompile) {
        try {
            String pathOrUri = toFilesystemPathIfPossible(documentUri);
            ClientSettings cfg = server.getClientSettings();
            LOG.debug("Validating [{}] via {} with modelRepositories={}", pathOrUri, source,
                    cfg.getModelRepositoriesList());

            Ili2cUtil.CompilationOutcome outcome = forceRecompile ? null : compilationCache.get(pathOrUri);
            if (forceRecompile || outcome == null || outcome.getTransferDescription() == null) {
                outcome = compiler.apply(cfg, pathOrUri);
                compilationCache.put(pathOrUri, outcome);
            }
            if (outcome == null) {
                outcome = new Ili2cUtil.CompilationOutcome(null, "", Collections.emptyList());
            }

            server.publishDiagnostics(documentUri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
            server.clearOutput();
            server.logToClient(outcome.getLogText());
        } catch (Exception ex) {
            if (CancellationUtil.isCancellation(ex)) {
                LOG.debug("Validation cancelled for {} (source={})", documentUri, source);
                throw CancellationUtil.propagateCancellation(ex);
            }
            LOG.error("Validation failed for {} (source={})", documentUri, source, ex);
            server.publishDiagnostics(documentUri, Collections.emptyList());
            server.logToClient(String.format("Validation failed for %s via %s: %s%n",
                    documentUri, source, ex.getMessage()));
        }
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
}
