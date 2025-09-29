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
    public CompletableFuture<Object> compile(String fileUriOrPath) {
        if (server.getClient() != null) {
            server.getClient().logMessage(new MessageParams(
                MessageType.Log, "compile called for " + fileUriOrPath));
        }
        
        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, fileUriOrPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        server.clearOutput();
        server.logToClient(outcome.getLogText());
        return CompletableFuture.completedFuture(outcome.getLogText());
    }
}
