export const InterlisGlspTypes = {
  diagramType: "interlis-class-diagram",
  classNodeType: "node:interlis-class",
  classLabelType: "label:interlis-class",
  cssClassNode: "interlis-class",
  cssClassLabel: "interlis-class-label"
} as const;

export type InterlisGlspTypeKey = keyof typeof InterlisGlspTypes;
