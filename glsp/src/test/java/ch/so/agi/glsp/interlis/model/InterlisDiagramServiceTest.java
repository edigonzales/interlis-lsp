package ch.so.agi.glsp.interlis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.glsp.interlis.InterlisGlspTypes;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.glsp.graph.GCompartment;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GLabel;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GNode;
import org.junit.jupiter.api.Test;

class InterlisDiagramServiceTest {

    private final InterlisDiagramService service = new InterlisDiagramService();

    @Test
    void loadDiagram_createsStyledSemanticNodes() throws URISyntaxException {
        Path modelPath = Path.of(getClass().getResource("/models/simple.ili").toURI());

        Optional<GGraph> result = service.loadDiagram(modelPath);

        assertTrue(result.isPresent());
        GGraph graph = result.orElseThrow();
        assertEquals(2, graph.getChildren().size());

        GNode modelTopic = findTopicNode(graph, "SimpleModel");
        GNode topicATopic = findTopicNode(graph, "SimpleModel::TopicA");

        List<GNode> modelChildren = topicContentNodes(modelTopic);
        Set<String> modelNames = modelChildren.stream()
            .map(node -> findLabelText(node, InterlisGlspTypes.NAME_LABEL_TYPE).orElse(""))
            .collect(Collectors.toSet());
        assertEquals(Set.of("RootClass", "Status"), modelNames);

        GNode rootClassNode = findNodeByName(modelChildren, "RootClass");
        List<String> rootClassLabels = collectLabels(rootClassNode).stream()
            .map(GLabel::getText)
            .toList();
        assertTrue(rootClassLabels.contains("«Abstract»"));
        assertTrue(rootClassLabels.contains("Identifier[1] : String"));

        List<GNode> topicAChildren = topicContentNodes(topicATopic);
        Set<String> topicANames = topicAChildren.stream()
            .map(node -> findLabelText(node, InterlisGlspTypes.NAME_LABEL_TYPE).orElse(""))
            .collect(Collectors.toSet());
        assertTrue(topicANames.containsAll(Set.of("ClassA", "ClassB", "Address", "Color")));

        GNode classANode = findNodeByName(topicAChildren, "ClassA");
        List<String> classALabels = collectLabels(classANode).stream()
            .map(GLabel::getText)
            .toList();
        assertTrue(classALabels.contains("Name[1] : String"));
        assertTrue(classALabels.contains("FavoriteColor[0..1] : Color"));
        assertTrue(classALabels.contains("Constraint1()"));

        GNode colorEnumeration = findNodeByName(topicAChildren, "Color");
        List<String> colorLabels = collectLabels(colorEnumeration).stream()
            .map(GLabel::getText)
            .toList();
        assertTrue(colorLabels.contains("«Enumeration»"));
        assertTrue(colorLabels.contains("Red"));

        Set<String> positions = graph.getChildren().stream()
            .map(GNode.class::cast)
            .map(node -> node.getPosition().getX() + "," + node.getPosition().getY())
            .collect(Collectors.toSet());

        assertEquals(graph.getChildren().size(), positions.size());
    }

    @Test
    void loadDiagram_returnsEmptyWhenSourceMissing() {
        Optional<GGraph> result = service.loadDiagram(Path.of("does-not-exist.ili"));

        assertTrue(result.isEmpty());
    }

    private static GNode findTopicNode(final GGraph graph, final String labelText) {
        return graph.getChildren().stream()
            .filter(GNode.class::isInstance)
            .map(GNode.class::cast)
            .filter(node -> collectLabels(node).stream()
                .anyMatch(label -> InterlisGlspTypes.TOPIC_LABEL_TYPE.equals(label.getType())
                    && labelText.equals(label.getText())))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Topic '" + labelText + "' not found"));
    }

    private static List<GNode> topicContentNodes(final GNode topicNode) {
        return topicNode.getChildren().stream()
            .filter(GCompartment.class::isInstance)
            .map(GCompartment.class::cast)
            .filter(compartment -> InterlisGlspTypes.TOPIC_CONTENT_COMPARTMENT_TYPE.equals(compartment.getType()))
            .findFirst()
            .map(compartment -> compartment.getChildren().stream()
                .filter(GNode.class::isInstance)
                .map(GNode.class::cast)
                .toList())
            .orElseGet(List::of);
    }

    private static Optional<String> findLabelText(final GNode node, final String labelType) {
        return collectLabels(node).stream()
            .filter(label -> labelType.equals(label.getType()))
            .map(GLabel::getText)
            .findFirst();
    }

    private static GNode findNodeByName(final List<GNode> nodes, final String name) {
        return nodes.stream()
            .filter(node -> findLabelText(node, InterlisGlspTypes.NAME_LABEL_TYPE)
                .filter(name::equals)
                .isPresent())
            .findFirst()
            .orElseThrow(() -> new AssertionError("Node '" + name + "' not found"));
    }

    private static List<GLabel> collectLabels(final GModelElement element) {
        List<GLabel> labels = new java.util.ArrayList<>();
        if (element instanceof GLabel label) {
            labels.add(label);
        }
        element.getChildren().forEach(child -> labels.addAll(collectLabels(child)));
        return labels;
    }
}
