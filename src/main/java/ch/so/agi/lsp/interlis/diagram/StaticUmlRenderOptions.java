package ch.so.agi.lsp.interlis.diagram;

import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Node;
import java.util.Collection;

/**
 * Shared render options for all UML diagram outputs.
 */
public final class StaticUmlRenderOptions {
    public static final String MUTED_ABSTRACT_FILL_COLOR = "#F3F3F3";
    public static final String MUTED_ABSTRACT_BORDER_COLOR = "#D6D6D6";
    public static final String MUTED_ABSTRACT_TEXT_COLOR = "#A6A6A6";

    private final UmlAttributeMode attributeMode;
    private final boolean deemphasizeAbstractTypes;
    private final boolean showAssociationNames;
    private final boolean showRoleCardinalities;
    private final boolean showLocalEnumerationValues;

    public StaticUmlRenderOptions(UmlAttributeMode attributeMode, boolean deemphasizeAbstractTypes) {
        this(attributeMode, deemphasizeAbstractTypes, true, true);
    }

    public StaticUmlRenderOptions(UmlAttributeMode attributeMode, boolean deemphasizeAbstractTypes,
            boolean showAssociationNames, boolean showRoleCardinalities) {
        this(attributeMode, deemphasizeAbstractTypes, showAssociationNames, showRoleCardinalities, true);
    }

    public StaticUmlRenderOptions(UmlAttributeMode attributeMode, boolean deemphasizeAbstractTypes,
            boolean showAssociationNames, boolean showRoleCardinalities, boolean showLocalEnumerationValues) {
        this.attributeMode = attributeMode != null ? attributeMode : UmlAttributeMode.OWN;
        this.deemphasizeAbstractTypes = deemphasizeAbstractTypes;
        this.showAssociationNames = showAssociationNames;
        this.showRoleCardinalities = showRoleCardinalities;
        this.showLocalEnumerationValues = showLocalEnumerationValues;
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

    public boolean isShowAssociationNames() {
        return showAssociationNames;
    }

    public boolean isShowRoleCardinalities() {
        return showRoleCardinalities;
    }

    public boolean isShowLocalEnumerationValues() {
        return showLocalEnumerationValues;
    }

    static boolean isMutedAbstractType(Node node, StaticUmlRenderOptions options) {
        return node != null && isMutedAbstractType(node.stereotypes, options);
    }

    public static boolean isMutedAbstractType(Collection<String> stereotypes, boolean deemphasizeAbstractTypes) {
        if (stereotypes == null || !deemphasizeAbstractTypes) {
            return false;
        }
        if (!stereotypes.contains("Abstract")) {
            return false;
        }
        if (stereotypes.contains("Structure")) {
            return true;
        }
        return !stereotypes.contains("Enumeration")
                && !stereotypes.contains("View")
                && !stereotypes.contains("Function")
                && !stereotypes.contains("External");
    }

    private static boolean isMutedAbstractType(Collection<String> stereotypes, StaticUmlRenderOptions options) {
        return options != null && isMutedAbstractType(stereotypes, options.isDeemphasizeAbstractTypes());
    }
}
