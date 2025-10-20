package ch.so.agi.glsp.interlis;

import java.util.Map;
import java.util.Objects;

import org.eclipse.glsp.server.actions.SaveModelAction;
import org.eclipse.glsp.server.features.core.model.RequestModelAction;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.model.GModelState;

import com.google.inject.Inject;

final class InterlisSourceModelStorage implements SourceModelStorage {
    private final GModelState modelState;

    @Inject
    InterlisSourceModelStorage(GModelState modelState) {
        this.modelState = Objects.requireNonNull(modelState, "modelState");
    }

    @Override
    public void loadSourceModel(RequestModelAction action) {
        Map<String, String> options = action != null ? action.getOptions() : null;
        String uri = options != null ? options.get(InterlisGlspConstants.OPTION_SOURCE_URI) : null;
        if (uri != null && !uri.isBlank()) {
            modelState.setProperty(InterlisGlspConstants.PROP_SOURCE_URI, uri);
        }
    }

    @Override
    public void saveSourceModel(SaveModelAction action) {
        // read-only view for now
    }
}
