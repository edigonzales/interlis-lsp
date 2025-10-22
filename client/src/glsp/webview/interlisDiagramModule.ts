import {
  ContainerModule
} from "inversify";
import {
  GLabel,
  GLabelView,
  RectangularNode,
  RectangularNodeView,
  SGraphImpl,
  SGraphView,
  configureModelElement
} from "@eclipse-glsp/sprotty";

export const INTERLIS_TYPES = {
  GRAPH: "graph",
  CLASS: "interlis-class",
  PLACEHOLDER: "interlis-placeholder",
  LABEL: "label"
} as const;

export const interlisDiagramModule = new ContainerModule((bind, unbind, isBound, rebind) => {
  const context = { bind, unbind, isBound, rebind };
  configureModelElement(context, INTERLIS_TYPES.GRAPH, SGraphImpl, SGraphView);
  configureModelElement(context, INTERLIS_TYPES.LABEL, GLabel, GLabelView);
  configureModelElement(context, INTERLIS_TYPES.CLASS, RectangularNode, RectangularNodeView);
  configureModelElement(context, INTERLIS_TYPES.PLACEHOLDER, RectangularNode, RectangularNodeView);
});
