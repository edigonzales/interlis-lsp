package ch.so.agi.glsp.interlis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.glsp.interlis.InterlisGlspTypes;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.glsp.graph.GCompartment;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GLabel;
import org.eclipse.glsp.graph.GNode;
import org.junit.jupiter.api.Test;

class InterlisDiagramServiceTest {

    private final InterlisDiagramService service = new InterlisDiagramService();

    @Test
    void buildGraph_addsCompartmentsAndCssClasses() {
        InterlisDiagramService.MemberInfo attribute = new InterlisDiagramService.MemberInfo(
            "interlis-attr-model-topic-structurea-0", "id[1] : Integer");
        InterlisDiagramService.MemberInfo constraint = new InterlisDiagramService.MemberInfo(
            "interlis-constraint-model-topic-structurea-0", "check()");

        InterlisDiagramService.ClassInfo structureInfo = new InterlisDiagramService.ClassInfo(
            "Model::Topic.StructureA",
            "model-topic-structurea",
            "Model::Topic.StructureA",
            InterlisDiagramService.NodeKind.STRUCTURE,
            List.of("Abstract", "Structure"),
            List.of(attribute),
            List.of(constraint));

        GGraph graph = service.buildGraph(List.of(structureInfo));

        assertEquals(InterlisGlspTypes.DIAGRAM_TYPE, graph.getType());
        assertEquals(1, graph.getChildren().size());

        GNode node = (GNode) graph.getChildren().get(0);
        assertTrue(node.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_NODE));
        assertTrue(node.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_STRUCTURE));
        assertTrue(node.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_ABSTRACT));

        GCompartment header = findCompartment(node, InterlisGlspTypes.COMPARTMENT_TYPE_HEADER);
        Optional<GLabel> stereotypeLabel = header.getChildren().stream()
            .filter(GLabel.class::isInstance)
            .map(GLabel.class::cast)
            .filter(label -> InterlisGlspTypes.LABEL_TYPE_STEREOTYPE.equals(label.getType()))
            .findFirst();
        assertTrue(stereotypeLabel.isPresent());
        assertEquals("«Abstract»\n«Structure»", stereotypeLabel.get().getText());

        GCompartment attributes = findCompartment(node, InterlisGlspTypes.COMPARTMENT_TYPE_ATTRIBUTES);
        assertEquals(1, attributes.getChildren().size());
        assertEquals("id[1] : Integer", ((GLabel) attributes.getChildren().get(0)).getText());

        GCompartment constraints = findCompartment(node, InterlisGlspTypes.COMPARTMENT_TYPE_CONSTRAINTS);
        assertEquals(1, constraints.getChildren().size());
        assertEquals("check()", ((GLabel) constraints.getChildren().get(0)).getText());
    }

    @Test
    void buildGraph_rendersEnumerationLiteralsAsAttributes() {
        InterlisDiagramService.MemberInfo literalA = new InterlisDiagramService.MemberInfo(
            "interlis-literal-model-domaina-0", "A");
        InterlisDiagramService.MemberInfo literalB = new InterlisDiagramService.MemberInfo(
            "interlis-literal-model-domaina-1", "B");

        InterlisDiagramService.ClassInfo enumerationInfo = new InterlisDiagramService.ClassInfo(
            "Model.DomainA",
            "model-domaina",
            "Model.DomainA",
            InterlisDiagramService.NodeKind.ENUMERATION,
            List.of("Enumeration"),
            List.of(literalA, literalB),
            List.of());

        GGraph graph = service.buildGraph(List.of(enumerationInfo));
        GNode node = (GNode) graph.getChildren().get(0);

        assertTrue(node.getCssClasses().contains(InterlisGlspTypes.CSS_CLASS_ENUMERATION));

        GCompartment attributes = findCompartment(node, InterlisGlspTypes.COMPARTMENT_TYPE_ATTRIBUTES);
        List<String> literalTexts = attributes.getChildren().stream()
            .filter(GLabel.class::isInstance)
            .map(GLabel.class::cast)
            .map(GLabel::getText)
            .collect(Collectors.toList());
        assertEquals(List.of("A", "B"), literalTexts);
    }

    private GCompartment findCompartment(final GNode node, final String compartmentType) {
        return node.getChildren().stream()
            .filter(GCompartment.class::isInstance)
            .map(GCompartment.class::cast)
            .filter(compartment -> compartmentType.equals(compartment.getType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing compartment: " + compartmentType));
    }
}
