package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.diagram.Ili2Mermaid;
import ch.so.agi.lsp.interlis.diagram.Ili2PlantUml;
import ch.so.agi.lsp.interlis.diagram.StaticUmlRenderOptions;
import ch.so.agi.lsp.interlis.diagram.UmlAttributeMode;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

class Ili2UmlRenderersTest {

    @TempDir
    Path tempDir;

    @Test
    void renderersDefaultToOwnAttributeMode() throws Exception {
        TransferDescription td = compileEnumDiagramModel(tempDir.resolve("EnumRenderers.ili"));

        String mermaid = Ili2Mermaid.render(td);
        String plantUml = Ili2PlantUml.renderSource(td);

        assertEquals(mermaid, Ili2Mermaid.render(td, UmlAttributeMode.OWN));
        assertEquals(plantUml, Ili2PlantUml.renderSource(td, UmlAttributeMode.OWN));
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

    @Test
    void renderersHideAttributesAndEnumerationValuesWhenAttributeModeIsNone() throws Exception {
        TransferDescription td = compileEnumDiagramModel(tempDir.resolve("EnumRenderersNone.ili"));

        String mermaid = Ili2Mermaid.render(td, UmlAttributeMode.NONE);
        String plantUml = Ili2PlantUml.renderSource(td, UmlAttributeMode.NONE);

        assertFalse(mermaid.contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(mermaid.contains("FarbenAttr[0..1] : Farben"));
        assertFalse(mermaid.contains("rot.hell"));
        assertFalse(mermaid.contains("\n      rot\n"));

        assertFalse(plantUml.contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(plantUml.contains("FarbenAttr[0..1] : Farben"));
        assertFalse(plantUml.contains("rot.hell"));
        assertFalse(plantUml.contains("\n    rot\n"));
    }

    @Test
    void renderersHideConstraintsWhenAttributeModeIsNone() throws Exception {
        TransferDescription td = compileInheritedDiagramModel(tempDir.resolve("InheritedRenderers.ili"));

        String mermaidOwn = Ili2Mermaid.render(td, UmlAttributeMode.OWN);
        String plantUmlOwn = Ili2PlantUml.renderSource(td, UmlAttributeMode.OWN);
        String mermaidNone = Ili2Mermaid.render(td, UmlAttributeMode.NONE);
        String plantUmlNone = Ili2PlantUml.renderSource(td, UmlAttributeMode.NONE);
        String mermaidInherited = Ili2Mermaid.render(td, UmlAttributeMode.OWN_AND_INHERITED);
        String plantUmlInherited = Ili2PlantUml.renderSource(td, UmlAttributeMode.OWN_AND_INHERITED);

        assertTrue(mermaidOwn.contains("ChildName[0..1] : String"));
        assertFalse(mermaidOwn.contains("Base.BaseName[0..1] : String"));
        assertFalse(mermaidOwn.contains("GrandBase.LegacyId[0..1] : String"));
        assertFalse(mermaidOwn.contains("AddressBase.Street[0..1] : String"));
        assertTrue(mermaidOwn.contains("Constraint1()"));

        assertTrue(plantUmlOwn.contains("ChildName[0..1] : String"));
        assertFalse(plantUmlOwn.contains("Base.BaseName[0..1] : String"));
        assertFalse(plantUmlOwn.contains("GrandBase.LegacyId[0..1] : String"));
        assertFalse(plantUmlOwn.contains("AddressBase.Street[0..1] : String"));
        assertTrue(plantUmlOwn.contains("Constraint1()"));

        assertFalse(mermaidNone.contains("ChildName[0..1] : String"));
        assertFalse(mermaidNone.contains("Base.BaseName[0..1] : String"));
        assertFalse(mermaidNone.contains("GrandBase.LegacyId[0..1] : String"));
        assertFalse(mermaidNone.contains("AddressBase.Street[0..1] : String"));
        assertFalse(mermaidNone.contains("Constraint1()"));

        assertFalse(plantUmlNone.contains("ChildName[0..1] : String"));
        assertFalse(plantUmlNone.contains("Base.BaseName[0..1] : String"));
        assertFalse(plantUmlNone.contains("GrandBase.LegacyId[0..1] : String"));
        assertFalse(plantUmlNone.contains("AddressBase.Street[0..1] : String"));
        assertFalse(plantUmlNone.contains("Constraint1()"));

        assertTrue(mermaidInherited.contains("ChildName[0..1] : String"));
        assertTrue(mermaidInherited.contains("Base.BaseName[0..1] : String"));
        assertTrue(mermaidInherited.contains("GrandBase.LegacyId[0..1] : String"));
        assertTrue(mermaidInherited.contains("HouseNo[0..1] : String"));
        assertTrue(mermaidInherited.contains("AddressBase.Street[0..1] : String"));
        assertTrue(mermaidInherited.contains("Constraint1()"));
        assertContainsInOrder(mermaidInherited,
                "ChildName[0..1] : String",
                "Base.BaseName[0..1] : String",
                "GrandBase.LegacyId[0..1] : String");
        assertContainsInOrder(mermaidInherited,
                "HouseNo[0..1] : String",
                "AddressBase.Street[0..1] : String");

        assertTrue(plantUmlInherited.contains("ChildName[0..1] : String"));
        assertTrue(plantUmlInherited.contains("Base.BaseName[0..1] : String"));
        assertTrue(plantUmlInherited.contains("GrandBase.LegacyId[0..1] : String"));
        assertTrue(plantUmlInherited.contains("HouseNo[0..1] : String"));
        assertTrue(plantUmlInherited.contains("AddressBase.Street[0..1] : String"));
        assertTrue(plantUmlInherited.contains("Constraint1()"));
        assertContainsInOrder(plantUmlInherited,
                "ChildName[0..1] : String",
                "Base.BaseName[0..1] : String",
                "GrandBase.LegacyId[0..1] : String");
        assertContainsInOrder(plantUmlInherited,
                "HouseNo[0..1] : String",
                "AddressBase.Street[0..1] : String");
    }

    @Test
    void renderersDeemphasizeAbstractClassesAndStructuresByDefault() throws Exception {
        TransferDescription td = compileAbstractStylingDiagramModel(tempDir.resolve("AbstractStyling.ili"));

        String mermaid = Ili2Mermaid.render(td);
        String mermaidDisabled = Ili2Mermaid.render(td, new StaticUmlRenderOptions(UmlAttributeMode.OWN, false));
        String plantUml = Ili2PlantUml.renderSource(td);
        String plantUmlDisabled = Ili2PlantUml.renderSource(td,
                new StaticUmlRenderOptions(UmlAttributeMode.OWN, false));

        assertTrue(mermaid.contains(
                "classDef mutedAbstract fill:#F3F3F3,stroke:#D6D6D6,color:#A6A6A6"));
        assertTrue(mermaid.contains(
                "class AbstractStyling.Demo.AbstractBase[\"AbstractBase\"]:::mutedAbstract {"));
        assertTrue(mermaid.contains(
                "class AbstractStyling.Demo.AbstractAddress[\"AbstractAddress\"]:::mutedAbstract {"));
        assertFalse(mermaid.contains(
                "class AbstractStyling.Demo.ConcreteThing[\"ConcreteThing\"]:::mutedAbstract {"));
        assertFalse(mermaid.contains("cssClass \""));
        assertFalse(mermaidDisabled.contains("classDef mutedAbstract"));
        assertFalse(mermaidDisabled.contains(":::mutedAbstract"));

        assertTrue(plantUml.contains(
                "skinparam class<<Abstract>> {"));
        assertTrue(plantUml.contains(
                "BackgroundColor #F3F3F3"));
        assertTrue(plantUml.contains(
                "BorderColor #D6D6D6"));
        assertTrue(plantUml.contains(
                "FontColor #A6A6A6"));
        assertTrue(plantUml.contains(
                "StereotypeFontColor #A6A6A6"));
        assertTrue(plantUml.contains(
                "class AbstractStyling_Demo_AbstractBase as \"AbstractBase\" <<Abstract>> {"));
        assertTrue(plantUml.contains(
                "struct AbstractStyling_Demo_AbstractAddress as \"AbstractAddress\" <<Abstract>> {"));
        assertFalse(plantUml.contains(
                "#back:#F3F3F3;line:#D6D6D6;text:#A6A6A6"));
        assertFalse(plantUml.contains("<<abstract>>"));
        assertFalse(plantUmlDisabled.contains("skinparam class<<Abstract>> {"));
    }

    @Test
    void plantUmlAbstractStylingRendersWithoutSyntaxErrors() throws Exception {
        TransferDescription td = compileAbstractStylingDiagramModel(tempDir.resolve("AbstractStylingPlantSyntax.ili"));

        String plantUml = Ili2PlantUml.renderSource(td);

        SourceStringReader reader = new SourceStringReader(plantUml);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var description = reader.outputImage(output, new FileFormatOption(FileFormat.SVG));
        String svg = output.toString(StandardCharsets.UTF_8);

        assertNotNull(description);
        assertTrue(svg.contains("<svg"), svg);
        assertFalse(svg.contains("Syntax Error"), svg);
        assertFalse(description.getDescription().contains("Error"), description.getDescription());
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

    private static TransferDescription compileInheritedDiagramModel(Path iliFile) throws Exception {
        Files.writeString(iliFile, """
                INTERLIS 2.3;
                MODEL InheritedRenderers (en)
                AT "http://example.com/InheritedRenderers.ili"
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
                END InheritedRenderers.
                """);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, outcome.getLogText());
        return td;
    }

    private static TransferDescription compileAbstractStylingDiagramModel(Path iliFile) throws Exception {
        Files.writeString(iliFile, """
                INTERLIS 2.3;
                MODEL AbstractStyling (en)
                AT "http://example.com/AbstractStyling.ili"
                VERSION "2024-01-01" =
                  TOPIC Demo (ABSTRACT) =
                    STRUCTURE AbstractAddress (ABSTRACT) =
                      Street : TEXT*40;
                    END AbstractAddress;
                    CLASS AbstractBase (ABSTRACT) =
                      Name : TEXT*40;
                    END AbstractBase;
                    CLASS ConcreteThing EXTENDS AbstractBase =
                    END ConcreteThing;
                  END Demo;
                END AbstractStyling.
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
