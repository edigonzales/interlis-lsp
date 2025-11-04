package ch.so.agi.glsp.interlis;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.glsp.server.diagram.BaseDiagramConfiguration;
import org.eclipse.glsp.server.types.EdgeTypeHint;
import org.eclipse.glsp.server.types.ShapeTypeHint;

final class InterlisGlspDiagramConfiguration extends BaseDiagramConfiguration {
    InterlisGlspDiagramConfiguration() {
        this.diagramType = InterlisGlspConstants.DIAGRAM_TYPE;
    }

    @Override
    public Map<String, EClass> getTypeMappings() {
        return Collections.emptyMap();
    }

    @Override
    public List<ShapeTypeHint> getShapeTypeHints() {
        return List.of(createDefaultShapeTypeHint(InterlisGlspConstants.CLASS_NODE_TYPE));
    }

    @Override
    public List<EdgeTypeHint> getEdgeTypeHints() {
        return List.of();
    }
}
