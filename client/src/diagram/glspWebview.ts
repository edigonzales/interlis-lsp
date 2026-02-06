import "reflect-metadata";
import {
  baseViewModule,
  ContainerConfiguration,
  DefaultTypes,
  FeatureModule,
  GEdge,
  GEdgeView,
  initializeDiagramContainer
} from "@eclipse-glsp/client";
import { overrideModelElement, svg } from "@eclipse-glsp/sprotty";
import { GLSPStarter } from "@eclipse-glsp/vscode-integration-webview";
import { Container, injectable } from "inversify";

@injectable()
class InterlisEdgeView extends GEdgeView {
  private static readonly INHERITANCE_ARROW_LENGTH = 14;
  private static readonly INHERITANCE_ARROW_WIDTH = 12;

  protected override renderAdditionals(edge: any, segments: any[], context: any): any[] {
    const additionals = [...super.renderAdditionals(edge, segments, context)];
    if (!this.isInheritanceEdge(edge) || !Array.isArray(segments) || segments.length < 2) {
      return additionals;
    }

    const tip = segments[segments.length - 1];
    const prev = segments[segments.length - 2];
    const dx = tip.x - prev.x;
    const dy = tip.y - prev.y;
    const length = Math.hypot(dx, dy);
    if (length < 0.001) {
      return additionals;
    }

    const ux = dx / length;
    const uy = dy / length;
    const baseX = tip.x - ux * InterlisEdgeView.INHERITANCE_ARROW_LENGTH;
    const baseY = tip.y - uy * InterlisEdgeView.INHERITANCE_ARROW_LENGTH;
    const px = -uy;
    const py = ux;
    const halfWidth = InterlisEdgeView.INHERITANCE_ARROW_WIDTH / 2;

    const leftX = baseX + px * halfWidth;
    const leftY = baseY + py * halfWidth;
    const rightX = baseX - px * halfWidth;
    const rightY = baseY - py * halfWidth;

    additionals.push(svg("path", {
      "class-interlis-inheritance-arrow": true,
      d: `M ${tip.x},${tip.y} L ${leftX},${leftY} L ${rightX},${rightY} Z`
    }));

    return additionals;
  }

  private isInheritanceEdge(edge: any): boolean {
    const cssClasses = edge?.cssClasses;
    return Array.isArray(cssClasses) && cssClasses.includes("interlis-edge-inheritance");
  }
}

const interlisEdgeViewModule = new FeatureModule((bind, unbind, isBound, rebind) => {
  overrideModelElement({ bind, isBound }, DefaultTypes.EDGE, GEdge, InterlisEdgeView);
});

class InterlisGlspWebviewStarter extends GLSPStarter {
  protected override createContainer(...containerConfiguration: ContainerConfiguration): Container {
    return initializeDiagramContainer(
      new Container(),
      ...containerConfiguration,
      { add: [baseViewModule, interlisEdgeViewModule] }
    );
  }
}

new InterlisGlspWebviewStarter();
