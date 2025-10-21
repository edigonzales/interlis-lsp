package ch.so.agi.glsp.interlis.model;

import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.glsp.server.actions.SaveModelAction;
import org.eclipse.glsp.server.features.core.model.RequestModelAction;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;

/**
 * Stores the last source URI that should be rendered by the GLSP server.
 */
public final class InterlisSourceModelStorage implements SourceModelStorage {
    private final InterlisDiagramService diagrams;

    @Inject
    public InterlisSourceModelStorage(InterlisDiagramService diagrams) {
        this.diagrams = diagrams;
    }

    @Override
    public void loadSourceModel(RequestModelAction action) {
        Map<String, String> options = action != null ? action.getOptions() : Map.of();
        diagrams.updateFromClientOptions(options);
    }

    @Override
    public void saveSourceModel(SaveModelAction action) {
        // No-op for the read-only prototype.
    }
}
