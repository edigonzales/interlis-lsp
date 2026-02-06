package ch.so.agi.lsp.interlis.glsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.glsp.graph.GEdge;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GLabel;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GraphFactory;
import org.eclipse.glsp.server.layout.LayoutEngine;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;

class InterlisGlspModelFactoryTest {
    private static final String EDGE_ID = "edge-association";
    private static final String EDGE_LABEL_ID = EDGE_ID + ":label";
    private static final String EDGE_SOURCE_CARD_ID = EDGE_ID + ":source-card";
    private static final String EDGE_TARGET_CARD_ID = EDGE_ID + ":target-card";

    @AfterEach
    void cleanup() {
        InterlisGlspBridge.clear();
    }

    @Test
    void createGModelOmitsCardinalitiesWhenDisabled() {
        ClientSettings settings = new ClientSettings();
        settings.setShowCardinalities(false);

        GGraph graph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        });

        GEdge edge = findEdge(graph, EDGE_ID);
        List<String> labelIds = edgeLabelIds(edge);

        assertTrue(labelIds.contains(EDGE_LABEL_ID));
        assertFalse(labelIds.contains(EDGE_SOURCE_CARD_ID));
        assertFalse(labelIds.contains(EDGE_TARGET_CARD_ID));
    }

    @Test
    void createGModelShowsCardinalitiesWhenEnabled() {
        ClientSettings settings = new ClientSettings();
        settings.setShowCardinalities(true);

        GGraph graph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        });

        GEdge edge = findEdge(graph, EDGE_ID);
        List<String> labelIds = edgeLabelIds(edge);

        assertTrue(labelIds.contains(EDGE_SOURCE_CARD_ID));
        assertTrue(labelIds.contains(EDGE_TARGET_CARD_ID));
    }

    @Test
    void createGModelKeepsAssociationLabelElkManagedWhenPositionIsProvided() {
        ClientSettings settings = new ClientSettings();
        settings.setShowCardinalities(true);

        GGraph graph = buildGraph(settings, modelState -> {
            GGraph root = (GGraph) modelState.getRoot();
            GLabel label = findLabel(root, EDGE_LABEL_ID);
            var point = GraphFactory.eINSTANCE.createGPoint();
            point.setX(160);
            point.setY(52);
            label.setPosition(point);
        });

        GLabel associationLabel = findLabel(graph, EDGE_LABEL_ID);
        assertNotNull(associationLabel.getPosition());
        assertNull(associationLabel.getEdgePlacement());
    }

    @Test
    void createGModelFallsBackToManualAssociationLabelPlacementWhenNoPositionExists() {
        ClientSettings settings = new ClientSettings();
        settings.setShowCardinalities(true);

        GGraph graph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        });

        GLabel associationLabel = findLabel(graph, EDGE_LABEL_ID);
        assertNotNull(associationLabel.getEdgePlacement());
        assertEquals(0.5d, associationLabel.getEdgePlacement().getPosition());
        assertEquals("top", associationLabel.getEdgePlacement().getSide());
        assertEquals(18d, associationLabel.getEdgePlacement().getOffset());
    }

    private static GGraph buildGraph(ClientSettings settings, java.util.function.Consumer<DefaultGModelState> layoutHook) {
        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(settings);
        InterlisGlspBridge.bindLanguageServer(server);

        DefaultGModelState modelState = new DefaultGModelState();
        modelState.setProperty(InterlisGlspModelStateKeys.MODEL, sampleDiagram());
        modelState.setProperty(InterlisGlspModelStateKeys.SOURCE_URI, "file:///tmp/Test.ili");

        InterlisGlspModelFactory factory = new InterlisGlspModelFactory();
        factory.modelState = modelState;
        factory.layoutEngine = Optional.of((LayoutEngine) () -> layoutHook.accept(modelState));
        factory.createGModel();

        return (GGraph) modelState.getRoot();
    }

    private static InterlisDiagramModel.DiagramModel sampleDiagram() {
        InterlisDiagramModel.ContainerModel container = new InterlisDiagramModel.ContainerModel(
                "container:1",
                "Demo",
                "Demo",
                "namespace",
                new ArrayList<>(List.of("node:A", "node:B")));
        InterlisDiagramModel.NodeModel nodeA = new InterlisDiagramModel.NodeModel(
                "node:A",
                "A",
                "container:1",
                List.of(),
                List.of(),
                List.of());
        InterlisDiagramModel.NodeModel nodeB = new InterlisDiagramModel.NodeModel(
                "node:B",
                "B",
                "container:1",
                List.of(),
                List.of(),
                List.of());
        InterlisDiagramModel.EdgeModel association = new InterlisDiagramModel.EdgeModel(
                EDGE_ID,
                "association",
                "node:A",
                "node:B",
                "0..*",
                "1",
                "owns");
        return new InterlisDiagramModel.DiagramModel(
                "1",
                List.of(container),
                List.of(nodeA, nodeB),
                List.of(association));
    }

    private static GEdge findEdge(GGraph graph, String edgeId) {
        for (GModelElement element : graph.getChildren()) {
            if (element instanceof GEdge edge && edgeId.equals(edge.getId())) {
                return edge;
            }
        }
        throw new AssertionError("Edge not found: " + edgeId);
    }

    private static GLabel findLabel(GGraph graph, String labelId) {
        for (GModelElement element : graph.getChildren()) {
            if (!(element instanceof GEdge edge)) {
                continue;
            }
            for (GModelElement child : edge.getChildren()) {
                if (child instanceof GLabel label && labelId.equals(label.getId())) {
                    return label;
                }
            }
        }
        throw new AssertionError("Label not found: " + labelId);
    }

    private static List<String> edgeLabelIds(GEdge edge) {
        List<String> ids = new ArrayList<>();
        for (GModelElement child : edge.getChildren()) {
            if (child instanceof GLabel label) {
                ids.add(label.getId());
            }
        }
        return ids;
    }
}
