package ch.so.agi.lsp.interlis.glsp;

import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.server.features.navigation.NavigationTarget;
import org.eclipse.glsp.server.features.navigation.NavigationTargetProvider;
import org.eclipse.glsp.server.model.GModelState;
import org.eclipse.glsp.server.types.EditorContext;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

import com.google.gson.Gson;
import com.google.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves semantic diagram node IDs to local INTERLIS declarations. */
public class InterlisGlspNavigationTargetProvider implements NavigationTargetProvider {
    public static final String TARGET_TYPE = "interlis.sourceDeclaration";
    private static final String INTERLIS_NODE_CLASS = "interlis-class";
    private static final int VIEW_COLUMN_BESIDE = -2;
    private static final Gson GSON = new Gson();

    @Inject
    protected GModelState modelState;

    @Override
    public List<? extends NavigationTarget> getTargets(EditorContext context) {
        String elementId = elementId(context);
        if (elementId == null || modelState == null || modelState.getRoot() == null) {
            return List.of();
        }

        GModelElement element = modelState.getIndex().get(elementId).orElse(null);
        if (element == null || !element.getCssClasses().contains(INTERLIS_NODE_CLASS)) {
            return List.of();
        }

        String sourceUri = modelState.getProperty(InterlisGlspModelStateKeys.SOURCE_URI, String.class).orElse(null);
        InterlisLanguageServer languageServer = InterlisGlspBridge.getLanguageServer();
        if (sourceUri == null || sourceUri.isBlank() || languageServer == null) {
            return List.of();
        }

        if (languageServer.getInterlisTextDocumentService().isDocumentDirty(sourceUri)) {
            return List.of();
        }

        Location location = languageServer.getInterlisTextDocumentService()
                .findDeclaration(sourceUri, elementId)
                .orElse(null);
        if (location == null || location.getUri() == null || location.getRange() == null) {
            return List.of();
        }

        Map<String, String> args = Map.of(
                NavigationTargetProvider.JSON_OPENER_OPTIONS,
                GSON.toJson(jsonOpenerOptions(location)));
        return List.of(new NavigationTarget(location.getUri(), elementId, args));
    }

    @Override
    public String getTargetTypeId() {
        return TARGET_TYPE;
    }

    private static String elementId(EditorContext context) {
        if (context == null || context.getArgs() == null || context.getArgs().size() != 1) {
            return null;
        }
        String elementId = context.getArgs().get("elementId");
        return elementId == null || elementId.isBlank() ? null : elementId;
    }

    private static Map<String, Object> jsonOpenerOptions(Location location) {
        Position start = location.getRange().getStart();
        Position end = location.getRange().getEnd();

        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("start", position(start));
        selection.put("end", position(end));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("selection", selection);
        options.put("preserveFocus", false);
        options.put("viewColumn", VIEW_COLUMN_BESIDE);
        return options;
    }

    private static Map<String, Integer> position(Position position) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("line", position != null ? position.getLine() : 0);
        result.put("character", position != null ? position.getCharacter() : 0);
        return result;
    }
}
