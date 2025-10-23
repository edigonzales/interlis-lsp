package ch.so.agi.glsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.eclipse.glsp.server.model.GModelState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.so.agi.glsp.interlis.model.InterlisDiagramService;

class InterlisGModelFactoryTest {

    private GModelState modelState;

    @BeforeEach
    void setUp() {
        modelState = new DefaultGModelState();
        modelState.setProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, "file:///models/placeholder.ili");
    }

    @Test
    void createGModel_usesDiagramServiceGraphWhenAvailable() {
        GGraph expected = new GGraphBuilder()
            .id("test-graph")
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .build();
        StubDiagramService service = new StubDiagramService(Optional.of(expected));
        InterlisGModelFactory factory = new InterlisGModelFactory(modelState, service);

        factory.createGModel();

        GGraph graph = assertInstanceOf(GGraph.class, modelState.getRoot());
        assertSame(expected, graph);
    }

    @Test
    void createGModel_preservesSourceUriProperty() {
        InterlisGModelFactory factory = new InterlisGModelFactory(modelState, new StubDiagramService(Optional.empty()));

        factory.createGModel();

        String sourceUri = modelState
            .getProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, String.class)
            .orElse(null);

        assertNotNull(sourceUri);
        assertEquals("file:///models/placeholder.ili", sourceUri);
    }

    @Test
    void createGModel_fallsBackToEmptyGraphWhenServiceReturnsNothing() {
        InterlisGModelFactory factory = new InterlisGModelFactory(modelState, new StubDiagramService(Optional.empty()));

        factory.createGModel();

        GGraph graph = assertInstanceOf(GGraph.class, modelState.getRoot());
        assertEquals(InterlisGlspTypes.GRAPH_ID, graph.getId());
        assertEquals(InterlisGlspTypes.DIAGRAM_TYPE, graph.getType());
        assertTrue(graph.getChildren().isEmpty());
    }

    private static final class StubDiagramService extends InterlisDiagramService {

        private final Optional<GGraph> graph;

        StubDiagramService(final Optional<GGraph> graph) {
            this.graph = graph;
        }

        @Override
        public Optional<GGraph> loadDiagram(final java.nio.file.Path sourceFile) {
            return graph;
        }
    }
}
