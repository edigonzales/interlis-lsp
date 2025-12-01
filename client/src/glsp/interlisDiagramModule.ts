import { GGraph, GGraphView } from '@eclipse-glsp/client';
import { FeatureModule, GLabel, GLabelView, GNode, RectangularNodeView, configureModelElement } from '@eclipse-glsp/sprotty';
import { InterlisGlspTypes } from './interlisGlspTypes';

export const interlisDiagramModule = new FeatureModule((bind, unbind, isBound, rebind) => {
  const context = { bind, unbind, isBound, rebind };

  configureModelElement(context, InterlisGlspTypes.diagramType, GGraph, GGraphView);
  configureModelElement(context, InterlisGlspTypes.classNodeType, GNode, RectangularNodeView);
  configureModelElement(context, InterlisGlspTypes.classLabelType, GLabel, GLabelView);
});
