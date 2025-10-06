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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            cancelChecker.checkCanceled();
            try {
                return performRename(params);
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.warn("Rename request failed", ex);
                return new WorkspaceEdit();
            }
        });
    }

    private WorkspaceEdit performRename(RenameParams params) throws Exception {
        if (params == null || params.getTextDocument() == null) {
            return new WorkspaceEdit();
        }

        String uri = params.getTextDocument().getUri();
        if (uri == null || uri.isBlank()) {
            return new WorkspaceEdit();
        }

        String newName = params.getNewName();
        if (newName == null || newName.isBlank()) {
            return new WorkspaceEdit();
        }

        String text = documents.getText(uri);
        if (text == null) {
            text = readDocument(uri);
        }
        if (text == null || text.isBlank()) {
            return new WorkspaceEdit();
        }

        Position position = params.getPosition();
        if (position == null) {
            return new WorkspaceEdit();
        }
        int offset = DocumentTracker.toOffset(text, position);
        IdentifierBounds bounds = findIdentifierBounds(text, offset);
        if (bounds == null) {
            return new WorkspaceEdit();
        }

        String oldName = text.substring(bounds.start(), bounds.end());
        if (oldName.equals(newName)) {
            return new WorkspaceEdit();
        }

        List<TextEdit> edits = collectRenameEdits(text, oldName, newName);
        if (edits.isEmpty()) {
            return new WorkspaceEdit();
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        Map<String, List<TextEdit>> changes = new HashMap<>();
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    private static IdentifierBounds findIdentifierBounds(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        int length = text.length();
        int safeOffset = Math.max(0, Math.min(offset, length));

        int start = safeOffset;
        while (start > 0 && isIdentifierCharacter(text.charAt(start - 1))) {
            start--;
        }

        int end = safeOffset;
        while (end < length && isIdentifierCharacter(text.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }

        return new IdentifierBounds(start, end);
    }

    private static boolean isIdentifierCharacter(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static List<TextEdit> collectRenameEdits(String text, String oldName, String newName) {
        List<TextEdit> edits = new ArrayList<>();
        if (text == null || text.isEmpty() || oldName == null || oldName.isBlank()) {
            return edits;
        }

        int index = text.indexOf(oldName);
        while (index >= 0) {
            int end = index + oldName.length();
            if (isWholeIdentifierMatch(text, index, end)) {
                Range range = new Range(DocumentTracker.positionAt(text, index),
                        DocumentTracker.positionAt(text, end));
                edits.add(new TextEdit(range, newName));
            }
            index = text.indexOf(oldName, index + oldName.length());
        }
        return edits;
    }

    private static boolean isWholeIdentifierMatch(String text, int start, int end) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (start > 0) {
            char before = text.charAt(start - 1);
            if (isIdentifierCharacter(before)) {
                return false;
            }
        }
        if (end < text.length()) {
            char after = text.charAt(end);
            if (isIdentifierCharacter(after)) {
                return false;
            }
        }
        return true;
    }

    private record IdentifierBounds(int start, int end) {
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
        }
    }

    static String toFilesystemPathIfPossible(String uriOrPath) {
        if (uriOrPath == null) return null;
        if (uriOrPath.startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(uriOrPath));
                return p.toString();
            } catch (Exception ignored) { /* leave as-is */ }
        }
        return uriOrPath;
    }

    static String readDocument(String uriOrPath) throws Exception {
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
