package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InterlisTextDocumentService implements TextDocumentService {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisTextDocumentService.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents = new DocumentTracker();
    private final InterlisDefinitionFinder definitionFinder;

    public InterlisTextDocumentService(InterlisLanguageServer server) {
        this.server = server;
        this.definitionFinder = new InterlisDefinitionFinder(server, documents);
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
        // Often a good moment to do an authoritative compile based on on-disk state
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
            String originalText = readDocument(uri);
            
            ClientSettings cfg = server.getClientSettings();
            String formattedText = Ili2cUtil.prettyPrint(cfg, pathOrUri);

            if (formattedText != null && !formattedText.equals(originalText)) {
                TextEdit edit = new TextEdit(fullDocumentRange(originalText), formattedText);
                edits = Collections.singletonList(edit);
            }
        } catch (Exception ex) {
            LOG.error("Formatting failed", ex);
        }
        return CompletableFuture.completedFuture(edits);
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                return definitionFinder.findDefinition(params);
            } catch (Exception ex) {
                LOG.error("Definition lookup failed", ex);
                return Either.forLeft(Collections.emptyList());
            }
        });
    }

    private void compileAndPublish(String documentUri, String source) {
        try {
            String pathOrUri = toFilesystemPathIfPossible(documentUri);
            ClientSettings cfg = server.getClientSettings();
            LOG.debug("Validating [{}] via {} with modelRepositories={}", pathOrUri, source,
                    cfg.getModelRepositoriesList());

            Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, pathOrUri);

            server.publishDiagnostics(documentUri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
            server.clearOutput();  
            server.logToClient(outcome.getLogText());
        } catch (Exception ex) {
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
