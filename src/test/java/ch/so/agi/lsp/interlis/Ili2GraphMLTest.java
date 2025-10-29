package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.interlis.ili2c.metamodel.TransferDescription;

class Ili2GraphMLTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersGraphmlWithDiagramDetails() throws Exception {
        Path iliFile = tempDir.resolve("GraphModel.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL GraphModel (en)\n" +
                "AT \"http://example.com/GraphModel.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC ExampleTopic =\n" +
                "    DOMAIN Status = (Active, Inactive);\n" +
                "    CLASS Base (ABSTRACT) =\n" +
                "      id : MANDATORY TEXT;\n" +
                "    END Base;\n" +
                "    CLASS Derived EXTENDS Base =\n" +
                "      status : MANDATORY Status;\n" +
                "    END Derived;\n" +
                "    STRUCTURE Address =\n" +
                "      street : TEXT;\n" +
                "    END Address;\n" +
                "    ASSOCIATION Ownership =\n" +
                "      owner -- {0..*} Derived;\n" +
                "      asset -- {1} Base;\n" +
                "    END Ownership;\n" +
                "  END ExampleTopic;\n" +
                "END GraphModel.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, outcome.getLogText());

        String graphml = Ili2GraphML.render(td);
        assertTrue(graphml.startsWith("<?xml version=\"1.0\""), graphml);
        assertTrue(graphml.contains("<graphml"));
        assertTrue(graphml.contains("ExampleTopic"));
        assertTrue(graphml.contains("Derived"));
        assertTrue(graphml.contains("&lt;&lt;Enumeration&gt;&gt;"));
        assertTrue(graphml.contains("white_delta"));
        assertTrue(graphml.contains("0..* -- 1"));
        assertTrue(graphml.contains("status[1] : Status"));
    }
}
