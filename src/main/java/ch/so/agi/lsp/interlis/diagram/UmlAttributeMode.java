package ch.so.agi.lsp.interlis.diagram;

import java.util.Locale;

/**
 * Controls how static UML exports render attributes and enumeration values.
 */
public enum UmlAttributeMode {
    OWN,
    NONE,
    OWN_AND_INHERITED;

    public static UmlAttributeMode from(String value) {
        if (value == null || value.isBlank()) {
            return OWN;
        }
        try {
            return UmlAttributeMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return OWN;
        }
    }
}
