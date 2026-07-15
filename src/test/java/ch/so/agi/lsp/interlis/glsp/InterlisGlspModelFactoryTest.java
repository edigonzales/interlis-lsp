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
import org.eclipse.glsp.graph.GNode;
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
        settings.setUmlShowRoleCardinalities(false);

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
        settings.setUmlShowRoleCardinalities(true);

        GGraph graph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        });

        GEdge edge = findEdge(graph, EDGE_ID);
        List<String> labelIds = edgeLabelIds(edge);

        assertTrue(labelIds.contains(EDGE_SOURCE_CARD_ID));
        assertTrue(labelIds.contains(EDGE_TARGET_CARD_ID));
    }

    @Test
    void createGModelOmitsAssociationNamesWhenDisabled() {
        ClientSettings settings = new ClientSettings();
        settings.setUmlShowAssociationNames(false);
        settings.setUmlShowRoleCardinalities(true);

        GGraph graph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        });

        GEdge edge = findEdge(graph, EDGE_ID);
        List<String> labelIds = edgeLabelIds(edge);

        assertFalse(labelIds.contains(EDGE_LABEL_ID));
        assertTrue(labelIds.contains(EDGE_SOURCE_CARD_ID));
        assertTrue(labelIds.contains(EDGE_TARGET_CARD_ID));
    }

    @Test
    void createGModelAppliesAbstractTypeDeemphasisSetting() {
        ClientSettings enabled = new ClientSettings();
        GGraph mutedGraph = buildGraph(enabled, modelState -> {
            // no-op ELK simulation
        });
        GModelElement mutedNode = findModelElement(mutedGraph, "node:A");
        assertNotNull(mutedNode);
        assertTrue(mutedNode.getCssClasses().contains("interlis-class-muted-abstract"));

        ClientSettings disabled = new ClientSettings();
        disabled.setUmlDeemphasizeAbstractTypes(false);
        GGraph normalGraph = buildGraph(disabled, modelState -> {
            // no-op ELK simulation
        });
        GModelElement normalNode = findModelElement(normalGraph, "node:A");
        assertNotNull(normalNode);
        assertFalse(normalNode.getCssClasses().contains("interlis-class-muted-abstract"));
    }

    @Test
    void createGModelKeepsAssociationLabelElkManagedWhenPositionIsProvided() {
        ClientSettings settings = new ClientSettings();
        settings.setUmlShowRoleCardinalities(true);

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
        settings.setUmlShowRoleCardinalities(true);

        GGraph graph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        });

        GLabel associationLabel = findLabel(graph, EDGE_LABEL_ID);
        assertNotNull(associationLabel.getEdgePlacement());
        assertEquals(0.5d, associationLabel.getEdgePlacement().getPosition());
        assertEquals("top", associationLabel.getEdgePlacement().getSide());
        assertEquals(18d, associationLabel.getEdgePlacement().getOffset());
    }

    @Test
    void createGModelSplitsMultilineAttributesAndIncreasesNodeHeight() {
        ClientSettings settings = new ClientSettings();
        String singleLine = "Status[0..1] : (planned, active, completed)";
        String multiline = "Status[0..1] : (planned,\n  active,\n  completed)";

        GGraph singleLineGraph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        }, diagramWithAttribute(singleLine));
        GGraph multilineGraph = buildGraph(settings, modelState -> {
            // no-op ELK simulation
        }, diagramWithAttribute(multiline));

        GNode singleLineNode = (GNode) findModelElement(singleLineGraph, "node:enum");
        GNode multilineNode = (GNode) findModelElement(multilineGraph, "node:enum");
        assertNotNull(singleLineNode);
        assertNotNull(multilineNode);

        List<GLabel> labels = childLabels(multilineNode, "interlis-class-attribute");
        assertEquals(3, labels.size());
        assertEquals("Status[0..1] : (planned,", labels.get(0).getText());
        assertEquals("active,", labels.get(1).getText());
        assertEquals("completed)", labels.get(2).getText());
        assertEquals(10d, labels.get(0).getPosition().getX());
        assertEquals(18d, labels.get(1).getPosition().getX());
        assertEquals(18d, labels.get(2).getPosition().getX());
        assertTrue(multilineNode.getSize().getHeight() > singleLineNode.getSize().getHeight());
    }

    @Test
    void createGModelKeepsHiddenLocalEnumerationValuesOnOneAttributeLine() {
        GGraph graph = buildGraph(new ClientSettings(), modelState -> {
            // no-op ELK simulation
        }, diagramWithAttribute("Status[0..1] : Enumeration"));

        GNode node = (GNode) findModelElement(graph, "node:enum");
        List<GLabel> labels = childLabels(node, "interlis-class-attribute");

        assertEquals(1, labels.size());
        assertEquals("Status[0..1] : Enumeration", labels.get(0).getText());
    }

    private static GGraph buildGraph(ClientSettings settings, java.util.function.Consumer<DefaultGModelState> layoutHook) {
        return buildGraph(settings, layoutHook, sampleDiagram());
    }

    private static GGraph buildGraph(ClientSettings settings, java.util.function.Consumer<DefaultGModelState> layoutHook,
            InterlisDiagramModel.DiagramModel diagram) {
        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(settings);
        InterlisGlspBridge.bindLanguageServer(server);

        DefaultGModelState modelState = new DefaultGModelState();
        modelState.setProperty(InterlisGlspModelStateKeys.MODEL, diagram);
        modelState.setProperty(InterlisGlspModelStateKeys.SOURCE_URI, "file:///tmp/Test.ili");

        InterlisGlspModelFactory factory = new InterlisGlspModelFactory();
        factory.modelState = modelState;
        factory.layoutEngine = Optional.of((LayoutEngine) () -> layoutHook.accept(modelState));
        factory.createGModel();

        return (GGraph) modelState.getRoot();
    }

    private static InterlisDiagramModel.DiagramModel diagramWithAttribute(String attribute) {
        InterlisDiagramModel.ContainerModel container = new InterlisDiagramModel.ContainerModel(
                "container:enum",
                "Demo",
                "Demo",
                "namespace",
                new ArrayList<>(List.of("node:enum")));
        InterlisDiagramModel.NodeModel node = new InterlisDiagramModel.NodeModel(
                "node:enum",
                "EnumHolder",
                "container:enum",
                List.of(),
                List.of(attribute),
                List.of());
        return new InterlisDiagramModel.DiagramModel(
                "1",
                List.of(container),
                List.of(node),
                List.of());
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
                List.of("Abstract"),
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

    private static GModelElement findModelElement(GModelElement root, String id) {
        if (root == null) {
            return null;
        }
        if (id.equals(root.getId())) {
            return root;
        }
        if (root instanceof GGraph graph) {
            for (GModelElement child : graph.getChildren()) {
                GModelElement result = findModelElement(child, id);
                if (result != null) {
                    return result;
                }
            }
        } else if (root instanceof GNode node) {
            for (GModelElement child : node.getChildren()) {
                GModelElement result = findModelElement(child, id);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
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

    private static List<GLabel> childLabels(GNode node, String cssClass) {
        List<GLabel> labels = new ArrayList<>();
        for (GModelElement child : node.getChildren()) {
            if (child instanceof GLabel label && label.getCssClasses().contains(cssClass)) {
                labels.add(label);
            }
        }
        return labels;
    }
}
