package ch.so.agi.glsp.interlis;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.glsp.graph.GraphExtension;
import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.types.EdgeTypeHint;
import org.eclipse.glsp.server.types.ShapeTypeHint;

/**
 * Declares metadata about the INTERLIS class diagram such as supported node types.
 */
public class InterlisDiagramConfiguration implements DiagramConfiguration {

    @Override
    public String getDiagramType() {
        return InterlisGlspTypes.DIAGRAM_TYPE;
    }

    @Override
    public Map<String, EClass> getTypeMappings() {
        return Collections.emptyMap();
    }

    @Override
    public List<ShapeTypeHint> getShapeTypeHints() {
        return List.of(
            createDefaultShapeTypeHint(InterlisGlspTypes.CLASS_NODE_TYPE),
            createDefaultShapeTypeHint(InterlisGlspTypes.STRUCTURE_NODE_TYPE),
            createDefaultShapeTypeHint(InterlisGlspTypes.VIEW_NODE_TYPE),
            createDefaultShapeTypeHint(InterlisGlspTypes.ENUMERATION_NODE_TYPE));
    }

    @Override
    public List<EdgeTypeHint> getEdgeTypeHints() {
        return List.of(
            new EdgeTypeHint(InterlisGlspTypes.INHERITANCE_EDGE_TYPE, false, false, false, List.of(), List.of()),
            new EdgeTypeHint(InterlisGlspTypes.ASSOCIATION_EDGE_TYPE, false, false, false, List.of(), List.of()));
    }

    @Override
    public Optional<GraphExtension> getGraphExtension() {
        return Optional.empty();
    }
}
