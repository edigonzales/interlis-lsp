package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void rendersGraphmlWithNamespacesAndAssociations() throws Exception {
        Path iliFile = tempDir.resolve("GraphModel.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL GraphModel (en)\n" +
                "AT \"http://example.com/GraphModel.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC Demo (ABSTRACT) =\n" +
                "    STRUCTURE Address =\n" +
                "      Street : TEXT*40;\n" +
                "    END Address;\n" +
                "    CLASS Person =\n" +
                "      Name : MANDATORY TEXT*40;\n" +
                "      Home : Address;\n" +
                "      MANDATORY CONSTRAINT Name <> \"\";\n" +
                "    END Person;\n" +
                "    CLASS Employee EXTENDS Person =\n" +
                "    END Employee;\n" +
                "    CLASS AbstractThing (ABSTRACT) =\n" +
                "    END AbstractThing;\n" +
                "    CLASS Company =\n" +
                "      Title : TEXT*80;\n" +
                "    END Company;\n" +
                "    ASSOCIATION WorksAt =\n" +
                "      worker -- {0..*} Employee;\n" +
                "      employer -- {1} Company;\n" +
                "    END WorksAt;\n" +
                "  END Demo;\n" +
                "END GraphModel.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, outcome.getLogText());

        String graphml = Ili2GraphML.render(td);
        assertNotNull(graphml);
        assertTrue(graphml.startsWith("<?xml"));
        assertTrue(graphml.contains("<graphml"));
        assertTrue(graphml.contains("Person"));
        assertTrue(graphml.contains("Address"));
        assertTrue(graphml.contains("Structure"));
        assertFalse(graphml.contains("&lt;&lt;Structure&gt;&gt;"));
        assertTrue(graphml.contains("Name[1] : String"));
        assertTrue(graphml.contains("Constraint1()"));
        assertTrue(graphml.contains("0..* ⟷ 1"));
        assertTrue(graphml.contains("worker–employer"));
        assertTrue(graphml.contains("color=\"#ffcc00\""));
        assertTrue(graphml.contains("color=\"#04b889\""));
        assertTrue(graphml.contains("color=\"#99ccff\""));
        //assertTrue(graphml.contains("constraint=\"abstract\""));
        assertTrue(graphml.contains("constraint=\"\""));
        assertTrue(graphml.contains("yfiles.foldertype=\"group\""));
        assertTrue(graphml.contains(">Demo</y:NodeLabel>"));
    }
}

