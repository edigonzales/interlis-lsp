package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.*;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.export.docx.IliDocxRenderer;
import ch.so.agi.lsp.interlis.export.docx.InterlisDocxExporter;
import ch.so.agi.lsp.interlis.export.html.InterlisHtmlExporter;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HtmlDocumentationParityTest {
    private TransferDescription model() {
        var outcome = Ili2cUtil.compile(new ClientSettings(), Path.of("src/test/data/HtmlExportParity.ili").toAbsolutePath().toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());
        return outcome.getTransferDescription();
    }

    private static Topic topic(TransferDescription td) {
        return IliDocxRenderer.getElements(td.getModelsFromLastFile()[0], Topic.class).get(0);
    }

    private static String section(String html, String name) {
        String marker = "<span class=\"heading-text\">" + name + "</span>";
        int start = html.indexOf(marker);
        assertTrue(start >= 0, name);
        int end = html.indexOf(" class=\"heading level-", start);
        return html.substring(start, end < 0 ? html.length() : end);
    }

    @Test
    void rendersTopicsStructuresAndUniqueDefinitionsWithWordSemantics() throws Exception {
        var td = model();
        String html = InterlisHtmlExporter.renderHtml(td, "HTML parity");
        assertTrue(html.contains("Documentation (Topic)</span>"));
        String building = section(html, "Building (Class)");
        assertTrue(building.contains("<h4 class=\"subheading\">UNIQUE</h4>"));
        assertTrue(building.contains("<th>Nr.</th><th>UNIQUE-Definition</th><th>Herkunft</th>"));
        for (String definition : List.of("UNIQUE (LOCAL) Address : Street;", "UNIQUE (GLOBAL) Name;",
                "UNIQUE (GLOBAL) Name, Score;", "UNIQUE (GLOBAL) Name, Score WHERE Score &lt;= 5;",
                "UNIQUE (GLOBAL) Name WHERE Name == &quot;A&amp;B&quot;;")) {
            assertTrue(building.contains(definition), definition + "\n" + building);
        }
        assertTrue(building.indexOf("class=\"attributes\"") < building.indexOf("class=\"uniqueness\""));
        assertFalse(building.contains("Score <= 5"));
        String structure = section(html, "AddressList (Structure)");
        assertTrue(structure.contains("<td>U1</td><td>UNIQUE (LOCAL) Addresses : Street;</td><td>direkt</td>"));
        assertFalse(section(html, "Address (Structure)").contains("<h4"));
        assertFalse(section(html, "NoUnique (Class)").contains("class=\"uniqueness\""));
        String child = section(html, "UniqueChild (Class)");
        assertTrue(child.contains("<td>U1</td><td>UNIQUE (GLOBAL) BaseCode;</td><td>geerbt von HtmlParity.Documentation.UniqueBase</td>"));
        assertTrue(child.contains("<td>U2</td><td>UNIQUE (GLOBAL) ChildCode;</td><td>direkt</td>"));

        // The HTML rows must contain the exact definitions/origins produced by the actual Word exporter.
        try (var word = new XWPFDocument(new ByteArrayInputStream(InterlisDocxExporter.renderDocx(td, "HTML parity")))) {
            int tables = 0;
            for (var table : word.getTables()) {
                if (table.getRow(0).getTableCells().size() != 3
                        || !table.getRow(0).getCell(1).getText().equals("UNIQUE-Definition")) continue;
                tables++;
                for (int i = 1; i < table.getRows().size(); i++) {
                    var row = table.getRow(i);
                    String expected = "<tr><td>" + escape(row.getCell(0).getText()) + "</td><td>"
                            + escape(row.getCell(1).getText()) + "</td><td>" + escape(row.getCell(2).getText()) + "</td></tr>";
                    assertTrue(html.contains(expected), expected);
                }
            }
            assertEquals(5, tables);
        }
    }

    @Test
    void rendersAssociationRolesOptionalAttributesAndUniqueInOrderWithEscapedDocumentation() {
        var td = model();
        var association = IliDocxRenderer.getElements(topic(td), AssociationDef.class).stream()
                .filter(item -> item.getName().equals("BuildingLink")).findFirst().orElseThrow();
        association.setDocumentation("Association <script> & text");
        association.getRoles().get(0).setDocumentation("Role <tag> & \"quoted\"");
        String html = InterlisHtmlExporter.renderHtml(td, "HTML parity");
        String section = section(html, "BuildingLink (Association)");
        assertTrue(section.contains("Association &lt;script&gt; &amp; text"));
        assertTrue(section.contains("<th>Rollenname</th><th>Kardinalität</th><th>Typ</th><th>Beschreibung</th>"));
        assertTrue(section.contains("<td>Left</td><td>0..*</td><td>Building</td>"));
        assertTrue(section.contains("<td>Right</td><td>1</td><td>Building</td>"));
        assertTrue(section.contains("Role &lt;tag&gt; &amp; &quot;quoted&quot;"));
        assertTrue(section.contains("LinkCode"));
        assertTrue(section.contains("UNIQUE (GLOBAL) Left-&gt;Name, Right-&gt;Score;"));
        assertTrue(section.indexOf("class=\"roles\"") < section.indexOf("class=\"attributes\""));
        assertTrue(section.indexOf("class=\"attributes\"") < section.indexOf("class=\"uniqueness\""));
        String plain = section(html, "PlainLink (Association)");
        assertTrue(plain.contains("class=\"roles\""));
        assertFalse(plain.contains("class=\"attributes\""));
        assertFalse(plain.contains("<h4"));
        String building = section(html, "Building (Class)");
        assertTrue(building.contains("<td>Left</td>"));
        assertTrue(building.contains("<td>Right</td>"));
        assertFalse(html.contains("<script>"));
    }

    @Test
    void keepsSequentialHeadingNumbersAndDoesNotNumberUniqueSubsections() {
        var td = model();
        String html = InterlisHtmlExporter.renderHtml(td, "HTML parity");
        assertTrue(html.indexOf("AddressList (Structure)") < html.indexOf("Documentation (Topic)"));
        assertTrue(html.indexOf("BuildingLink (Association)") > html.indexOf("Documentation (Topic)"));
        assertTrue(section(html, "BuildingLink (Association)").contains("UNIQUE (GLOBAL) Left-&gt;Name, Right-&gt;Score;"));
        var headings = Pattern.compile("heading-number\\\">([^<]+)").matcher(html);
        int chapter = 0, subsection = 0;
        while (headings.find()) {
            String number = headings.group(1);
            if (!number.contains(".")) {
                assertEquals(Integer.toString(++chapter), number);
                subsection = 0;
            } else assertEquals(chapter + "." + (++subsection), number);
        }
        assertEquals(2, chapter);
        assertEquals(6, subsection);
    }

    @Test
    void includesBasketScopeFromTheSharedWordFormatter() {
        var td = model();
        var base = IliDocxRenderer.getElements(topic(td), Table.class).stream()
                .filter(table -> table.getName().equals("UniqueBase")).findFirst().orElseThrow();
        IliDocxRenderer.getElements(base, UniquenessConstraint.class).get(0).setPerBasket(true);
        String html = InterlisHtmlExporter.renderHtml(td, "Basket uniqueness");
        assertTrue(section(html, "UniqueBase (Class)").contains("UNIQUE (GLOBAL, per Basket) BaseCode;"));
        assertTrue(section(html, "UniqueChild (Class)").contains("UNIQUE (GLOBAL, per Basket) BaseCode;"));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
