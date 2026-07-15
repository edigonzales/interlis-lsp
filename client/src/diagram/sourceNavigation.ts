import {
  FeatureModule,
  NavigateAction,
  NavigationTargetResolver,
  TYPES,
  bindAsService
} from "@eclipse-glsp/client";
import { Action, GModelElement, MouseListener, NavigationTarget } from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";

export const INTERLIS_SOURCE_DECLARATION_TARGET_TYPE = "interlis.sourceDeclaration";
const JSON_OPENER_OPTIONS = "jsonOpenerOptions";

@injectable()
export class InterlisSourceNavigationTargetResolver extends NavigationTargetResolver {
  override resolve(target: NavigationTarget) {
    if (target.args?.[JSON_OPENER_OPTIONS]) {
      return Promise.resolve(undefined);
    }
    return super.resolve(target);
  }
}

export class InterlisSourceNavigationMouseListener extends MouseListener {
  override doubleClick(target: GModelElement, event: MouseEvent): Action[] {
    if (event.button !== 0) {
      return [];
    }

    const semanticNode = findSemanticNode(target);
    if (!semanticNode) {
      return [];
    }

    return [NavigateAction.create(INTERLIS_SOURCE_DECLARATION_TARGET_TYPE, {
      args: { elementId: semanticNode.id }
    })];
  }
}

function findSemanticNode(target: GModelElement | undefined): GModelElement | undefined {
  let current = target;
  while (current) {
    if (current.cssClasses?.includes("interlis-class")) {
      return current;
    }
    current = current.parent;
  }
  return undefined;
}

export const interlisSourceNavigationModule = new FeatureModule((bind, _unbind, isBound, rebind) => {
  bindAsService(bind, TYPES.MouseListener, InterlisSourceNavigationMouseListener);

  if (isBound(NavigationTargetResolver)) {
    rebind(NavigationTargetResolver).to(InterlisSourceNavigationTargetResolver).inSingletonScope();
  } else {
    bind(NavigationTargetResolver).to(InterlisSourceNavigationTargetResolver).inSingletonScope();
  }
}, { featureId: Symbol("interlisSourceNavigation") });
