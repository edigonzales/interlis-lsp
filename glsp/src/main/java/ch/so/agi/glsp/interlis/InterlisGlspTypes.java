package ch.so.agi.glsp.interlis;

/**
 * Centralizes GLSP diagram type and element identifiers used by the INTERLIS diagram server.
 */
public final class InterlisGlspTypes {
    public static final String DIAGRAM_TYPE = "interlis-class-diagram";
    public static final String GRAPH_ID = "interlis-class-graph";

    public static final String CLASS_NODE_TYPE = "node:interlis-class";
    public static final String STRUCTURE_NODE_TYPE = "node:interlis-structure";
    public static final String VIEW_NODE_TYPE = "node:interlis-view";
    public static final String ENUMERATION_NODE_TYPE = "node:interlis-enumeration";

    public static final String HEADER_COMPARTMENT_TYPE = "comp:interlis-header";
    public static final String ATTRIBUTE_COMPARTMENT_TYPE = "comp:interlis-attributes";
    public static final String CONSTRAINT_COMPARTMENT_TYPE = "comp:interlis-constraints";

    public static final String NAMESPACE_LABEL_TYPE = "label:interlis-namespace";
    public static final String STEREOTYPE_LABEL_TYPE = "label:interlis-stereotype";
    public static final String NAME_LABEL_TYPE = "label:interlis-name";
    public static final String ATTRIBUTE_LABEL_TYPE = "label:interlis-attribute";
    public static final String CONSTRAINT_LABEL_TYPE = "label:interlis-constraint";
    public static final String ASSOCIATION_LABEL_TYPE = "label:interlis-association";

    public static final String INHERITANCE_EDGE_TYPE = "edge:interlis-inheritance";
    public static final String ASSOCIATION_EDGE_TYPE = "edge:interlis-association";

    public static final String CSS_CLASS_NODE = "interlis-node";
    public static final String CSS_CLASS_CLASS = "interlis-class";
    public static final String CSS_CLASS_STRUCTURE = "interlis-structure";
    public static final String CSS_CLASS_VIEW = "interlis-view";
    public static final String CSS_CLASS_ENUMERATION = "interlis-enumeration";
    public static final String CSS_CLASS_NAMESPACE = "interlis-namespace";
    public static final String CSS_CLASS_STEREOTYPE = "interlis-stereotype";
    public static final String CSS_CLASS_NAME = "interlis-name";
    public static final String CSS_CLASS_ATTRIBUTE = "interlis-attribute";
    public static final String CSS_CLASS_CONSTRAINT = "interlis-constraint";
    public static final String CSS_CLASS_ENUM_LITERAL = "interlis-enum-literal";
    public static final String CSS_CLASS_COMPARTMENT = "interlis-compartment";
    public static final String CSS_CLASS_HEADER = "interlis-header";
    public static final String CSS_CLASS_ATTRIBUTE_COMPARTMENT = "interlis-attribute-compartment";
    public static final String CSS_CLASS_CONSTRAINT_COMPARTMENT = "interlis-constraint-compartment";
    public static final String CSS_CLASS_ASSOCIATION_LABEL = "interlis-association-label";
    public static final String CSS_CLASS_INHERITANCE_EDGE = "interlis-inheritance-edge";
    public static final String CSS_CLASS_ASSOCIATION_EDGE = "interlis-association-edge";

    private InterlisGlspTypes() {
    }
}
