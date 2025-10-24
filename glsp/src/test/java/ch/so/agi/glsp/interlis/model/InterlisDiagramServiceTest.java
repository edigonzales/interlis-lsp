package ch.so.agi.glsp.interlis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GCompartment;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.graph.GLabel;
import org.junit.jupiter.api.Test;

import ch.so.agi.glsp.interlis.InterlisGlspTypes;

class InterlisDiagramServiceTest {

    private final InterlisDiagramService service = new InterlisDiagramService();

    @Test
    void loadDiagram_createsSemanticNodes() throws URISyntaxException {
        Path modelPath = Path.of(getClass().getResource("/models/simple.ili").toURI());

        Optional<GGraph> result = service.loadDiagram(modelPath);

        assertTrue(result.isPresent());
        GGraph graph = result.orElseThrow();

        List<GNode> nodes = graph.getChildren().stream()
            .map(GNode.class::cast)
            .collect(Collectors.toList());

        List<GNode> namespaces = nodes.stream()
            .filter(node -> InterlisGlspTypes.TOPIC_NODE_TYPE.equals(node.getType()))
            .collect(Collectors.toList());
        assertEquals(2, namespaces.size(), "Expected namespace nodes for model and topic");

        Set<String> namespaceLabels = namespaces.stream()
            .flatMap(node -> node.getChildren().stream())
            .filter(GLabel.class::isInstance)
            .map(GLabel.class::cast)
            .map(GLabel::getText)
            .collect(Collectors.toSet());
        assertTrue(namespaceLabels.contains("SimpleModel"));
        assertTrue(namespaceLabels.contains("SimpleModel::TopicA"));

        List<GNode> classifiers = nodes.stream()
            .filter(node -> !InterlisGlspTypes.TOPIC_NODE_TYPE.equals(node.getType()))
            .collect(Collectors.toList());
        assertEquals(5, classifiers.size(), "Expected classifiers for classes, structure and enumerations");

        GNode rootClass = findClassifier(classifiers, InterlisGlspTypes.CLASS_NODE_TYPE, "RootClass");
        List<String> rootStereotypes = collectDirectLabelTexts(rootClass, InterlisGlspTypes.STEREOTYPE_LABEL_TYPE);
        assertTrue(rootStereotypes.contains("«abstract»"));

        List<String> rootAttributes = collectCompartmentTexts(rootClass, InterlisGlspTypes.ATTRIBUTE_COMPARTMENT_TYPE,
            InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE);
        assertTrue(rootAttributes.stream().anyMatch(text -> text.startsWith("Identifier")));
        assertTrue(rootAttributes.stream().anyMatch(text -> text.startsWith("status")));

        List<String> rootConstraints = collectCompartmentTexts(rootClass,
            InterlisGlspTypes.CONSTRAINT_COMPARTMENT_TYPE, InterlisGlspTypes.CONSTRAINT_LABEL_TYPE);
        assertTrue(rootConstraints.contains("Constraint1()"));

        GNode structure = findClassifier(classifiers, InterlisGlspTypes.STRUCTURE_NODE_TYPE, "HelperStruct");
        List<String> structureStereotypes = collectDirectLabelTexts(structure, InterlisGlspTypes.STEREOTYPE_LABEL_TYPE);
        assertTrue(structureStereotypes.contains("«structure»"));

        GNode statusEnum = findClassifier(classifiers, InterlisGlspTypes.ENUMERATION_NODE_TYPE, "Status");
        List<String> literals = collectCompartmentTexts(statusEnum, InterlisGlspTypes.ATTRIBUTE_COMPARTMENT_TYPE,
            InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE);
        assertTrue(literals.contains("Pending"));
        assertTrue(literals.contains("Active"));
        assertTrue(literals.contains("Closed"));

        GNode topicClass = findClassifier(classifiers, InterlisGlspTypes.CLASS_NODE_TYPE, "ClassA");
        assertFalse(topicClass.getChildren().isEmpty(), "ClassA should have child labels");
    }

    @Test
    void loadDiagram_returnsEmptyWhenSourceMissing() {
        Optional<GGraph> result = service.loadDiagram(Path.of("does-not-exist.ili"));

        assertTrue(result.isEmpty());
    }

    private static GNode findClassifier(final List<GNode> nodes, final String type, final String name) {
        return nodes.stream()
            .filter(node -> type.equals(node.getType()))
            .filter(node -> collectDirectLabelTexts(node, InterlisGlspTypes.NAME_LABEL_TYPE).contains(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Classifier " + name + " not found"));
    }

    private static List<String> collectDirectLabelTexts(final GNode node, final String type) {
        return node.getChildren().stream()
            .filter(GLabel.class::isInstance)
            .map(GLabel.class::cast)
            .filter(label -> type.equals(label.getType()))
            .map(GLabel::getText)
            .collect(Collectors.toList());
    }

    private static List<String> collectCompartmentTexts(final GNode node, final String compartmentType,
        final String labelType) {
        return node.getChildren().stream()
            .filter(GCompartment.class::isInstance)
            .map(GCompartment.class::cast)
            .filter(comp -> compartmentType.equals(comp.getType()))
            .flatMap(comp -> comp.getChildren().stream())
            .filter(GLabel.class::isInstance)
            .map(GLabel.class::cast)
            .filter(label -> labelType.equals(label.getType()))
            .map(GLabel::getText)
            .collect(Collectors.toList());
    }
}
