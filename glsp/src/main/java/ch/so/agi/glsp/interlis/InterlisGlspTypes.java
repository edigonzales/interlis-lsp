package ch.so.agi.glsp.interlis;

/**
 * Centralizes GLSP diagram type and element identifiers used by the INTERLIS diagram server.
 */
public final class InterlisGlspTypes {
    public static final String DIAGRAM_TYPE = "interlis-class-diagram";
    public static final String GRAPH_ID = "interlis-class-graph";

    public static final String TOPIC_NODE_TYPE = "node:interlis-topic";
    public static final String CLASS_NODE_TYPE = "node:interlis-class";
    public static final String STRUCTURE_NODE_TYPE = "node:interlis-structure";
    public static final String VIEW_NODE_TYPE = "node:interlis-view";
    public static final String ENUMERATION_NODE_TYPE = "node:interlis-enumeration";

    public static final String TOPIC_LABEL_TYPE = "label:interlis-topic";
    public static final String STEREOTYPE_LABEL_TYPE = "label:interlis-stereotype";
    public static final String NAME_LABEL_TYPE = "label:interlis-name";
    public static final String ATTRIBUTE_LABEL_TYPE = "label:interlis-attribute";
    public static final String CONSTRAINT_LABEL_TYPE = "label:interlis-constraint";

    public static final String ATTRIBUTE_COMPARTMENT_TYPE = "comp:interlis-attributes";
    public static final String CONSTRAINT_COMPARTMENT_TYPE = "comp:interlis-constraints";

    public static final String CSS_CLASS_TOPIC = "interlis-topic";
    public static final String CSS_CLASS_TOPIC_LABEL = "interlis-topic-label";
    public static final String CSS_CLASS_CLASS = "interlis-class";
    public static final String CSS_CLASS_STRUCTURE = "interlis-structure";
    public static final String CSS_CLASS_VIEW = "interlis-view";
    public static final String CSS_CLASS_ENUMERATION = "interlis-enumeration";
    public static final String CSS_CLASS_STEREOTYPE = "interlis-stereotype";
    public static final String CSS_CLASS_NAME = "interlis-name";
    public static final String CSS_CLASS_ATTRIBUTE_COMPARTMENT = "interlis-attributes";
    public static final String CSS_CLASS_CONSTRAINT_COMPARTMENT = "interlis-constraints";
    public static final String CSS_CLASS_ATTRIBUTE_LABEL = "interlis-attribute-label";
    public static final String CSS_CLASS_CONSTRAINT_LABEL = "interlis-constraint-label";

    private InterlisGlspTypes() {
    }
}
