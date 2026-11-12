package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.export.html.MermaidHtmlRenderer;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MermaidHtmlRendererTest {

    @Test
    void injectsDiagramIntoTemplate() {
        String html = MermaidHtmlRenderer.render("classDiagram\nclass Foo");
        assertTrue(html.contains("classDiagram"));
        assertFalse(html.contains("${mermaidString}"));
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("diagram-pane"));
        assertTrue(html.contains("Mermaid source"));
        assertTrue(html.contains("hideEmptyMembersBox: false"));
        assertFalse(html.contains("${hideEmptyMembersBox}"));
    }

    @Test
    void escapesHtmlCharacters() {
        String html = MermaidHtmlRenderer.render("A < B & C");
        assertTrue(html.contains("A &lt; B &amp; C"));
    }

    @Test
    void enablesCompactBoxesOnlyWhenRequestedWithoutChangingSource() {
        String source = "classDiagram\nclass Foo[\"${hideEmptyMembersBox} < & >\"]";
        String compact = MermaidHtmlRenderer.render(source, true);
        assertTrue(compact.contains("hideEmptyMembersBox: true"));
        assertTrue(compact.contains("class Foo[\"${hideEmptyMembersBox} &lt; &amp; &gt;\"]"));
        String regular = MermaidHtmlRenderer.render(source, false);
        assertTrue(regular.contains("hideEmptyMembersBox: false"));
        assertFalse(regular.contains("hideEmptyMembersBox: true"));
    }
}
