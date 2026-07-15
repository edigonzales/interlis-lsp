package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ch.so.agi.lsp.interlis.diagram.UmlAttributeMode;
import ch.so.agi.lsp.interlis.server.ClientSettings;

class ClientSettingsTest {
    @Test
    void fromParsesNestedAndUmlSettings() {
        Map<String, Object> payload = Map.of(
                "interlisLsp", Map.of(
                        "modelRepositories", "%ILI_DIR,https://models.interlis.ch",
                        "diagram", Map.of(
                                "layout", Map.of("edgeRouting", "POLYLINE")),
                        "uml", Map.of(
                                "attributeMode", "OWN_AND_INHERITED",
                                "deemphasizeAbstractTypes", false,
                                "showAssociationNames", false,
                                "showRoleCardinalities", false,
                                "showLocalEnumerationValues", false)));

        ClientSettings settings = ClientSettings.from(payload);

        assertEquals("%ILI_DIR,https://models.interlis.ch", settings.getModelRepositories());
        assertEquals("POLYLINE", settings.getEdgeRouting());
        assertEquals(UmlAttributeMode.OWN_AND_INHERITED, settings.getUmlAttributeMode());
        assertFalse(settings.isUmlDeemphasizeAbstractTypes());
        assertFalse(settings.isUmlShowAssociationNames());
        assertFalse(settings.isUmlShowRoleCardinalities());
        assertFalse(settings.isUmlShowLocalEnumerationValues());
    }

    @Test
    void fromParsesFlattenedDiagramAndUmlSettings() {
        Map<String, Object> payload = Map.of(
                "interlisLsp", Map.of(
                        "diagram.layout.edgeRouting", "SPLINES",
                        "uml.attributeMode", "NONE",
                        "uml.deemphasizeAbstractTypes", "false",
                        "uml.showAssociationNames", "false",
                        "uml.showRoleCardinalities", "false",
                        "uml.showLocalEnumerationValues", "false"));

        ClientSettings settings = ClientSettings.from(payload);

        assertEquals("SPLINES", settings.getEdgeRouting());
        assertEquals(UmlAttributeMode.NONE, settings.getUmlAttributeMode());
        assertFalse(settings.isUmlDeemphasizeAbstractTypes());
        assertFalse(settings.isUmlShowAssociationNames());
        assertFalse(settings.isUmlShowRoleCardinalities());
        assertFalse(settings.isUmlShowLocalEnumerationValues());
    }

    @Test
    void fromKeepsDefaultsWhenSettingsMissing() {
        ClientSettings settings = ClientSettings.from(Map.of("interlisLsp", Map.of()));

        assertEquals("", settings.getEdgeRouting());
        assertEquals(UmlAttributeMode.OWN, settings.getUmlAttributeMode());
        assertTrue(settings.isUmlDeemphasizeAbstractTypes());
        assertTrue(settings.isUmlShowAssociationNames());
        assertTrue(settings.isUmlShowRoleCardinalities());
        assertTrue(settings.isUmlShowLocalEnumerationValues());
    }

    @Test
    void fromFallsBackToOwnForUnknownAttributeMode() {
        ClientSettings settings = ClientSettings.from(Map.of(
                "interlisLsp", Map.of("uml", Map.of("attributeMode", "custom"))));

        assertEquals(UmlAttributeMode.OWN, settings.getUmlAttributeMode());
        assertTrue(settings.isUmlDeemphasizeAbstractTypes());
    }

    @Test
    void fromKeepsVisibilityDefaultsForInvalidValues() {
        ClientSettings settings = ClientSettings.from(Map.of(
                "interlisLsp", Map.of("uml", Map.of(
                        "showAssociationNames", "maybe",
                        "showRoleCardinalities", "sometimes",
                        "showLocalEnumerationValues", "sometimes"))));

        assertTrue(settings.isUmlShowAssociationNames());
        assertTrue(settings.isUmlShowRoleCardinalities());
        assertTrue(settings.isUmlShowLocalEnumerationValues());
    }

    @Test
    void fromParsesNestedAndFlatJsonValues() {
        ClientSettings nested = ClientSettings.from(JsonParser.parseString(
                "{\"interlisLsp\":{\"uml\":{\"showLocalEnumerationValues\":false}}}"));
        ClientSettings flattened = ClientSettings.from(JsonParser.parseString(
                "{\"interlisLsp.uml.showLocalEnumerationValues\":false}"));

        assertFalse(nested.isUmlShowLocalEnumerationValues());
        assertFalse(flattened.isUmlShowLocalEnumerationValues());
    }
}
