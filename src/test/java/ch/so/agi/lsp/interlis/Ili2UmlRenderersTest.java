package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.diagram.Ili2Mermaid;
import ch.so.agi.lsp.interlis.diagram.Ili2PlantUml;
import ch.so.agi.lsp.interlis.server.ClientSettings;

class Ili2UmlRenderersTest {

    @TempDir
    Path tempDir;

    @Test
    void renderersIncludeInlineAndDomainEnumerationValues() throws Exception {
        TransferDescription td = compileEnumDiagramModel(tempDir.resolve("EnumRenderers.ili"));

        String mermaid = Ili2Mermaid.render(td);
        String plantUml = Ili2PlantUml.renderSource(td);

        assertTrue(mermaid.contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(mermaid.contains("Aaaaaamyenum[0..1] : MyBase"));
        assertTrue(mermaid.contains("FarbenAttr[0..1] : Farben"));
        assertTrue(mermaid.contains("rot.hell"));
        assertTrue(mermaid.contains("rot.dunkel"));
        assertTrue(mermaid.contains("\n      rot\n"));

        assertTrue(plantUml.contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(plantUml.contains("Aaaaaamyenum[0..1] : MyBase"));
        assertTrue(plantUml.contains("FarbenAttr[0..1] : Farben"));
        assertTrue(plantUml.contains("rot.hell"));
        assertTrue(plantUml.contains("rot.dunkel"));
        assertTrue(plantUml.contains("\n    rot\n"));
    }

    private static TransferDescription compileEnumDiagramModel(Path iliFile) throws Exception {
        Files.writeString(iliFile, """
                INTERLIS 2.3;
                MODEL EnumRenderers (en)
                AT "http://example.com/EnumRenderers.ili"
                VERSION "2024-01-01" =
                  TOPIC Demo (ABSTRACT) =
                    DOMAIN Farben = (gruen, blau, rot(hell, dunkel));
                    DOMAIN AlleFarben = ALL OF Farben;
                    CLASS MyBase (ABSTRACT) =
                      Aaaaaamyenum : (foo, bar);
                      FarbenAttr : Farben;
                    END MyBase;
                  END Demo;
                END EnumRenderers.
                """);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, outcome.getLogText());
        return td;
    }
}
