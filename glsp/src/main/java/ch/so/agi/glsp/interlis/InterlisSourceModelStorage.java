package ch.so.agi.glsp.interlis;

import java.util.Map;
import java.util.Objects;

import org.eclipse.glsp.server.actions.SaveModelAction;
import org.eclipse.glsp.server.features.core.model.RequestModelAction;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.model.GModelState;

import com.google.inject.Inject;

/**
 * Stores metadata about the INTERLIS source model so that the {@link InterlisGModelFactory}
 * can later build the diagram from the corresponding *.ili file.
 */
public class InterlisSourceModelStorage implements SourceModelStorage {

    /** Key used in the model state properties to keep the currently rendered INTERLIS file path. */
    public static final String SOURCE_URI_PROPERTY = "interlis.sourceUri";

    private static final String SOURCE_URI_OPTION = "sourceUri";

    private final GModelState modelState;

    /**
     * Creates a storage instance that can write diagram related options to the shared model state.
     *
     * @param modelState the shared GLSP model state
     */
    @Inject
    public InterlisSourceModelStorage(final GModelState modelState) {
        this.modelState = modelState;
    }

    @Override
    public void loadSourceModel(final RequestModelAction action) {
        final Map<String, String> options = action.getOptions();
        final String sourceUri = options != null ? options.get(SOURCE_URI_OPTION) : null;
        modelState.setProperty(SOURCE_URI_PROPERTY, Objects.requireNonNull(sourceUri, "sourceUri option missing"));
    }

    @Override
    public void saveSourceModel(final SaveModelAction action) {
        // The diagram is currently read-only, therefore there is nothing to persist yet.
    }
}
