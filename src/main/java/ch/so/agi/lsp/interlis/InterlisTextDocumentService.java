package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class InterlisTextDocumentService implements TextDocumentService {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisTextDocumentService.class);

    private final InterlisLanguageServer server;

    public InterlisTextDocumentService(InterlisLanguageServer server) {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        validateAndPublish(uri, "didOpen");
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        // For typing responsiveness you might debounce; for now run directly
        validateAndPublish(uri, "didChange");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        // Often a good moment to do an authoritative compile based on on-disk state
        validateAndPublish(uri, "didSave");
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        server.publishDiagnostics(uri, Collections.emptyList());
    }

    private void validateAndPublish(String documentUri, String source) {
        try {
            String pathOrUri = toFilesystemPathIfPossible(documentUri);
            ClientSettings cfg = server.getClientSettings();
            LOG.debug("Validating [{}] via {} with modelRepositories={}", pathOrUri, source,
                    cfg.getModelRepositoriesList());

            InterlisValidator validator = new InterlisValidator(cfg);
            InterlisValidator.ValidationOutcome outcome = validator.validate(pathOrUri);

            server.publishDiagnostics(documentUri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
        } catch (Exception ex) {
            LOG.error("Validation failed for {} (source={})", documentUri, source, ex);
        }
    }

    private static String toFilesystemPathIfPossible(String uriOrPath) {
        if (uriOrPath == null) return null;
        if (uriOrPath.startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(uriOrPath));
                return p.toString();
            } catch (Exception ignored) { /* leave as-is */ }
        }
        return uriOrPath;
    }
}
