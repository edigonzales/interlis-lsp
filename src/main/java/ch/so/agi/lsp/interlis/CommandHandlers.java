package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

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
        if (server.getClient() != null) {
            server.getClient().logMessage(new MessageParams(
                MessageType.Log, "validate called for " + fileUriOrPath));
        }
        
        InterlisValidator validator = new InterlisValidator();
        InterlisValidator.ValidationOutcome outcome = validator.validate(fileUriOrPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        return CompletableFuture.completedFuture(outcome.getLogText());
    }
}
