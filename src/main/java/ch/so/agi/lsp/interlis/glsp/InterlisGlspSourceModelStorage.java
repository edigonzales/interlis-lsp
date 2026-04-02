package ch.so.agi.lsp.interlis.glsp;

import java.util.Map;
import java.util.concurrent.CompletionException;

import org.eclipse.glsp.server.actions.SaveModelAction;
import org.eclipse.glsp.server.features.core.model.RequestModelAction;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.model.GModelState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.workspace.CommandHandlers;

public class InterlisGlspSourceModelStorage implements SourceModelStorage {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGlspSourceModelStorage.class);

    @Inject
    protected GModelState modelState;

    @Override
    public void loadSourceModel(RequestModelAction requestModelAction) {
        String sourceUri = sourceUri(requestModelAction);
        modelState.setProperty(InterlisGlspModelStateKeys.SOURCE_URI, sourceUri);
        modelState.clearProperty(InterlisGlspModelStateKeys.MODEL);
        modelState.clearProperty(InterlisGlspModelStateKeys.ERROR);

        if (sourceUri == null || sourceUri.isBlank()) {
            modelState.setProperty(InterlisGlspModelStateKeys.ERROR, "Missing sourceUri for diagram request.");
            return;
        }

        InterlisLanguageServer languageServer = InterlisGlspBridge.getLanguageServer();
        if (languageServer == null) {
            modelState.setProperty(InterlisGlspModelStateKeys.ERROR,
                    "INTERLIS language server is not available in embedded GLSP runtime.");
            return;
        }

        CommandHandlers handlers = new CommandHandlers(languageServer);
        try {
            InterlisDiagramModel.DiagramModel model = handlers.exportDiagramModel(sourceUri).join();
            modelState.setProperty(InterlisGlspModelStateKeys.MODEL, model);
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            handleLoadFailure(sourceUri, cause);
        } catch (Exception ex) {
            handleLoadFailure(sourceUri, ex);
        }
    }

    @Override
    public void saveSourceModel(SaveModelAction saveModelAction) {
        // Read-only diagram for now.
    }

    private static String sourceUri(RequestModelAction requestModelAction) {
        if (requestModelAction == null || requestModelAction.getOptions() == null) {
            return null;
        }

        Map<String, String> options = requestModelAction.getOptions();
        return firstNonBlank(
                options.get("sourceUri"),
                options.get("uri"),
                options.get("path"));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String bestErrorMessage(Throwable error) {
        if (error == null) {
            return "Unknown diagram model error.";
        }

        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }

        String type = error.getClass().getSimpleName();
        if (type == null || type.isBlank()) {
            return "Unknown diagram model error.";
        }
        return type;
    }

    private void handleLoadFailure(String sourceUri, Throwable error) {
        if (CommandHandlers.isDiagramSourceMissingFailure(error)) {
            LOG.debug("INTERLIS source model missing for {}: {}", sourceUri, error != null ? error.getMessage() : null);
            modelState.setProperty(InterlisGlspModelStateKeys.ERROR, CommandHandlers.diagramSourceMissingMessage());
            return;
        }

        if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
            LOG.warn("Failed to load INTERLIS source model for {}: {}", sourceUri, error.getMessage());
        } else {
            LOG.warn("Failed to load INTERLIS source model for {}", sourceUri, error);
        }
        modelState.setProperty(InterlisGlspModelStateKeys.ERROR, bestErrorMessage(error));
    }
}
