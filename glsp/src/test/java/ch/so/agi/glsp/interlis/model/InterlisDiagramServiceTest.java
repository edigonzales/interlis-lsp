package ch.so.agi.glsp.interlis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.glsp.interlis.InterlisGlspTypes;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.glsp.graph.GEdge;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GLabel;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GNode;
import org.junit.jupiter.api.Test;

class InterlisDiagramServiceTest {

    private final InterlisDiagramService service = new InterlisDiagramService();

    @Test
    void loadDiagram_createsStyledNodesAndEdges() throws URISyntaxException {
        Path modelPath = Path.of(getClass().getResource("/models/simple.ili").toURI());

        Optional<GGraph> result = service.loadDiagram(modelPath);

        assertTrue(result.isPresent());
        GGraph graph = result.orElseThrow();

        GNode classANode = findNodeByName(graph, "ClassA");
        GNode classBNode = findNodeByName(graph, "ClassB");
        GNode rootNode = findNodeByName(graph, "RootClass");
        GNode structureNode = findNodeByName(graph, "Address");
        GNode enumerationNode = findNodeByName(graph, "Color");
        assertTrue(classANode.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_CLASS));
        assertTrue(rootNode.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_CLASS));
        assertTrue(structureNode.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_STRUCTURE));
        assertTrue(enumerationNode.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_ENUMERATION));

        assertLabelWithText(rootNode, InterlisGlspTypes.STEREOTYPE_LABEL_TYPE, "<<Abstract>>");
        assertLabelWithText(structureNode, InterlisGlspTypes.STEREOTYPE_LABEL_TYPE, "<<Structure>>");
        assertLabelWithText(classANode, InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE, "Name [1] : String");
        assertLabelWithText(classANode, InterlisGlspTypes.CONSTRAINT_LABEL_TYPE, "Constraint1()");
        assertLabelWithText(classBNode, InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE, "Shade [0..1] : Color");
        assertLabelWithText(enumerationNode, InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE, "red");
        assertLabelWithText(classANode, InterlisGlspTypes.NAMESPACE_LABEL_TYPE, "SimpleModel::TopicA");
        assertLabelWithText(rootNode, InterlisGlspTypes.NAMESPACE_LABEL_TYPE, "SimpleModel");

        List<GEdge> edges = graph.getChildren().stream()
            .filter(GEdge.class::isInstance)
            .map(GEdge.class::cast)
            .toList();

        assertTrue(edges.stream()
            .filter(edge -> InterlisGlspTypes.INHERITANCE_EDGE_TYPE.equals(edge.getType()))
            .anyMatch(edge -> edge.getSourceId().equals(classANode.getId())
                && edge.getTargetId().equals(rootNode.getId())));

        GEdge association = edges.stream()
            .filter(edge -> InterlisGlspTypes.ASSOCIATION_EDGE_TYPE.equals(edge.getType()))
            .findFirst()
            .orElseThrow();
        assertEquals(classANode.getId(), association.getSourceId());
        assertEquals(classBNode.getId(), association.getTargetId());
        assertTrue(streamLabels(association)
            .map(GLabel::getText)
            .anyMatch(text -> text.contains("a [0..*] ↔ b [1]")));
    }

    @Test
    void loadDiagram_returnsEmptyWhenSourceMissing() {
        Optional<GGraph> result = service.loadDiagram(Path.of("does-not-exist.ili"));

        assertTrue(result.isEmpty());
    }

    private static GNode findNodeByName(final GGraph graph, final String name) {
        return graph.getChildren().stream()
            .filter(GNode.class::isInstance)
            .map(GNode.class::cast)
            .filter(node -> streamLabels(node)
                .anyMatch(label -> InterlisGlspTypes.NAME_LABEL_TYPE.equals(label.getType())
                    && name.equals(label.getText())))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Node with name '" + name + "' not found"));
    }

    private static void assertLabelWithText(final GModelElement element, final String type, final String text) {
        assertTrue(streamLabels(element)
            .filter(label -> type.equals(label.getType()))
            .anyMatch(label -> text.equals(label.getText())),
            () -> "Expected label '" + text + "' of type '" + type + "'");
    }

    private static Stream<GLabel> streamLabels(final GModelElement element) {
        Stream<GLabel> current = element instanceof GLabel label ? Stream.of(label) : Stream.empty();
        Stream<GLabel> children = element.getChildren().stream()
            .flatMap(child -> streamLabels(child));
        return Stream.concat(current, children);
    }
}
