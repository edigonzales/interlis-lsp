package ch.so.agi.lsp.interlis.workspace;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.diagram.Ili2GraphML;
import ch.so.agi.lsp.interlis.diagram.Ili2Mermaid;
import ch.so.agi.lsp.interlis.diagram.Ili2PlantUml;
import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.export.docx.InterlisDocxExporter;
import ch.so.agi.lsp.interlis.export.html.InterlisHtmlExporter;
import ch.so.agi.lsp.interlis.export.html.MermaidHtmlRenderer;
import ch.so.agi.lsp.interlis.export.html.PlantUmlHtmlRenderer;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.server.RuntimeDiagnostics;
import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import ch.interlis.ili2c.metamodel.TransferDescription;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import org.eclipse.lsp4j.Diagnostic;
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
        if (handleBlankSource(fileUriOrPath, "compile-command", true)) {
            return CompletableFuture.completedFuture(blankCompileInfoMessage());
        }
        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "compile-command", true);
        return CompletableFuture.completedFuture(outcome.getLogText());
    }

    /** Compile an .ili file and return an HTML page with the generated Mermaid diagram. */
    public CompletableFuture<Object> generateUml(String fileUriOrPath) {
        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        if (handleBlankSource(fileUriOrPath, "generateUml", true)) {
            return CompletableFuture.failedFuture(blankDiagramFailure());
        }
        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "generateUml", true);

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

    /** Compile an .ili file and return an HTML page with the generated PlantUML diagram. */
    public CompletableFuture<Object> generatePlantUml(String fileUriOrPath) {
        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        if (handleBlankSource(fileUriOrPath, "generatePlantUml", true)) {
            return CompletableFuture.failedFuture(blankDiagramFailure());
        }
        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "generatePlantUml", true);

        String displayPath = firstNonBlank(filesystemPath, fileUriOrPath);

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
        }

        try {
            String plant = Ili2PlantUml.renderSource(td);
            String html = PlantUmlHtmlRenderer.render(plant);
            return CompletableFuture.completedFuture(html);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<String> exportGraphml(String fileUriOrPath) {
        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        if (handleBlankSource(fileUriOrPath, "exportGraphml", true)) {
            return CompletableFuture.failedFuture(blankExportFailure());
        }
        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "exportGraphml", true);

        String displayPath = firstNonBlank(filesystemPath, fileUriOrPath);

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
        }

        try {
            String graphml = Ili2GraphML.render(td);
            return CompletableFuture.completedFuture(graphml);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<InterlisDiagramModel.DiagramModel> exportDiagramModel(String fileUriOrPath) {
        RuntimeDiagnostics.logDiagramRequest(server, fileUriOrPath);

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        String displayPath = firstNonBlank(filesystemPath, fileUriOrPath);
        InterlisTextDocumentService textService = server.getInterlisTextDocumentService();
        boolean tracked = textService.isTrackedDocument(fileUriOrPath);
        boolean dirty = tracked && textService.isDocumentDirty(fileUriOrPath);

        if (dirty) {
            Ili2cUtil.CompilationOutcome outcome = textService.getLastSuccessfulCompilation(fileUriOrPath);
            if (outcome == null || outcome.getTransferDescription() == null) {
                return CompletableFuture.failedFuture(diagramDirtyFailure(displayPath));
            }
            return renderDiagramModel(outcome);
        }

        if (textService.isBlankSource(fileUriOrPath)) {
            return CompletableFuture.failedFuture(blankDiagramFailure());
        }

        if (tracked) {
            Ili2cUtil.CompilationOutcome outcome = resolveTrackedDiagramOutcome(
                    textService.getLastSavedCompilationAttempt(fileUriOrPath),
                    textService.getLastSuccessfulCompilation(fileUriOrPath));
            if (outcome != null) {
                if (outcome.getTransferDescription() == null) {
                    return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
                }
                return renderDiagramModel(outcome);
            }
        }

        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "exportDiagramModel-fallback", false);

        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return CompletableFuture.failedFuture(compilerFailure(displayPath, outcome));
        }

        return renderDiagramModel(outcome);
    }

    public CompletableFuture<String> exportDocx(String fileUriOrPath, String titleOverride) {
        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        if (handleBlankSource(fileUriOrPath, "exportDocx", true)) {
            return CompletableFuture.failedFuture(blankExportFailure());
        }
        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "exportDocx", true);

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
        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        if (handleBlankSource(fileUriOrPath, "exportHtml", true)) {
            return CompletableFuture.failedFuture(blankExportFailure());
        }
        Ili2cUtil.CompilationOutcome outcome = compileAndPublish(fileUriOrPath, "exportHtml", true);

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

    private Ili2cUtil.CompilationOutcome compileAndPublish(String fileUriOrPath, String source, boolean emitCompileFinished) {
        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        ClientSettings cfg = server.getClientSettings();
        try {
            server.clearOutput();
            Ili2cUtil.CompilationOutcome outcome = RuntimeDiagnostics.compile(server, Ili2cUtil::compile, cfg, filesystemPath, source);
            server.getInterlisTextDocumentService().rememberSavedCompilationOutcome(fileUriOrPath, outcome);

            List<Diagnostic> diagnostics = server.getInterlisTextDocumentService()
                    .buildCompilePublishDiagnostics(fileUriOrPath, outcome);
            server.publishDiagnostics(fileUriOrPath, diagnostics);
            server.logToClient(outcome.getLogText());
            if (emitCompileFinished) {
                server.notifyCompileFinished(fileUriOrPath, outcome != null && outcome.getTransferDescription() != null);
            }
            return outcome;
        } catch (RuntimeException ex) {
            if (emitCompileFinished) {
                server.notifyCompileFinished(fileUriOrPath, false);
            }
            throw ex;
        }
    }

    private boolean handleBlankSource(String fileUriOrPath, String source, boolean emitCompileFinished) {
        InterlisTextDocumentService textService = server.getInterlisTextDocumentService();
        if (!textService.isBlankSource(fileUriOrPath)) {
            return false;
        }

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileUriOrPath);
        RuntimeDiagnostics.logSkippedCompile(server, source, filesystemPath, InterlisTextDocumentService.BLANK_SOURCE_REASON);
        server.publishDiagnostics(fileUriOrPath, List.of());
        if (emitCompileFinished) {
            server.notifyCompileFinished(fileUriOrPath, false);
        }
        return true;
    }

    private CompletableFuture<InterlisDiagramModel.DiagramModel> renderDiagramModel(Ili2cUtil.CompilationOutcome outcome) {
        try {
            InterlisDiagramModel.DiagramModel model = InterlisDiagramModel.render(outcome.getTransferDescription());
            return CompletableFuture.completedFuture(model);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Ili2cUtil.CompilationOutcome firstOutcome(Ili2cUtil.CompilationOutcome primary,
                                                             Ili2cUtil.CompilationOutcome fallback) {
        return primary != null ? primary : fallback;
    }

    private static Ili2cUtil.CompilationOutcome resolveTrackedDiagramOutcome(Ili2cUtil.CompilationOutcome savedAttempt,
                                                                             Ili2cUtil.CompilationOutcome successful) {
        if (savedAttempt != null && savedAttempt.getTransferDescription() != null) {
            return savedAttempt;
        }
        if (successful != null) {
            return successful;
        }
        return savedAttempt;
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

    static ResponseErrorException diagramDirtyFailure(String fileUriOrPath) {
        String message = "Save the file to refresh the diagram";
        if (fileUriOrPath != null && !fileUriOrPath.isBlank()) {
            message = message + " (" + fileUriOrPath + ")";
        }
        ResponseError error = new ResponseError(ResponseErrorCode.InternalError, message, null);
        return new ResponseErrorException(error);
    }

    static ResponseErrorException blankDiagramFailure() {
        ResponseError error = new ResponseError(
                ResponseErrorCode.InternalError,
                InterlisTextDocumentService.BLANK_SOURCE_MESSAGE + " Add INTERLIS content and save to render the diagram.",
                null);
        return new ResponseErrorException(error);
    }

    static ResponseErrorException blankExportFailure() {
        ResponseError error = new ResponseError(
                ResponseErrorCode.InternalError,
                InterlisTextDocumentService.BLANK_SOURCE_MESSAGE + " Add INTERLIS content and save before exporting.",
                null);
        return new ResponseErrorException(error);
    }

    static String blankCompileInfoMessage() {
        return InterlisTextDocumentService.BLANK_SOURCE_MESSAGE + " Add INTERLIS content and save before compiling.";
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
