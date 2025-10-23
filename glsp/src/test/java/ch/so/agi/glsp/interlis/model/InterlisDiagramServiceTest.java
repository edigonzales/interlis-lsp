package ch.so.agi.glsp.interlis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.graph.GLabel;
import org.junit.jupiter.api.Test;

class InterlisDiagramServiceTest {

    private final InterlisDiagramService service = new InterlisDiagramService();

    @Test
    void loadDiagram_createsNodesForEachInterlisClass() throws URISyntaxException {
        Path modelPath = Path.of(getClass().getResource("/models/simple.ili").toURI());

        Optional<GGraph> result = service.loadDiagram(modelPath);

        assertTrue(result.isPresent());
        GGraph graph = result.orElseThrow();
        assertEquals(3, graph.getChildren().size());

        Set<String> labels = graph.getChildren().stream()
            .map(GNode.class::cast)
            .map(node -> node.getChildren().stream()
                .filter(GLabel.class::isInstance)
                .map(GLabel.class::cast)
                .findFirst()
                .map(GLabel::getText)
                .orElse(""))
            .collect(Collectors.toSet());

        assertTrue(labels.contains("SimpleModel::TopicA.ClassA"));
        assertTrue(labels.contains("SimpleModel::TopicA.ClassB"));
        assertTrue(labels.contains("SimpleModel.RootClass"));

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
}
