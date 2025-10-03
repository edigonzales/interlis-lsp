package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.TransferDescription;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

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

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);

        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, filesystemPath);
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

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);

        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, filesystemPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        server.clearOutput();
        server.logToClient(outcome.getLogText());

        String displayPath = firstNonBlank(filesystemPath, fileUriOrPath);

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
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

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);

        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, filesystemPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        server.clearOutput();
        server.logToClient(outcome.getLogText());

        String displayPath = firstNonBlank(filesystemPath, fileUriOrPath);

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
        }

        try {
            String title = (titleOverride != null && !titleOverride.isBlank())
                    ? titleOverride
                    : deriveDocumentTitle(displayPath);
            byte[] bytes = InterlisDocxExporter.renderDocx(td, title);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return CompletableFuture.completedFuture(base64);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<String> exportHtml(String fileUriOrPath, String titleOverride) {
        if (server.getClient() != null) {
            server.getClient().logMessage(new MessageParams(
                MessageType.Log, "exportHtml called for " + fileUriOrPath));
        }

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);

        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, filesystemPath);
        List<Diagnostic> diagnostics = DiagnosticsMapper.toDiagnostics(outcome.getMessages());
        server.publishDiagnostics(fileUriOrPath, diagnostics);
        server.clearOutput();
        server.logToClient(outcome.getLogText());

        String displayPath = firstNonBlank(filesystemPath, fileUriOrPath);

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
        }

        try {
            String title = (titleOverride != null && !titleOverride.isBlank())
                    ? titleOverride
                    : deriveDocumentTitle(displayPath);
            String html = InterlisHtmlExporter.renderHtml(td, title);
            return CompletableFuture.completedFuture(html);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
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

    static ResponseErrorException compilerFailure(String fileUriOrPath, Ili2cUtil.CompilationOutcome outcome) {
        String message = "ili2c did not return a TransferDescription";
        if (outcome != null) {
            message = bestErrorMessage(outcome).orElse(message);
        }

        String details = outcome != null ? outcome.getLogText() : null;
        if (fileUriOrPath != null && !fileUriOrPath.isBlank()) {
            message = message + " (" + fileUriOrPath + ")";
        }

        ResponseError error = new ResponseError(ResponseErrorCode.InternalError, message, details);
        return new ResponseErrorException(error);
    }

    private static Optional<String> bestErrorMessage(Ili2cUtil.CompilationOutcome outcome) {
        if (outcome == null || outcome.getMessages() == null) {
            return Optional.empty();
        }

        return outcome.getMessages().stream()
                .filter(msg -> msg != null && msg.getSeverity() == Ili2cUtil.Message.Severity.ERROR)
                .map(Ili2cUtil.Message::getText)
                .filter(text -> text != null && !text.isBlank())
                .findFirst();
    }
}
