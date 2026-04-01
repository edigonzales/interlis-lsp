package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.diagram.Ili2GraphML;
import ch.so.agi.lsp.interlis.diagram.UmlAttributeMode;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rendersInlineAndDomainEnumerationValues() throws Exception {
        Path iliFile = tempDir.resolve("GraphEnumModel.ili");
        TransferDescription td = compileEnumDiagramModel(iliFile);

        String graphml = Ili2GraphML.render(td);
        assertNotNull(graphml);
        assertEquals(graphml, Ili2GraphML.render(td, UmlAttributeMode.OWN));
        assertTrue(graphml.contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(graphml.contains("Aaaaaamyenum[0..1] : MyBase"));
        assertTrue(graphml.contains("FarbenAttr[0..1] : Farben"));
        assertTrue(graphml.contains("gruen"));
        assertTrue(graphml.contains("blau"));
        assertTrue(graphml.contains("rot.hell"));
        assertTrue(graphml.contains("rot.dunkel"));
        assertTrue(graphml.contains("rot"));
    }

    @Test
    void hidesAttributesAndEnumerationValuesWhenAttributeModeIsNone() throws Exception {
        TransferDescription td = compileEnumDiagramModel(tempDir.resolve("GraphEnumModelNone.ili"));

        String graphml = Ili2GraphML.render(td, UmlAttributeMode.NONE);

        assertFalse(graphml.contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(graphml.contains("FarbenAttr[0..1] : Farben"));
        assertFalse(graphml.contains("gruen"));
        assertFalse(graphml.contains("rot.hell"));
    }

    @Test
    void hidesConstraintsWhenAttributeModeIsNone() throws Exception {
        TransferDescription td = compileInheritedDiagramModel(tempDir.resolve("GraphInheritedModel.ili"));

        String graphmlOwn = Ili2GraphML.render(td, UmlAttributeMode.OWN);
        String graphmlNone = Ili2GraphML.render(td, UmlAttributeMode.NONE);
        String graphmlInherited = Ili2GraphML.render(td, UmlAttributeMode.OWN_AND_INHERITED);

        assertTrue(graphmlOwn.contains("ChildName[0..1] : String"));
        assertFalse(graphmlOwn.contains("Base.BaseName[0..1] : String"));
        assertFalse(graphmlOwn.contains("GrandBase.LegacyId[0..1] : String"));
        assertFalse(graphmlOwn.contains("AddressBase.Street[0..1] : String"));
        assertTrue(graphmlOwn.contains("Constraint1()"));

        assertFalse(graphmlNone.contains("ChildName[0..1] : String"));
        assertFalse(graphmlNone.contains("Base.BaseName[0..1] : String"));
        assertFalse(graphmlNone.contains("GrandBase.LegacyId[0..1] : String"));
        assertFalse(graphmlNone.contains("AddressBase.Street[0..1] : String"));
        assertFalse(graphmlNone.contains("Constraint1()"));

        assertTrue(graphmlInherited.contains("ChildName[0..1] : String"));
        assertTrue(graphmlInherited.contains("Base.BaseName[0..1] : String"));
        assertTrue(graphmlInherited.contains("GrandBase.LegacyId[0..1] : String"));
        assertTrue(graphmlInherited.contains("HouseNo[0..1] : String"));
        assertTrue(graphmlInherited.contains("AddressBase.Street[0..1] : String"));
        assertTrue(graphmlInherited.contains("Constraint1()"));
        assertContainsInOrder(graphmlInherited,
                "ChildName[0..1] : String",
                "Base.BaseName[0..1] : String",
                "GrandBase.LegacyId[0..1] : String");
        assertContainsInOrder(graphmlInherited,
                "HouseNo[0..1] : String",
                "AddressBase.Street[0..1] : String");
    }

    private static TransferDescription compileEnumDiagramModel(Path iliFile) throws Exception {
        Files.writeString(iliFile, """
                INTERLIS 2.3;
                MODEL GraphEnumModel (en)
                AT "http://example.com/GraphEnumModel.ili"
                VERSION "2024-01-01" =
                  TOPIC Demo (ABSTRACT) =
                    DOMAIN Farben = (gruen, blau, rot(hell, dunkel));
                    DOMAIN AlleFarben = ALL OF Farben;
                    CLASS MyBase (ABSTRACT) =
                      Aaaaaamyenum : (foo, bar);
                      FarbenAttr : Farben;
                    END MyBase;
                  END Demo;
                END GraphEnumModel.
                """);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, outcome.getLogText());
        return td;
    }

    private static TransferDescription compileInheritedDiagramModel(Path iliFile) throws Exception {
        Files.writeString(iliFile, """
                INTERLIS 2.3;
                MODEL GraphInheritedModel (en)
                AT "http://example.com/GraphInheritedModel.ili"
                VERSION "2024-01-01" =
                  TOPIC Demo (ABSTRACT) =
                    STRUCTURE AddressBase =
                      Street : TEXT*40;
                    END AddressBase;
                    STRUCTURE AddressChild EXTENDS AddressBase =
                      HouseNo : TEXT*10;
                    END AddressChild;
                    CLASS GrandBase =
                      LegacyId : TEXT*12;
                    END GrandBase;
                    CLASS Base EXTENDS GrandBase =
                      BaseName : TEXT*40;
                    END Base;
                    CLASS Child EXTENDS Base =
                      ChildName : TEXT*60;
                      MANDATORY CONSTRAINT ChildName <> "";
                    END Child;
                  END Demo;
                END GraphInheritedModel.
                """);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, outcome.getLogText());
        return td;
    }

    private static void assertContainsInOrder(String text, String... segments) {
        int cursor = -1;
        for (String segment : segments) {
            int next = text.indexOf(segment, cursor + 1);
            assertTrue(next >= 0, "Expected to find segment: " + segment);
            cursor = next;
        }
    }
}
