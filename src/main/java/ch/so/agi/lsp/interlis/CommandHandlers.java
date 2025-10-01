package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ch.interlis.ili2c.metamodel.TransferDescription;

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

    /** Compile an .ili file and return an HTML page with the generated Mermaid diagram. */
    public CompletableFuture<Object> generateUml(String fileUriOrPath) {
        if (server.getClient() != null) {
            server.getClient().logMessage(new MessageParams(
                MessageType.Log, "generateUml called for " + fileUriOrPath));
        }

        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, fileUriOrPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        server.clearOutput();
        server.logToClient(outcome.getLogText());

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("ili2c did not return a TransferDescription"));
        }

        try {
            String mermaid = Ili2Mermaid.render(td);
            String html = MermaidHtmlRenderer.render(mermaid);
            return CompletableFuture.completedFuture(html);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
