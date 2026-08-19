package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.export.docx.IliDocxRenderer;
import ch.so.agi.lsp.interlis.export.docx.InterlisDocxExporter;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.EnumTreeValueType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisDocxExporterTest {

    @Test
    void createsNonEmptyDocxFromEmptyTransferDescription() throws Exception {
        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[0];
            }
        };

        byte[] bytes = InterlisDocxExporter.renderDocx(td, "Example");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Expected exporter to produce non-empty DOCX");
    }

    @Test
    void rendersMetadataDocumentationAndViews(@TempDir Path tempDir) throws Exception {
        Path iliFile = Files.createTempFile(tempDir, "DocTest", ".ili");
        Files.writeString(iliFile, String.join("\n",
                "INTERLIS 2.3;",
                "MODEL DocTest (en)",
                "!!@ title=\"Model Title\"",
                "!!@ shortDescription=\"Model short description\"",
                "AT \"http://example.com/DocTest.ili\"",
                "VERSION \"2024-01-01\" =",
                "  TOPIC DocTopic =",
                "    DOMAIN RoofColor = (",
                "      !!@ ili2db.dispName=Rot",
                "      rot (",
                "        !!@ ili2db.dispName=Hellrot",
                "        hell, dunkel",
                "      ),",
                "      blau",
                "    );",
                "    DOMAIN AllRoofColors = ALL OF RoofColor;",
                "    DOMAIN ShortText = TEXT*20;",
                "    STRUCTURE Address =",
                "      Street : MANDATORY TEXT*50;",
                "    END Address;",
                "    CLASS UniqueBase =",
                "      BaseCode : TEXT*10;",
                "    UNIQUE BaseCode;",
                "    END UniqueBase;",
                "    CLASS UniqueChild EXTENDS UniqueBase =",
                "      ChildName : TEXT*10;",
                "    END UniqueChild;",
                "    CLASS NoUnique =",
                "      Value : TEXT*10;",
                "    END NoUnique;",
                "    CLASS Building =",
                "      Name : MANDATORY ShortText;",
                "      Score : 0..10;",
                "      Address : Address;",
                "      RoofColor : RoofColor;",
                "      Status : (",
                "        !!@ ili2db.dispName=Geplant",
                "        geplant,",
                "        beschlossen_verfuegt,",
                "        abgerissen",
                "      );",
                "    UNIQUE (LOCAL) Address : Street;",
                "    UNIQUE Name;",
                "    UNIQUE Name, Score;",
                "    UNIQUE WHERE Score == 0 : Name, Score;",
                "    END Building;",
                "    ASSOCIATION BuildingLink =",
                "      /** Left role documentation */",
                "      Left -- {0..*} Building;",
                "      /** Right role documentation */",
                "      Right -- {0..*} Building;",
                "      /** Link code documentation */",
                "      LinkCode : TEXT*5;",
                "    UNIQUE Left->Name, Right->Score;",
                "    END BuildingLink;",
                "  END DocTopic;",
                "END DocTest.",
                ""));

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, "Expected compile to produce transfer description");

        Model[] models = td.getModelsFromLastFile();
        assertNotNull(models);
        Model model = models[0];
        model.setMetaValue("title", "Model Title");
        model.setMetaValue("shortDescription", "Model short description");
        assertEquals("Model Title", model.getMetaValue("title"));
        assertEquals("Model short description", model.getMetaValue("shortDescription"));

        Topic topic = findTopic(model, "DocTopic");
        assertNotNull(topic, "Expected topic DocTopic");
        topic.setDocumentation("Topic documentation");

        Table structure = findTable(topic, "Address");
        assertNotNull(structure, "Expected structure Address");
        structure.setDocumentation("Structure documentation");
        try {
            structure.setAbstract(true);
        } catch (java.beans.PropertyVetoException e) {
            throw new IllegalStateException("Unable to mark structure abstract", e);
        }

        Table clazz = findTable(topic, "Building");
        assertNotNull(clazz, "Expected class Building");
        clazz.setDocumentation("Class documentation");

        AssociationDef association = findAssociation(topic, "BuildingLink");
        assertNotNull(association, "Expected association BuildingLink");
        association.setDocumentation("Association documentation");

        AttributeDef statusAttribute = findAttribute(clazz, "Status");
        assertNotNull(statusAttribute, "Expected Status attribute");
        assertTrue(statusAttribute.getDomain() instanceof EnumerationType,
                "Expected inline enumeration for Status attribute");

        Domain enumDomain = findDomain(topic, "RoofColor");
        assertNotNull(enumDomain, "Expected domain RoofColor");
        enumDomain.setDocumentation("Roof color documentation");

        Domain allRoofColors = findDomain(topic, "AllRoofColors");
        assertNotNull(allRoofColors, "Expected domain AllRoofColors");
        allRoofColors.setDocumentation("All roof colors documentation");
        assertTrue(allRoofColors.getType() instanceof EnumTreeValueType,
                "Expected EnumTreeValueType for AllRoofColors");

        EnumerationType enumerationType = (EnumerationType) enumDomain.getType();
        assertNotNull(enumerationType, "Expected enumeration type for RoofColor");
        Enumeration enumeration = enumerationType.getEnumeration();
        assertNotNull(enumeration, "Expected enumeration tree");
        Enumeration.Element rot = enumeration.getElement(0);
        assertNotNull(rot, "Expected rot element");
        rot.setDocumentation("Rot doc");
        Enumeration subRot = rot.getSubEnumeration();
        assertNotNull(subRot, "Expected rot sub enumeration");
        Enumeration.Element hell = subRot.getElement(0);
        hell.setDocumentation("Hell doc");
        Enumeration.Element dunkel = subRot.getElement(1);
        dunkel.setDocumentation("Dunkel doc");
        Enumeration.Element blau = enumeration.getElement(1);
        assertNotNull(blau, "Expected blau element");
        blau.setDocumentation("Blau doc");

        Viewable view = addSyntheticView(topic, "BuildingView");
        view.setDocumentation("View documentation");

        byte[] bytes = InterlisDocxExporter.renderDocx(td, "Document Title");
        assertNotNull(bytes);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            XWPFStyle titleStyle = document.getStyles().getStyle("Title");
            assertNotNull(titleStyle, "Expected title style to exist");
            assertEquals("Arial", titleStyle.getCTStyle().getRPr().getRFontsArray(0).getAscii());
            assertEquals(BigInteger.valueOf(36), titleStyle.getCTStyle().getRPr().getSzArray(0).getVal());

            XWPFStyle heading1 = document.getStyles().getStyle("Heading1");
            assertNotNull(heading1, "Expected heading1 style to exist");
            assertEquals(BigInteger.valueOf(22), heading1.getCTStyle().getRPr().getSzArray(0).getVal());

            CTPageSz pageSize = document.getDocument().getBody().getSectPr().getPgSz();
            assertEquals(BigInteger.valueOf(16838), pageSize.getW());
            assertEquals(BigInteger.valueOf(11906), pageSize.getH());
            assertEquals(STPageOrientation.LANDSCAPE, pageSize.getOrient());

            List<String> paragraphs = extractNonEmptyParagraphTexts(document);
            int modelIndex = indexContaining(paragraphs, "DocTest");
            assertTrue(modelIndex >= 0, "Model heading not found");
            assertTrue(paragraphs.get(modelIndex + 1).contains("Titel: Model Title"),
                    "Expected model title metadata after heading");
            assertTrue(paragraphs.get(modelIndex + 2).contains("Beschreibung: Model short description"),
                    "Expected model short description metadata after title");

            int topicIndex = paragraphs.indexOf("DocTopic (Topic)");
            assertTrue(topicIndex >= 0, "Expected Topic heading to include (Topic)");
            assertTrue(paragraphs.get(topicIndex + 1).contains("Topic documentation"),
                    "Expected topic documentation after heading");

            int structureIndex = indexContaining(paragraphs, "Address (Abstract Structure)");
            assertTrue(paragraphs.get(structureIndex + 1).contains("Structure documentation"),
                    "Expected structure documentation after heading");

            int classIndex = indexContaining(paragraphs, "Building (Class)");
            assertTrue(paragraphs.get(classIndex + 1).contains("Class documentation"),
                    "Expected class documentation after heading");

            int viewIndex = indexContaining(paragraphs, "BuildingView (View)");
            assertTrue(paragraphs.get(viewIndex + 1).contains("View documentation"),
                    "Expected view documentation after heading");

            int associationIndex = indexContaining(paragraphs, "BuildingLink (Association)");
            assertTrue(associationIndex >= 0, "Association heading not found");
            assertTrue(paragraphs.get(associationIndex + 1).contains("Association documentation"),
                    "Expected association documentation after heading");

            int enumIndex = indexContaining(paragraphs, "RoofColor (Enumeration)");
            assertTrue(paragraphs.get(enumIndex + 1).contains("Roof color documentation"),
                    "Expected enumeration documentation after heading");

            int enumTreeIndex = indexContaining(paragraphs, "AllRoofColors (Enumeration)");
            assertTrue(enumTreeIndex >= 0, "Expected AllRoofColors enumeration heading");
            assertTrue(paragraphs.get(enumTreeIndex + 1).contains("All roof colors documentation"),
                    "Expected EnumTreeValueType documentation after heading");

            List<XWPFTable> tables = document.getTables();
            assertTrue(!tables.isEmpty(), "Expected at least one table");
            XWPFTable table = findAttributeTable(tables, "Street");
            assertNotNull(table, "Expected attribute table for Address structure");
            XWPFTableRow headerRow = table.getRow(0);
            assertNotNull(headerRow);
            assertEquals(4, headerRow.getTableCells().size());
            assertEquals("Attributname", headerRow.getCell(0).getText());
            assertEquals("Kardinalität", headerRow.getCell(1).getText());
            assertEquals("Typ", headerRow.getCell(2).getText());
            assertEquals("Beschreibung", headerRow.getCell(3).getText());
            assertFalse(headerRow.getCell(0).getParagraphs().isEmpty());
            assertFalse(headerRow.getCell(0).getParagraphs().get(0).getRuns().isEmpty());
            XWPFRun headerRun = headerRow.getCell(0).getParagraphs().get(0).getRuns().get(0);
            assertTrue(headerRun.isBold(), "Expected header text to be bold");
            assertEquals("Arial", headerRun.getFontFamily());

            assertEquals(BigInteger.valueOf(13500), table.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(BigInteger.valueOf(3000), table.getCTTbl().getTblGrid().getGridColArray(0).getW());
            assertEquals(BigInteger.valueOf(1500), table.getCTTbl().getTblGrid().getGridColArray(1).getW());
            assertEquals(BigInteger.valueOf(2500), table.getCTTbl().getTblGrid().getGridColArray(2).getW());
            assertEquals(BigInteger.valueOf(6500), table.getCTTbl().getTblGrid().getGridColArray(3).getW());

            assertEquals(BigInteger.valueOf(4), table.getCTTbl().getTblPr().getTblBorders().getTop().getSz());
            assertEquals(BigInteger.valueOf(4), table.getCTTbl().getTblPr().getTblBorders().getInsideV().getSz());

            XWPFTableRow streetRow = findAttributeRow(table, "Street");
            assertNotNull(streetRow, "Expected Street attribute row");
            assertEquals("TEXT*50", streetRow.getCell(2).getText());

            XWPFTable classTable = findAttributeTable(tables, "Name");
            assertNotNull(classTable, "Expected attribute table for Building class");
            XWPFTableRow nameRow = findAttributeRow(classTable, "Name");
            assertNotNull(nameRow, "Expected Name attribute row");
            assertEquals("TEXT*20", nameRow.getCell(2).getText());
            XWPFTableRow scoreRow = findAttributeRow(classTable, "Score");
            assertNotNull(scoreRow, "Expected Score attribute row");
            assertEquals("0..10", scoreRow.getCell(2).getText());

            List<XWPFTable> uniqueTables = tables.stream()
                    .filter(t -> t.getRow(0) != null && t.getRow(0).getCell(1) != null
                            && "UNIQUE-Definition".equals(t.getRow(0).getCell(1).getText()))
                    .collect(Collectors.toList());
            assertEquals(4, uniqueTables.size(),
                    "Expected UNIQUE tables for Building, BuildingLink, UniqueBase and UniqueChild");
            assertTrue(paragraphs.stream().filter("UNIQUE"::equals).count() >= 4,
                    "Expected a UNIQUE label before each non-empty UNIQUE table");

            XWPFTable buildingUniqueTable = findUniqueTable(tables, "UNIQUE (GLOBAL) Name;");
            assertNotNull(buildingUniqueTable, "Expected UNIQUE table for Building");
            assertEquals(3, buildingUniqueTable.getRow(0).getTableCells().size());
            assertEquals("Nr.", buildingUniqueTable.getRow(0).getCell(0).getText());
            assertEquals("UNIQUE-Definition", buildingUniqueTable.getRow(0).getCell(1).getText());
            assertEquals("Herkunft", buildingUniqueTable.getRow(0).getCell(2).getText());
            assertEquals(BigInteger.valueOf(13500),
                    buildingUniqueTable.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(BigInteger.valueOf(1000),
                    buildingUniqueTable.getCTTbl().getTblGrid().getGridColArray(0).getW());
            assertEquals(BigInteger.valueOf(10000),
                    buildingUniqueTable.getCTTbl().getTblGrid().getGridColArray(1).getW());
            assertEquals(BigInteger.valueOf(2500),
                    buildingUniqueTable.getCTTbl().getTblGrid().getGridColArray(2).getW());

            XWPFTableRow localUniqueRow = findUniqueRowInAnyTable(uniqueTables,
                    "UNIQUE (LOCAL) Address : Street;");
            assertNotNull(localUniqueRow, "Expected local/prefix UNIQUE definition");
            assertEquals("U1", localUniqueRow.getCell(0).getText());
            assertEquals("direkt", localUniqueRow.getCell(2).getText());
            assertNotNull(findUniqueRow(buildingUniqueTable, "UNIQUE (GLOBAL) Name, Score;"),
                    "Expected combined UNIQUE definition");
            assertNotNull(findUniqueRow(buildingUniqueTable,
                    "UNIQUE (GLOBAL) Name, Score WHERE Score == 0;"),
                    "Expected WHERE UNIQUE definition");

            XWPFTableRow associationUniqueRow = findUniqueRowInAnyTable(uniqueTables,
                    "UNIQUE (GLOBAL) Left->Name, Right->Score;");
            assertNotNull(associationUniqueRow, "Expected role paths in Association UNIQUE definition");

            XWPFTable roleTable = findTableByHeader(tables, "Rollenname");
            assertNotNull(roleTable, "Expected Association role table");
            assertEquals(4, roleTable.getRow(0).getTableCells().size());
            assertEquals("Rollenname", roleTable.getRow(0).getCell(0).getText());
            assertEquals("Kardinalität", roleTable.getRow(0).getCell(1).getText());
            assertEquals("Typ", roleTable.getRow(0).getCell(2).getText());
            assertEquals("Beschreibung", roleTable.getRow(0).getCell(3).getText());
            assertEquals(BigInteger.valueOf(13500), roleTable.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(BigInteger.valueOf(3000), roleTable.getCTTbl().getTblGrid().getGridColArray(0).getW());
            assertEquals(BigInteger.valueOf(1500), roleTable.getCTTbl().getTblGrid().getGridColArray(1).getW());
            assertEquals(BigInteger.valueOf(2500), roleTable.getCTTbl().getTblGrid().getGridColArray(2).getW());
            assertEquals(BigInteger.valueOf(6500), roleTable.getCTTbl().getTblGrid().getGridColArray(3).getW());

            XWPFTableRow leftRoleRow = findRowByFirstCell(roleTable, "Left");
            assertNotNull(leftRoleRow, "Expected Left role row");
            assertEquals("0..*", leftRoleRow.getCell(1).getText());
            assertEquals("Building", leftRoleRow.getCell(2).getText());
            assertEquals("Left role documentation", leftRoleRow.getCell(3).getText());
            XWPFTableRow rightRoleRow = findRowByFirstCell(roleTable, "Right");
            assertNotNull(rightRoleRow, "Expected Right role row");
            assertEquals("0..*", rightRoleRow.getCell(1).getText());
            assertEquals("Building", rightRoleRow.getCell(2).getText());
            assertEquals("Right role documentation", rightRoleRow.getCell(3).getText());

            XWPFTable associationAttributeTable = findAttributeTable(tables, "LinkCode");
            assertNotNull(associationAttributeTable, "Expected Association attribute table");
            assertEquals("Attributname", associationAttributeTable.getRow(0).getCell(0).getText());
            XWPFTableRow linkCodeRow = findAttributeRow(associationAttributeTable, "LinkCode");
            assertNotNull(linkCodeRow, "Expected LinkCode attribute row");
            assertEquals("TEXT*5", linkCodeRow.getCell(2).getText());
            assertEquals("Link code documentation", linkCodeRow.getCell(3).getText());
            assertTrue(tables.indexOf(roleTable) < tables.indexOf(associationAttributeTable),
                    "Expected role table before Association attribute table");
            XWPFTable associationUniqueTable = findUniqueTable(tables,
                    "UNIQUE (GLOBAL) Left->Name, Right->Score;");
            assertNotNull(associationUniqueTable, "Expected Association UNIQUE table");
            assertTrue(tables.indexOf(associationAttributeTable) < tables.indexOf(associationUniqueTable),
                    "Expected Association attribute table before UNIQUE table");

            XWPFTableRow inheritedUniqueRow = findUniqueRowInAnyTable(uniqueTables,
                    "UNIQUE (GLOBAL) BaseCode;", "geerbt von DocTest.DocTopic.UniqueBase");
            assertNotNull(inheritedUniqueRow, "Expected inherited UNIQUE definition");

            List<XWPFTable> enumerationTables = tables.stream()
                    .filter(t -> t.getRow(0) != null && t.getRow(0).getCell(0) != null
                            && "Wert".equals(t.getRow(0).getCell(0).getText()))
                    .collect(Collectors.toList());
            assertEquals(2, enumerationTables.size(), "Expected tables for both enumerations");

            XWPFTable enumTreeTable = enumerationTables.get(0);
            assertEquals(BigInteger.valueOf(13500), enumTreeTable.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(BigInteger.valueOf(3000), enumTreeTable.getCTTbl().getTblGrid().getGridColArray(0).getW());
            assertEquals(BigInteger.valueOf(3500), enumTreeTable.getCTTbl().getTblGrid().getGridColArray(1).getW());
            assertEquals(BigInteger.valueOf(7000), enumTreeTable.getCTTbl().getTblGrid().getGridColArray(2).getW());
            assertEquals("Wert", enumTreeTable.getRow(0).getCell(0).getText());
            assertEquals("Anzeigename", enumTreeTable.getRow(0).getCell(1).getText());
            assertEquals("Beschreibung", enumTreeTable.getRow(0).getCell(2).getText());
            assertEquals("rot", enumTreeTable.getRow(1).getCell(0).getText());
            assertEquals("Rot", enumTreeTable.getRow(1).getCell(1).getText());
            assertEquals("Rot doc", enumTreeTable.getRow(1).getCell(2).getText());
            assertEquals("rot.hell", enumTreeTable.getRow(2).getCell(0).getText());
            assertEquals("Hellrot", enumTreeTable.getRow(2).getCell(1).getText());
            assertEquals("Hell doc", enumTreeTable.getRow(2).getCell(2).getText());
            assertEquals("rot.dunkel", enumTreeTable.getRow(3).getCell(0).getText());
            assertEquals("", enumTreeTable.getRow(3).getCell(1).getText());
            assertEquals("Dunkel doc", enumTreeTable.getRow(3).getCell(2).getText());
            assertEquals("blau", enumTreeTable.getRow(4).getCell(0).getText());
            assertEquals("", enumTreeTable.getRow(4).getCell(1).getText());
            assertEquals("Blau doc", enumTreeTable.getRow(4).getCell(2).getText());

            XWPFTable enumTable = enumerationTables.get(1);
            assertEquals(BigInteger.valueOf(13500), enumTable.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(BigInteger.valueOf(3000), enumTable.getCTTbl().getTblGrid().getGridColArray(0).getW());
            assertEquals(BigInteger.valueOf(3500), enumTable.getCTTbl().getTblGrid().getGridColArray(1).getW());
            assertEquals(BigInteger.valueOf(7000), enumTable.getCTTbl().getTblGrid().getGridColArray(2).getW());
            assertEquals("Wert", enumTable.getRow(0).getCell(0).getText());
            assertEquals("Anzeigename", enumTable.getRow(0).getCell(1).getText());
            assertEquals("Beschreibung", enumTable.getRow(0).getCell(2).getText());
            assertEquals("rot.hell", enumTable.getRow(1).getCell(0).getText());
            assertEquals("Hellrot", enumTable.getRow(1).getCell(1).getText());
            assertEquals("Hell doc", enumTable.getRow(1).getCell(2).getText());
            assertEquals("rot.dunkel", enumTable.getRow(2).getCell(0).getText());
            assertEquals("", enumTable.getRow(2).getCell(1).getText());
            assertEquals("Dunkel doc", enumTable.getRow(2).getCell(2).getText());
            assertEquals("blau", enumTable.getRow(3).getCell(0).getText());
            assertEquals("", enumTable.getRow(3).getCell(1).getText());
            assertEquals("Blau doc", enumTable.getRow(3).getCell(2).getText());

            List<IliDocxRenderer.EnumEntry> enumEntries = IliDocxRenderer
                    .collectEnumerationEntries((AbstractEnumerationType) enumerationType);
            assertEquals(3, enumEntries.size(), "Expected EnumerationType entries to contain only leaves");
            assertTrue(enumEntries.stream().noneMatch(entry -> "rot".equals(entry.value())),
                    "EnumerationType entries should not contain intermediate nodes");

            List<IliDocxRenderer.EnumEntry> enumTreeEntries = IliDocxRenderer
                    .collectEnumerationEntries((AbstractEnumerationType) allRoofColors.getType());
            assertEquals(4, enumTreeEntries.size(), "Expected EnumTreeValueType entries");

            boolean foundInlineEnum = false;
            for (XWPFTable attrTable : tables) {
                if (attrTable.getRow(0) == null || attrTable.getRow(0).getCell(0) == null
                        || !"Attributname".equals(attrTable.getRow(0).getCell(0).getText())) {
                    continue;
                }
                for (int i = 1; i < attrTable.getNumberOfRows(); i++) {
                    XWPFTableRow row = attrTable.getRow(i);
                    if (row.getCell(0) != null && "Status".equals(row.getCell(0).getText())) {
                        assertEquals("Enumeration", row.getCell(2).getText().trim());
                        assertEquals("geplant (Geplant), beschlossen_verfuegt, abgerissen", row.getCell(3).getText().trim());
                        foundInlineEnum = true;
                    }
                }
            }
            assertTrue(foundInlineEnum, "Expected inline enumeration values in description column");
        }
    }

    private static Topic findTopic(Model model, String name) {
        if (model == null || name == null) {
            return null;
        }
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof Topic topic && name.equals(topic.getName())) {
                return topic;
            }
        }
        return null;
    }

    private static Table findTable(Topic topic, String name) {
        if (topic == null || name == null) {
            return null;
        }
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof Table table && name.equals(table.getName())) {
                return table;
            }
        }
        return null;
    }

    private static AssociationDef findAssociation(Topic topic, String name) {
        if (topic == null || name == null) {
            return null;
        }
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof AssociationDef association && name.equals(association.getName())) {
                return association;
            }
        }
        return null;
    }

    private static Domain findDomain(Topic topic, String name) {
        if (topic == null || name == null) {
            return null;
        }
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof Domain domain && name.equals(domain.getName())) {
                return domain;
            }
        }
        return null;
    }

    private static AttributeDef findAttribute(Table table, String name) {
        if (table == null || name == null) {
            return null;
        }
        for (Iterator<?> it = table.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof AttributeDef attribute && name.equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }

    private static Viewable addSyntheticView(Topic topic, String name) {
        View view = new View() {};
        try {
            view.setName(name);
        } catch (java.beans.PropertyVetoException e) {
            throw new IllegalStateException("Unable to name synthetic view", e);
        }
        topic.add(view);
        return view;
    }

    private static List<String> extractNonEmptyParagraphTexts(XWPFDocument document) {
        List<String> texts = new ArrayList<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && !text.isBlank()) {
                texts.add(text);
            }
        }
        return texts;
    }

    private static XWPFTable findAttributeTable(List<XWPFTable> tables, String attributeName) {
        if (tables == null || attributeName == null) {
            return null;
        }
        for (XWPFTable table : tables) {
            if (table == null || table.getRow(0) == null || table.getRow(0).getCell(0) == null
                    || !"Attributname".equals(table.getRow(0).getCell(0).getText())) {
                continue;
            }
            if (findAttributeRow(table, attributeName) != null) {
                return table;
            }
        }
        return null;
    }

    private static XWPFTableRow findAttributeRow(XWPFTable table, String attributeName) {
        if (table == null || attributeName == null) {
            return null;
        }
        for (int i = 1; i < table.getNumberOfRows(); i++) {
            XWPFTableRow row = table.getRow(i);
            if (row != null && row.getCell(0) != null && attributeName.equals(row.getCell(0).getText())) {
                return row;
            }
        }
        return null;
    }

    private static XWPFTable findTableByHeader(List<XWPFTable> tables, String firstHeader) {
        if (tables == null || firstHeader == null) {
            return null;
        }
        for (XWPFTable table : tables) {
            if (table != null && table.getRow(0) != null && table.getRow(0).getCell(0) != null
                    && firstHeader.equals(table.getRow(0).getCell(0).getText())) {
                return table;
            }
        }
        return null;
    }

    private static XWPFTableRow findRowByFirstCell(XWPFTable table, String value) {
        if (table == null || value == null) {
            return null;
        }
        for (int i = 1; i < table.getNumberOfRows(); i++) {
            XWPFTableRow row = table.getRow(i);
            if (row != null && row.getCell(0) != null && value.equals(row.getCell(0).getText())) {
                return row;
            }
        }
        return null;
    }

    private static XWPFTable findUniqueTable(List<XWPFTable> tables, String definition) {
        if (tables == null || definition == null) {
            return null;
        }
        for (XWPFTable table : tables) {
            if (findUniqueRow(table, definition) != null) {
                return table;
            }
        }
        return null;
    }

    private static XWPFTableRow findUniqueRowInAnyTable(List<XWPFTable> tables, String definition) {
        if (tables == null || definition == null) {
            return null;
        }
        for (XWPFTable table : tables) {
            XWPFTableRow row = findUniqueRow(table, definition);
            if (row != null) {
                return row;
            }
        }
        return null;
    }

    private static XWPFTableRow findUniqueRowInAnyTable(List<XWPFTable> tables, String definition,
            String originPrefix) {
        if (tables == null || definition == null || originPrefix == null) {
            return null;
        }
        for (XWPFTable table : tables) {
            XWPFTableRow row = findUniqueRow(table, definition);
            if (row != null && row.getCell(2) != null && row.getCell(2).getText().startsWith(originPrefix)) {
                return row;
            }
        }
        return null;
    }

    private static XWPFTableRow findUniqueRow(XWPFTable table, String definition) {
        if (table == null || definition == null || table.getRow(0) == null
                || table.getRow(0).getCell(1) == null
                || !"UNIQUE-Definition".equals(table.getRow(0).getCell(1).getText())) {
            return null;
        }
        for (int i = 1; i < table.getNumberOfRows(); i++) {
            XWPFTableRow row = table.getRow(i);
            if (row != null && row.getCell(1) != null && definition.equals(row.getCell(1).getText())) {
                return row;
            }
        }
        return null;
    }

    private static int indexContaining(List<String> texts, String needle) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
