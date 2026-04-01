package ch.so.agi.lsp.interlis.text;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.DiagnosticsMapper;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.live.DocumentSnapshot;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.live.LiveParseResult;
import ch.so.agi.lsp.interlis.model.ModelDiscoveryService;
import ch.interlis.ili2c.metamodel.TransferDescription;
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
    public static final String BLANK_SOURCE_REASON = "blank-document";
    public static final String BLANK_SOURCE_MESSAGE = "Source file is empty.";

    private final InterlisLanguageServer server;
    private final DocumentTracker documents = new DocumentTracker();
    private final LiveAnalysisService liveAnalysis = new LiveAnalysisService();
    private final CompilationCache compilationCache;
    private final InterlisDefinitionFinder definitionFinder;
    private final ModelDiscoveryService modelDiscoveryService;
    private final InterlisCompletionProvider completionProvider;
    private final InterlisRenameProvider renameProvider;
    private final InterlisReferencesProvider referencesProvider;
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
        this.definitionFinder = new InterlisDefinitionFinder(server, documents, this.compilationCache, this.compiler, this.liveAnalysis);
        this.modelDiscoveryService = new ModelDiscoveryService();
        this.completionProvider = new InterlisCompletionProvider(server, documents, this.compilationCache, this.compiler, this.modelDiscoveryService, this.liveAnalysis);
        this.renameProvider = new InterlisRenameProvider(server, documents, this.compilationCache, this.compiler, this.liveAnalysis);
        this.referencesProvider = new InterlisReferencesProvider(server, documents, this.compilationCache, this.compiler, this.liveAnalysis);
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
        if (skipBlankAuthoritativeCompile(uri, "didOpen")) {
            return;
        }
        liveAnalysis.analyze(currentSnapshot(uri));
        compileAndPublish(uri, "didOpen");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        documents.applyChanges(params.getTextDocument(), params.getContentChanges());
        String uri = params.getTextDocument() != null ? params.getTextDocument().getUri() : null;
        DocumentSnapshot snapshot = currentSnapshot(uri);
        String pathOrUri = toFilesystemPathIfPossible(uri);
        TransferDescription authoritativeTd = InteractiveCompilationResolver.resolveTransferDescriptionForInteractiveFeature(
                server,
                documents,
                compilationCache,
                compiler,
                uri,
                pathOrUri,
                "live-diagnostics-fallback");
        liveAnalysis.schedule(snapshot, authoritativeTd, result -> publishLiveDiagnosticsIfCurrent(snapshot, result));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.markSaved(uri);
        if (skipBlankAuthoritativeCompile(uri, "didSave")) {
            return;
        }
        liveAnalysis.analyze(currentSnapshot(uri));
        compileAndPublish(uri, "didSave");
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        server.publishDiagnostics(uri, Collections.emptyList());
        documents.close(uri);
        liveAnalysis.remove(uri);
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
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                return referencesProvider.references(params);
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.error("References lookup failed", ex);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                return renameProvider.prepareRename(params);
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.error("Prepare rename failed", ex);
                return null;
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

            server.publishDiagnostics(documentUri, buildCompilePublishDiagnostics(documentUri, outcome));
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

    private boolean skipBlankAuthoritativeCompile(String documentUri, String source) {
        if (!isBlankSource(documentUri)) {
            return false;
        }

        String pathOrUri = toFilesystemPathIfPossible(documentUri);
        RuntimeDiagnostics.logSkippedCompile(server, source, pathOrUri, BLANK_SOURCE_REASON);
        compilationCache.invalidate(pathOrUri);
        liveAnalysis.remove(documentUri);
        server.publishDiagnostics(documentUri, Collections.emptyList());
        server.notifyCompileFinished(documentUri, false);
        return true;
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

    public boolean isBlankSource(String uriOrPath) {
        String text = resolveSourceText(uriOrPath);
        return text != null && text.isBlank();
    }

    public String resolveSourceText(String uriOrPath) {
        String documentUri = toDocumentUriIfPossible(uriOrPath);
        if (documentUri != null && documents.isTracked(documentUri)) {
            String trackedText = documents.getText(documentUri);
            if (trackedText != null) {
                return trackedText;
            }
        }

        try {
            return readDocument(uriOrPath);
        } catch (Exception ex) {
            LOG.debug("Unable to read source text for {}", uriOrPath, ex);
            return null;
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
        Ili2cUtil.CompilationOutcome outcome = InteractiveCompilationResolver.resolveOutcomeForInteractiveFeature(
                server,
                documents,
                compilationCache,
                compiler,
                documentUri,
                pathOrUri,
                compileSource);
        if (outcome != null) {
            recordAuthoritativeOutcome(pathOrUri, outcome);
        }
        return outcome;
    }

    private void recordAuthoritativeOutcome(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        if (pathOrUri == null || pathOrUri.isBlank() || outcome == null) {
            return;
        }
        compilationCache.putSavedAttempt(pathOrUri, outcome);
        compilationCache.putSuccessful(pathOrUri, outcome);
    }

    private DocumentSnapshot currentSnapshot(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String text = documents.getText(uri);
        if (text == null) {
            try {
                text = readDocument(uri);
            } catch (Exception ex) {
                LOG.debug("Unable to read snapshot for {}", uri, ex);
                return null;
            }
        }
        return new DocumentSnapshot(uri, toFilesystemPathIfPossible(uri), text, documents.getVersion(uri));
    }

    private void publishLiveDiagnosticsIfCurrent(DocumentSnapshot snapshot, LiveParseResult result) {
        if (snapshot == null || result == null || snapshot.uri() == null) {
            return;
        }
        String currentText = documents.getText(snapshot.uri());
        Integer currentVersion = documents.getVersion(snapshot.uri());
        if (!documents.isDirty(snapshot.uri())) {
            return;
        }
        if (currentVersion != null && snapshot.version() != null && !currentVersion.equals(snapshot.version())) {
            return;
        }
        if (currentText != null && !currentText.equals(snapshot.text())) {
            return;
        }
        server.publishDiagnostics(snapshot.uri(), result.diagnostics());
    }

    public List<Diagnostic> buildCompilePublishDiagnostics(String uriOrPath, Ili2cUtil.CompilationOutcome outcome) {
        List<Diagnostic> diagnostics = new ArrayList<>(DiagnosticsMapper.toDiagnostics(
                outcome != null ? outcome.getMessages() : Collections.emptyList()));
        diagnostics.addAll(savedLintDiagnostics(uriOrPath, outcome));
        return List.copyOf(diagnostics);
    }

    private List<Diagnostic> savedLintDiagnostics(String uriOrPath, Ili2cUtil.CompilationOutcome outcome) {
        if (outcome == null || outcome.getTransferDescription() == null) {
            return List.of();
        }

        DocumentSnapshot snapshot = savedSnapshot(uriOrPath);
        if (snapshot == null) {
            return List.of();
        }

        try {
            LiveParseResult result = liveAnalysis.analyze(snapshot, outcome.getTransferDescription());
            if (result == null || result.diagnostics() == null || result.diagnostics().isEmpty()) {
                return List.of();
            }

            List<Diagnostic> diagnostics = new ArrayList<>();
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (!isUnusedImportLintDiagnostic(diagnostic)) {
                    continue;
                }
                Diagnostic copy = new Diagnostic(
                        diagnostic.getRange(),
                        diagnostic.getMessage(),
                        diagnostic.getSeverity(),
                        "lint");
                copy.setTags(diagnostic.getTags());
                copy.setCode(diagnostic.getCode());
                copy.setCodeDescription(diagnostic.getCodeDescription());
                copy.setRelatedInformation(diagnostic.getRelatedInformation());
                copy.setData(diagnostic.getData());
                diagnostics.add(copy);
            }
            return diagnostics.isEmpty() ? List.of() : List.copyOf(diagnostics);
        } catch (Exception ex) {
            LOG.debug("Unable to derive saved lint diagnostics for {}", uriOrPath, ex);
            return List.of();
        }
    }

    private DocumentSnapshot savedSnapshot(String uriOrPath) {
        String documentUri = toDocumentUriIfPossible(uriOrPath);
        if (documentUri != null && documents.isTracked(documentUri) && !documents.isDirty(documentUri)) {
            DocumentSnapshot snapshot = currentSnapshot(documentUri);
            if (snapshot != null) {
                return snapshot;
            }
        }

        try {
            String documentText = readDocument(uriOrPath);
            return new DocumentSnapshot(
                    documentUri != null ? documentUri : uriOrPath,
                    toFilesystemPathIfPossible(uriOrPath),
                    documentText,
                    documents.getVersion(documentUri));
        } catch (Exception ex) {
            LOG.debug("Unable to read saved snapshot for {}", uriOrPath, ex);
            return null;
        }
    }

    private static boolean isUnusedImportLintDiagnostic(Diagnostic diagnostic) {
        if (diagnostic == null || diagnostic.getSeverity() != DiagnosticSeverity.Warning) {
            return false;
        }
        if (diagnostic.getTags() == null || !diagnostic.getTags().contains(DiagnosticTag.Unnecessary)) {
            return false;
        }
        String message = diagnostic.getMessage();
        return message != null
                && message.contains("Imported model '")
                && message.contains("' is never used");
    }
}
