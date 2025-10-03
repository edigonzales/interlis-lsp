package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ili2c.metamodel.Table;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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
                "    STRUCTURE Address =",
                "      Street : MANDATORY TEXT*50;",
                "    END Address;",
                "    CLASS Building =",
                "      Name : MANDATORY TEXT*50;",
                "      Address : Address;",
                "    END Building;",
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

        Table clazz = findTable(topic, "Building");
        assertNotNull(clazz, "Expected class Building");
        clazz.setDocumentation("Class documentation");

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

            List<String> paragraphs = extractNonEmptyParagraphTexts(document);
            int modelIndex = indexContaining(paragraphs, "DocTest");
            assertTrue(modelIndex >= 0, "Model heading not found");
            assertTrue(paragraphs.get(modelIndex + 1).contains("Titel: Model Title"),
                    "Expected model title metadata after heading");
            assertTrue(paragraphs.get(modelIndex + 2).contains("Beschreibung: Model short description"),
                    "Expected model short description metadata after title");

            int topicIndex = indexContaining(paragraphs, "DocTopic");
            assertTrue(paragraphs.get(topicIndex + 1).contains("Topic documentation"),
                    "Expected topic documentation after heading");

            int structureIndex = indexContaining(paragraphs, "Address (Structure)");
            assertTrue(paragraphs.get(structureIndex + 1).contains("Structure documentation"),
                    "Expected structure documentation after heading");

            int classIndex = indexContaining(paragraphs, "Building (Class)");
            assertTrue(paragraphs.get(classIndex + 1).contains("Class documentation"),
                    "Expected class documentation after heading");

            int viewIndex = indexContaining(paragraphs, "BuildingView (View)");
            assertTrue(paragraphs.get(viewIndex + 1).contains("View documentation"),
                    "Expected view documentation after heading");

            List<XWPFTable> tables = document.getTables();
            assertTrue(!tables.isEmpty(), "Expected at least one table");
            XWPFTable table = tables.get(0);
            XWPFTableRow headerRow = table.getRow(0);
            assertNotNull(headerRow);
            assertFalse(headerRow.getCell(0).getParagraphs().isEmpty());
            assertFalse(headerRow.getCell(0).getParagraphs().get(0).getRuns().isEmpty());
            XWPFRun headerRun = headerRow.getCell(0).getParagraphs().get(0).getRuns().get(0);
            assertTrue(headerRun.isBold(), "Expected header text to be bold");
            assertEquals("Arial", headerRun.getFontFamily());

            assertEquals(BigInteger.valueOf(9000), table.getCTTbl().getTblPr().getTblW().getW());
            assertEquals(BigInteger.valueOf(2250), table.getCTTbl().getTblGrid().getGridColArray(0).getW());
            assertEquals(BigInteger.valueOf(1500), table.getCTTbl().getTblGrid().getGridColArray(1).getW());
            assertEquals(BigInteger.valueOf(3000), table.getCTTbl().getTblGrid().getGridColArray(3).getW());

            assertEquals(BigInteger.valueOf(4), table.getCTTbl().getTblPr().getTblBorders().getTop().getSz());
            assertEquals(BigInteger.valueOf(4), table.getCTTbl().getTblPr().getTblBorders().getInsideV().getSz());
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

    private static int indexContaining(List<String> texts, String needle) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
