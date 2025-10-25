package ch.so.agi.glsp.interlis;

/**
 * Centralizes GLSP diagram type and element identifiers used by the INTERLIS diagram server.
 */
public final class InterlisGlspTypes {
    public static final String DIAGRAM_TYPE = "interlis-class-diagram";
    public static final String GRAPH_ID = "interlis-class-graph";

    public static final String NODE_TYPE_VIEWABLE = "node:interlis-viewable";
    public static final String COMPARTMENT_TYPE_HEADER = "compartment:interlis-header";
    public static final String COMPARTMENT_TYPE_ATTRIBUTES = "compartment:interlis-attributes";
    public static final String COMPARTMENT_TYPE_CONSTRAINTS = "compartment:interlis-constraints";

    public static final String LABEL_TYPE_STEREOTYPE = "label:interlis-stereotype";
    public static final String LABEL_TYPE_NAME = "label:interlis-name";
    public static final String LABEL_TYPE_ATTRIBUTE = "label:interlis-attribute";
    public static final String LABEL_TYPE_CONSTRAINT = "label:interlis-constraint";

    public static final String CSS_CLASS_NODE = "interlis-node";
    public static final String CSS_CLASS_TOPIC = "interlis-topic";
    public static final String CSS_CLASS_CLASS = "interlis-class";
    public static final String CSS_CLASS_STRUCTURE = "interlis-structure";
    public static final String CSS_CLASS_VIEW = "interlis-view";
    public static final String CSS_CLASS_ENUMERATION = "interlis-enumeration";
    public static final String CSS_CLASS_ABSTRACT = "interlis-abstract";

    public static final String CSS_CLASS_HEADER = "interlis-header";
    public static final String CSS_CLASS_ATTRIBUTES = "interlis-attributes";
    public static final String CSS_CLASS_CONSTRAINTS = "interlis-constraints";

    public static final String CSS_CLASS_STEREOTYPE_LABEL = "interlis-stereotype-label";
    public static final String CSS_CLASS_NAME_LABEL = "interlis-name-label";
    public static final String CSS_CLASS_ATTRIBUTE_LABEL = "interlis-attribute-label";
    public static final String CSS_CLASS_CONSTRAINT_LABEL = "interlis-constraint-label";

    private InterlisGlspTypes() {
    }
}
