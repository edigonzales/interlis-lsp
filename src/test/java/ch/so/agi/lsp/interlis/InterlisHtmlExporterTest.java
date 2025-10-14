package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisHtmlExporterTest {

    @Test
    void createsStandaloneHtmlWithModelContent(@TempDir Path tempDir) throws Exception {
        Path iliFile = Files.createTempFile(tempDir, "DocTest", ".ili");
        Files.writeString(iliFile, String.join("\n",
                "INTERLIS 2.3;",
                "MODEL DocTest (en)",
                "!!@ title=\"Model Title\"",
                "!!@ shortDescription=\"Model short description\"",
                "AT \"http://example.com/DocTest.ili\"",
                "VERSION \"2024-01-01\" =",
                "  TOPIC DocTopic =",
                "    DOMAIN RoofColor = (rot (hell, dunkel), blau);",
                "    DOMAIN AllRoofColors = ALL OF RoofColor;",
                "    STRUCTURE Address =",
                "      Street : MANDATORY TEXT*50;",
                "    END Address;",
                "    CLASS Building =",
                "      Name : MANDATORY TEXT*50;",
                "      Address : Address;",
                "      RoofColor : RoofColor;",
                "      Status : (geplant, gebaut, abgerissen);",
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

        Topic topic = findTopic(model, "DocTopic");
        topic.setDocumentation("Topic documentation");

        Table structure = findTable(topic, "Address");
        structure.setDocumentation("Structure documentation");
        try {
            structure.setAbstract(true);
        } catch (java.beans.PropertyVetoException e) {
            throw new IllegalStateException("Unable to mark structure abstract", e);
        }

        Table clazz = findTable(topic, "Building");
        clazz.setDocumentation("Class documentation");

        AttributeDef statusAttribute = findAttribute(clazz, "Status");
        EnumerationType inlineEnum = (EnumerationType) statusAttribute.getDomain();
        inlineEnum.getEnumeration().getElement(0).setDocumentation("Geplant doc");

        Domain enumDomain = findDomain(topic, "RoofColor");
        enumDomain.setDocumentation("Roof color documentation");

        Domain allRoofColors = findDomain(topic, "AllRoofColors");
        allRoofColors.setDocumentation("All roof colors documentation");
        EnumerationType enumerationType = (EnumerationType) enumDomain.getType();
        Enumeration enumeration = enumerationType.getEnumeration();
        Enumeration.Element rot = enumeration.getElement(0);
        rot.setDocumentation("Rot doc");
        Enumeration subRot = rot.getSubEnumeration();
        subRot.getElement(0).setDocumentation("Hell doc");
        subRot.getElement(1).setDocumentation("Dunkel doc");
        enumeration.getElement(1).setDocumentation("Blau doc");

        Viewable view = addSyntheticView(topic, "BuildingView");
        view.setDocumentation("View documentation");

        String html = InterlisHtmlExporter.renderHtml(td, "Document Title");
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<title>Document Title</title>"));
        assertTrue(html.contains("<h1 class=\"doc-title\">Document Title</h1>"));

        assertTrue(html.contains("<span class=\"heading-number\">1</span><span class=\"heading-text\">DocTest</span>"));
        assertTrue(html.contains("<p class=\"metadata\"><strong>Titel:</strong> Model Title</p>"));
        assertTrue(html.contains("<span class=\"heading-number\">2</span><span class=\"heading-text\">DocTopic</span>"));
        assertTrue(html.contains("<span class=\"heading-number\">2.1</span><span class=\"heading-text\">Address (Abstract Structure)</span>"));
        assertTrue(html.contains("<span class=\"heading-number\">2.2</span><span class=\"heading-text\">Building (Class)</span>"));
        assertTrue(html.contains("<span class=\"heading-number\">2.3</span><span class=\"heading-text\">BuildingView (View)</span>"));
        assertTrue(html.contains("<span class=\"heading-number\">2.4</span><span class=\"heading-text\">AllRoofColors (Enumeration)</span>"));
        assertTrue(html.contains("<span class=\"heading-number\">2.5</span><span class=\"heading-text\">RoofColor (Enumeration)</span>"));
        assertTrue(html.contains("Topic documentation"));
        assertTrue(html.contains("Structure documentation"));
        assertTrue(html.contains("Class documentation"));
        assertTrue(html.contains("View documentation"));
        assertTrue(html.contains("Roof color documentation"));
        assertTrue(html.contains("All roof colors documentation"));

        assertTrue(html.contains("<th>Attributname</th><th>Kardinalität</th><th>Typ</th><th>Beschreibung</th>"));
        assertTrue(html.contains("<td>Status</td><td>0..1</td><td>Enumeration</td><td>geplant, gebaut, abgerissen</td>"));

        assertTrue(html.contains("<th>Wert</th><th>Beschreibung</th>"));
        int enumerationTableCount = html.split("<th>Wert</th><th>Beschreibung</th>").length - 1;
        assertEquals(2, enumerationTableCount, "Expected enumeration tables for both domains");

        String allRoofColorsTable = tableHtmlForHeading(html, "AllRoofColors (Enumeration)");
        assertTrue(allRoofColorsTable.contains("<td>rot</td><td>Rot doc</td>"));
        assertTrue(allRoofColorsTable.contains("<td>rot.hell</td><td>Hell doc</td>"));
        assertTrue(allRoofColorsTable.contains("<td>rot.dunkel</td><td>Dunkel doc</td>"));
        assertTrue(allRoofColorsTable.contains("<td>blau</td><td>Blau doc</td>"));

        String roofColorTable = tableHtmlForHeading(html, "RoofColor (Enumeration)");
        assertFalse(roofColorTable.contains("<td>rot</td>"),
                "EnumerationType table should not contain intermediate values");
        assertTrue(roofColorTable.contains("<td>rot.hell</td><td>Hell doc</td>"));
        assertTrue(roofColorTable.contains("<td>rot.dunkel</td><td>Dunkel doc</td>"));
        assertTrue(roofColorTable.contains("<td>blau</td><td>Blau doc</td>"));

        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<link"));
    }

    private static Topic findTopic(Model model, String name) {
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof Topic topic && name.equals(topic.getName())) {
                return topic;
            }
        }
        throw new IllegalStateException("Topic not found: " + name);
    }

    private static Table findTable(Topic topic, String name) {
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof Table table && name.equals(table.getName())) {
                return table;
            }
        }
        throw new IllegalStateException("Table not found: " + name);
    }

    private static Domain findDomain(Topic topic, String name) {
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof Domain domain && name.equals(domain.getName())) {
                return domain;
            }
        }
        throw new IllegalStateException("Domain not found: " + name);
    }

    private static AttributeDef findAttribute(Table table, String name) {
        for (Iterator<?> it = table.iterator(); it.hasNext();) {
            Object next = it.next();
            if (next instanceof AttributeDef attribute && name.equals(attribute.getName())) {
                return attribute;
            }
        }
        throw new IllegalStateException("Attribute not found: " + name);
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

    private static String tableHtmlForHeading(String html, String headingText) {
        String marker = "<span class=\"heading-text\">" + headingText + "</span>";
        int headingIndex = html.indexOf(marker);
        assertTrue(headingIndex >= 0, "Heading not found: " + headingText);
        int tableStart = html.indexOf("<table", headingIndex);
        assertTrue(tableStart >= 0, "Table not found after heading: " + headingText);
        int tableEnd = html.indexOf("</table>", tableStart);
        assertTrue(tableEnd >= 0, "Table not closed for heading: " + headingText);
        return html.substring(tableStart, tableEnd + "</table>".length());
    }
}
