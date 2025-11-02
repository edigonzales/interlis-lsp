import { GCompartment, GCompartmentView, GGraph, GGraphView } from '@eclipse-glsp/client';
import { FeatureModule, GLabel, GLabelView, GNode, RectangularNodeView, configureModelElement } from '@eclipse-glsp/sprotty';
import { InterlisGlspTypes } from './interlisGlspTypes';

export const interlisDiagramModule = new FeatureModule((bind, unbind, isBound, rebind) => {
  const context = { bind, unbind, isBound, rebind };

  configureModelElement(context, InterlisGlspTypes.diagramType, GGraph, GGraphView);
  configureModelElement(context, InterlisGlspTypes.topicNodeType, GNode, RectangularNodeView);
  configureModelElement(context, InterlisGlspTypes.classNodeType, GNode, RectangularNodeView);
  configureModelElement(context, InterlisGlspTypes.structureNodeType, GNode, RectangularNodeView);
  configureModelElement(context, InterlisGlspTypes.viewNodeType, GNode, RectangularNodeView);
  configureModelElement(context, InterlisGlspTypes.enumerationNodeType, GNode, RectangularNodeView);

  configureModelElement(context, InterlisGlspTypes.topicHeaderType, GCompartment, GCompartmentView);
  configureModelElement(context, InterlisGlspTypes.topicContentCompartmentType, GCompartment, GCompartmentView);
  configureModelElement(context, InterlisGlspTypes.headerCompartmentType, GCompartment, GCompartmentView);
  configureModelElement(context, InterlisGlspTypes.attributeCompartmentType, GCompartment, GCompartmentView);
  configureModelElement(context, InterlisGlspTypes.constraintCompartmentType, GCompartment, GCompartmentView);
  configureModelElement(context, InterlisGlspTypes.enumerationLiteralCompartmentType, GCompartment, GCompartmentView);

  configureModelElement(context, InterlisGlspTypes.topicLabelType, GLabel, GLabelView);
  configureModelElement(context, InterlisGlspTypes.nameLabelType, GLabel, GLabelView);
  configureModelElement(context, InterlisGlspTypes.stereotypeLabelType, GLabel, GLabelView);
  configureModelElement(context, InterlisGlspTypes.attributeLabelType, GLabel, GLabelView);
  configureModelElement(context, InterlisGlspTypes.constraintLabelType, GLabel, GLabelView);
  configureModelElement(context, InterlisGlspTypes.enumerationLiteralLabelType, GLabel, GLabelView);
});
