package ch.so.agi.glsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GLabel;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.eclipse.glsp.server.model.GModelState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterlisGModelFactoryTest {

    private GModelState modelState;

    @BeforeEach
    void setUp() {
        modelState = new DefaultGModelState();
        modelState.setProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, "file:///models/placeholder.ili");
    }

    @Test
    void createGModel_populatesGraphWithPlaceholderClass() {
        InterlisGModelFactory factory = new InterlisGModelFactory(modelState);

        factory.createGModel();

        GGraph graph = assertInstanceOf(GGraph.class, modelState.getRoot());
        assertEquals(InterlisGlspTypes.GRAPH_ID, graph.getId());
        assertEquals(InterlisGlspTypes.DIAGRAM_TYPE, graph.getType());
        assertEquals(1, graph.getChildren().size());

        GModelElement element = graph.getChildren().get(0);
        GNode classNode = assertInstanceOf(GNode.class, element);
        assertEquals(InterlisGlspTypes.CLASS_NODE_ID, classNode.getId());
        assertEquals(InterlisGlspTypes.CLASS_NODE_TYPE, classNode.getType());
        assertTrue(classNode.getChildren().size() >= 1);

        GLabel classLabel = assertInstanceOf(GLabel.class, classNode.getChildren().get(0));
        assertEquals(InterlisGlspTypes.CLASS_LABEL_ID, classLabel.getId());
        assertEquals("INTERLIS Class", classLabel.getText());
    }

    @Test
    void createGModel_preservesSourceUriProperty() {
        InterlisGModelFactory factory = new InterlisGModelFactory(modelState);

        factory.createGModel();

        String sourceUri = modelState
            .getProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, String.class)
            .orElse(null);

        assertNotNull(sourceUri);
        assertEquals("file:///models/placeholder.ili", sourceUri);
    }
}
