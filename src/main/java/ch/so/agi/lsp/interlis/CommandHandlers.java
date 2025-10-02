package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.TransferDescription;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

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

    public CompletableFuture<String> exportDocx(String fileUriOrPath, String titleOverride) {
        if (server.getClient() != null) {
            server.getClient().logMessage(new MessageParams(
                MessageType.Log, "exportDocx called for " + fileUriOrPath));
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
            String title = (titleOverride != null && !titleOverride.isBlank())
                    ? titleOverride
                    : deriveDocumentTitle(fileUriOrPath);
            byte[] bytes = InterlisDocxExporter.renderDocx(td, title);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return CompletableFuture.completedFuture(base64);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String deriveDocumentTitle(String fileUriOrPath) {
        if (fileUriOrPath == null || fileUriOrPath.isBlank()) {
            return "INTERLIS";
        }
        try {
            Path p = Paths.get(fileUriOrPath);
            Path name = p.getFileName();
            if (name != null) {
                return name.toString();
            }
        } catch (Exception ignore) {
            // fall back to raw input
        }
        return fileUriOrPath;
    }
}
