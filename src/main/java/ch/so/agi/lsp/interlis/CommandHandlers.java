package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Diagnostic;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Central place for workspace/executeCommand handlers. */
public class CommandHandlers {
    private final InterlisLanguageServer server;

    public CommandHandlers(InterlisLanguageServer server) {
        this.server = server;
    }

    /** Validate an .ili file and return textual log; also publishes diagnostics. */
    public CompletableFuture<Object> validate(String fileUriOrPath) {
        InterlisValidator validator = new InterlisValidator();
        InterlisValidator.ValidationOutcome outcome = validator.validate(fileUriOrPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        return CompletableFuture.completedFuture(outcome.getLogText());
    }
}
