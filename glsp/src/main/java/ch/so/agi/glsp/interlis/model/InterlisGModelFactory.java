package ch.so.agi.glsp.interlis.model;

import com.google.inject.Inject;
import org.eclipse.glsp.graph.GModelRoot;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.model.GModelState;

/**
 * GLSP factory responsible for asking the {@link InterlisDiagramService} to
 * build a fresh graph model and publishing it to the shared {@link GModelState}
 * whenever the client requests a diagram update.
 */
public final class InterlisGModelFactory implements GModelFactory {
    private final InterlisDiagramService diagrams;
    private final GModelState modelState;

    @Inject
    public InterlisGModelFactory(InterlisDiagramService diagrams, GModelState modelState) {
        this.diagrams = diagrams;
        this.modelState = modelState;
    }

    @Override
    public void createGModel() {
        GModelRoot root = diagrams.createModel(modelState.getClientOptions());
        modelState.updateRoot(root);
    }
}
