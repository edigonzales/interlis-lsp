package ch.so.agi.glsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.eclipse.glsp.server.model.GModelState;
import org.eclipse.glsp.server.types.ShapeTypeHint;
import org.junit.jupiter.api.Test;

class InterlisDiagramModuleTest {

    @Test
    void configurationProvidesClassShapeHint() {
        DiagramConfiguration configuration = new InterlisDiagramConfiguration();

        List<ShapeTypeHint> shapeHints = configuration.getShapeTypeHints();

        assertEquals(4, shapeHints.size());
        assertTrue(shapeHints.stream()
            .map(ShapeTypeHint::getElementTypeId)
            .toList()
            .containsAll(List.of(
                InterlisGlspTypes.CLASS_NODE_TYPE,
                InterlisGlspTypes.STRUCTURE_NODE_TYPE,
                InterlisGlspTypes.VIEW_NODE_TYPE,
                InterlisGlspTypes.ENUMERATION_NODE_TYPE)));

        assertEquals(2, configuration.getEdgeTypeHints().size());
        assertTrue(configuration.getEdgeTypeHints().stream()
            .map(edgeHint -> edgeHint.getElementTypeId())
            .toList()
            .containsAll(List.of(
                InterlisGlspTypes.INHERITANCE_EDGE_TYPE,
                InterlisGlspTypes.ASSOCIATION_EDGE_TYPE)));
    }

    @Test
    void diagramModuleBindsInterlisServices() {
        TestableInterlisDiagramModule module = new TestableInterlisDiagramModule();

        assertEquals(InterlisDiagramConfiguration.class, module.exposeDiagramConfiguration());
        assertEquals(InterlisSourceModelStorage.class, module.exposeSourceModelStorage());
        assertEquals(InterlisGModelFactory.class, module.exposeGModelFactory());
        assertEquals(InterlisGlspTypes.DIAGRAM_TYPE, module.getDiagramType());
        assertEquals(DefaultGModelState.class, module.exposeGModelState());
    }

    private static final class TestableInterlisDiagramModule extends InterlisDiagramModule {

        Class<? extends DiagramConfiguration> exposeDiagramConfiguration() {
            return super.bindDiagramConfiguration();
        }

        Class<? extends SourceModelStorage> exposeSourceModelStorage() {
            return super.bindSourceModelStorage();
        }

        Class<? extends GModelFactory> exposeGModelFactory() {
            return super.bindGModelFactory();
        }

        Class<? extends GModelState> exposeGModelState() {
            return super.bindGModelState();
        }
    }
}
