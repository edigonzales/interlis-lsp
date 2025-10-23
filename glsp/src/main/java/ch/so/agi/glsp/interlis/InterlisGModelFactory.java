package ch.so.agi.glsp.interlis;

import java.util.Map;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.model.GModelState;

import com.google.inject.Inject;

/**
 * Builds the graphical model that is sent to GLSP clients. The current implementation
 * produces a single class node placeholder but keeps access to the source *.ili file so
 * that full UML projections can be added in later iterations.
 */
public class InterlisGModelFactory implements GModelFactory {

    private final GModelState modelState;

    /**
     * @param modelState the shared model state containing the source model metadata
     */
    @Inject
    public InterlisGModelFactory(final GModelState modelState) {
        this.modelState = modelState;
    }

    @Override
    public void createGModel() {
        modelState.getProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, String.class)
            .ifPresent(source -> modelState.setProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, source));

        final GGraph root = new GGraphBuilder()
            .id(InterlisGlspTypes.GRAPH_ID)
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .layoutOptions(Map.of("padding", "24"))
            .add(new GNodeBuilder()
                .id(InterlisGlspTypes.CLASS_NODE_ID)
                .type(InterlisGlspTypes.CLASS_NODE_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_NODE)
                .position(80, 80)
                .size(240, 140)
                .add(new GLabelBuilder()
                    .id(InterlisGlspTypes.CLASS_LABEL_ID)
                    .type(InterlisGlspTypes.CLASS_LABEL_TYPE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_LABEL)
                    .text("INTERLIS Class")
                    .build())
                .build())
            .build();

        modelState.updateRoot(root);
    }
}
