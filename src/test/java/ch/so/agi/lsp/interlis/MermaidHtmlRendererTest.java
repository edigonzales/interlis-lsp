package ch.so.agi.lsp.interlis;

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
    }

    @Test
    void escapesHtmlCharacters() {
        String html = MermaidHtmlRenderer.render("A < B & C");
        assertTrue(html.contains("A &lt; B &amp; C"));
    }
}
