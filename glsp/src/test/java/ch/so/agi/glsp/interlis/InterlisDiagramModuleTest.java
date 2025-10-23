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

        assertEquals(1, shapeHints.size());
        assertEquals(InterlisGlspTypes.CLASS_NODE_TYPE, shapeHints.get(0).getElementTypeId());
        assertTrue(configuration.getEdgeTypeHints().isEmpty());
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
