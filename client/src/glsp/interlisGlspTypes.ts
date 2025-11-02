export const InterlisGlspTypes = {
  diagramType: "interlis-class-diagram",
  topicNodeType: "node:interlis-topic",
  classNodeType: "node:interlis-class",
  structureNodeType: "node:interlis-structure",
  viewNodeType: "node:interlis-view",
  enumerationNodeType: "node:interlis-enumeration",
  topicLabelType: "label:interlis-topic",
  stereotypeLabelType: "label:interlis-stereotype",
  nameLabelType: "label:interlis-name",
  attributeCompartmentType: "comp:interlis-attributes",
  constraintCompartmentType: "comp:interlis-constraints",
  attributeLabelType: "label:interlis-attribute",
  constraintLabelType: "label:interlis-constraint"
} as const;

export type InterlisGlspTypeKey = keyof typeof InterlisGlspTypes;
