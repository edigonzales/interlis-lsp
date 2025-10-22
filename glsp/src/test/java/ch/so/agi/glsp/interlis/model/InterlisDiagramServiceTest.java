package ch.so.agi.glsp.interlis.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GModelRoot;
import org.junit.jupiter.api.Test;

class InterlisDiagramServiceTest {

    @Test
    void createModel_buildsNodesForClasses() throws Exception {
        Path source = copyResource("models/SampleModel.ili");
        InterlisDiagramService service = new InterlisDiagramService();

        GModelRoot root = service.createModel(Map.of("uri", source.toUri().toString()));

        assertNotNull(root, "Expected a GLSP root model");

        List<GModelElement> children = root.getChildren();
        assertFalse(children.isEmpty(), "Expected at least one diagram element");

        long classNodes = children.stream()
                .filter(element -> "interlis-class".equals(element.getType()))
                .count();

        assertTrue(classNodes >= 1, "Expected at least one INTERLIS class node");
    }

    private Path copyResource(String resourcePath) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Missing test resource: " + resourcePath);
            Path file = Files.createTempFile("interlis-diagram-", ".ili");
            file.toFile().deleteOnExit();
            Files.write(file, in.readAllBytes());
            return file;
        }
    }
}
