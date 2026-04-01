package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.so.agi.lsp.interlis.diagram.UmlAttributeMode;
import ch.so.agi.lsp.interlis.server.ClientSettings;

class ClientSettingsTest {
    @Test
    void fromParsesNestedDiagramSettings() {
        Map<String, Object> payload = Map.of(
                "interlisLsp", Map.of(
                        "modelRepositories", "%ILI_DIR,https://models.interlis.ch",
                        "diagram", Map.of(
                                "layout", Map.of("edgeRouting", "POLYLINE"),
                                "showCardinalities", false),
                        "uml", Map.of("attributeMode", "OWN_AND_INHERITED")));

        ClientSettings settings = ClientSettings.from(payload);

        assertEquals("%ILI_DIR,https://models.interlis.ch", settings.getModelRepositories());
        assertEquals("POLYLINE", settings.getEdgeRouting());
        assertFalse(settings.isShowCardinalities());
        assertEquals(UmlAttributeMode.OWN_AND_INHERITED, settings.getUmlAttributeMode());
    }

    @Test
    void fromParsesFlattenedDiagramSettings() {
        Map<String, Object> payload = Map.of(
                "interlisLsp", Map.of(
                        "diagram.layout.edgeRouting", "SPLINES",
                        "diagram.showCardinalities", "false",
                        "uml.attributeMode", "NONE"));

        ClientSettings settings = ClientSettings.from(payload);

        assertEquals("SPLINES", settings.getEdgeRouting());
        assertFalse(settings.isShowCardinalities());
        assertEquals(UmlAttributeMode.NONE, settings.getUmlAttributeMode());
    }

    @Test
    void fromKeepsDefaultsWhenSettingsMissing() {
        ClientSettings settings = ClientSettings.from(Map.of("interlisLsp", Map.of()));

        assertEquals("", settings.getEdgeRouting());
        assertTrue(settings.isShowCardinalities());
        assertEquals(UmlAttributeMode.OWN, settings.getUmlAttributeMode());
    }

    @Test
    void fromFallsBackToOwnForUnknownAttributeMode() {
        ClientSettings settings = ClientSettings.from(Map.of(
                "interlisLsp", Map.of("uml", Map.of("attributeMode", "custom"))));

        assertEquals(UmlAttributeMode.OWN, settings.getUmlAttributeMode());
    }
}
