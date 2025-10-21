package ch.so.agi.glsp.interlis;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.glsp.graph.GraphExtension;
import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.layout.ServerLayoutKind;
import org.eclipse.glsp.server.types.EdgeTypeHint;
import org.eclipse.glsp.server.types.ShapeTypeHint;

/**
 * Minimal diagram configuration used by the placeholder UML diagram.
 */
public final class InterlisDiagramConfiguration implements DiagramConfiguration {
    @Override
    public String getDiagramType() {
        return InterlisDiagramModule.DIAGRAM_TYPE;
    }

    @Override
    public Map<String, EClass> getTypeMappings() {
        return Collections.emptyMap();
    }

    @Override
    public List<ShapeTypeHint> getShapeTypeHints() {
        return Collections.emptyList();
    }

    @Override
    public List<EdgeTypeHint> getEdgeTypeHints() {
        return Collections.emptyList();
    }

    @Override
    public Optional<GraphExtension> getGraphExtension() {
        return Optional.empty();
    }

    @Override
    public ServerLayoutKind getLayoutKind() {
        return ServerLayoutKind.NONE;
    }

    @Override
    public boolean needsClientLayout() {
        return true;
    }

    @Override
    public boolean animatedUpdate() {
        return false;
    }
}
