package ch.so.agi.lsp.interlis.diagram;

import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Node;

/**
 * Shared render options for static UML outputs.
 */
public final class StaticUmlRenderOptions {
    public static final String MUTED_ABSTRACT_FILL_COLOR = "#F3F3F3";
    public static final String MUTED_ABSTRACT_BORDER_COLOR = "#D6D6D6";
    public static final String MUTED_ABSTRACT_TEXT_COLOR = "#A6A6A6";

    private final UmlAttributeMode attributeMode;
    private final boolean deemphasizeAbstractTypes;

    public StaticUmlRenderOptions(UmlAttributeMode attributeMode, boolean deemphasizeAbstractTypes) {
        this.attributeMode = attributeMode != null ? attributeMode : UmlAttributeMode.OWN;
        this.deemphasizeAbstractTypes = deemphasizeAbstractTypes;
    }

    public static StaticUmlRenderOptions defaults() {
        return new StaticUmlRenderOptions(UmlAttributeMode.OWN, true);
    }

    public static StaticUmlRenderOptions withAttributeMode(UmlAttributeMode attributeMode) {
        return new StaticUmlRenderOptions(attributeMode, true);
    }

    public UmlAttributeMode getAttributeMode() {
        return attributeMode;
    }

    public boolean isDeemphasizeAbstractTypes() {
        return deemphasizeAbstractTypes;
    }

    static boolean isMutedAbstractType(Node node, StaticUmlRenderOptions options) {
        if (node == null || options == null || !options.isDeemphasizeAbstractTypes()) {
            return false;
        }
        if (!node.stereotypes.contains("Abstract")) {
            return false;
        }
        if (node.stereotypes.contains("Structure")) {
            return true;
        }
        return !node.stereotypes.contains("Enumeration")
                && !node.stereotypes.contains("View")
                && !node.stereotypes.contains("Function")
                && !node.stereotypes.contains("External");
    }
}
