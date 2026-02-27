package ch.so.agi.lsp.interlis.glsp;

import java.util.List;
import java.util.Optional;

import org.eclipse.glsp.graph.DefaultTypes;
import org.eclipse.glsp.graph.GraphExtension;
import org.eclipse.glsp.server.diagram.BaseDiagramConfiguration;
import org.eclipse.glsp.server.layout.ServerLayoutKind;
import org.eclipse.glsp.server.types.EdgeTypeHint;
import org.eclipse.glsp.server.types.ShapeTypeHint;

public class InterlisGlspDiagramConfiguration extends BaseDiagramConfiguration {
    public InterlisGlspDiagramConfiguration() {
        this.diagramType = InterlisGlspConstants.DIAGRAM_TYPE;
        this.graphExtension = Optional.empty();
    }

    @Override
    public List<ShapeTypeHint> getShapeTypeHints() {
        return List.of(
                readOnlyShapeHint(DefaultTypes.NODE),
                readOnlyShapeHint(DefaultTypes.COMPARTMENT),
                readOnlyShapeHint(DefaultTypes.COMPARTMENT_HEADER),
                readOnlyShapeHint(DefaultTypes.LABEL));
    }

    @Override
    public List<EdgeTypeHint> getEdgeTypeHints() {
        EdgeTypeHint hint = createDefaultEdgeTypeHint(DefaultTypes.EDGE);
        hint.setDeletable(false);
        hint.setRepositionable(false);
        hint.setRoutable(false);
        hint.setDynamic(false);
        return List.of(hint);
    }

    @Override
    public ServerLayoutKind getLayoutKind() {
        return ServerLayoutKind.MANUAL;
    }

    @Override
    public boolean needsClientLayout() {
        // All coordinates/sizes are computed server-side for deterministic read-only rendering.
        return false;
    }

    @Override
    public boolean animatedUpdate() {
        return false;
    }

    private ShapeTypeHint readOnlyShapeHint(String type) {
        ShapeTypeHint hint = createDefaultShapeTypeHint(type);
        hint.setDeletable(false);
        hint.setRepositionable(false);
        hint.setResizable(false);
        hint.setReparentable(false);
        return hint;
    }
}
