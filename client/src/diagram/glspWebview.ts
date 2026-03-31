import "reflect-metadata";
import "sprotty/css/sprotty.css";
import "@eclipse-glsp/vscode-integration-webview/css/diagram.css";
import "@eclipse-glsp/vscode-integration-webview/css/features.css";
import "@eclipse-glsp/vscode-integration-webview/css/command-palette.css";
import "@eclipse-glsp/vscode-integration-webview/css/tool-palette.css";
import "@eclipse-glsp/vscode-integration-webview/css/decoration.css";
import {
  baseViewModule,
  ContainerConfiguration,
  createDiagramOptionsModule,
  DefaultTypes,
  FeatureModule,
  GEdge,
  GEdgeView,
  initializeDiagramContainer
} from "@eclipse-glsp/client";
import { overrideModelElement, svg } from "@eclipse-glsp/sprotty";
import {
  GLSPDiagramIdentifier,
  GLSPDiagramWidget,
  GLSPStarter,
  WebviewGlspClient
} from "@eclipse-glsp/vscode-integration-webview";
import { Container, ContainerModule, injectable } from "inversify";
import { initializeViewportStateApi, interlisViewportPersistenceModule } from "./viewportPersistence";

const DIAGRAM_LOAD_WARNING_DELAY_MS = 2500;
const MODEL_SWITCH_RETRY_DELAY_MS = 40;
const MODEL_SWITCH_MAX_RETRIES = 10;

type DiagramStatusKind = "warning" | "error";
type DiagramLoadFailureReason = "startup-error" | "timeout" | "load-error";
type DiagramReadyHostMessage = {
  type: "interlis:diagramReady";
  clientId: string;
  sourceUri?: string;
  reason?: string;
};
type DiagramLoadFailedHostMessage = {
  type: "interlis:diagramLoadFailed";
  clientId: string;
  sourceUri?: string;
  message: string;
  detail?: string;
  reason: DiagramLoadFailureReason;
};
type DiagramHostMessage = DiagramReadyHostMessage | DiagramLoadFailedHostMessage;
type DiagramStatusCommand = {
  type: "interlis:diagramStatus";
  clear?: boolean;
  kind?: DiagramStatusKind;
  title?: string;
  detail?: string;
};
type DiagramRecoverCommand = {
  type: "interlis:recoverStartup";
  attempt: number;
  reason?: DiagramLoadFailureReason;
};
type DiagramWebviewCommand = DiagramStatusCommand | DiagramRecoverCommand;
type VsCodeApi = {
  postMessage(message: unknown): void;
  getState?(): unknown;
  setState?(state: unknown): void;
};

let hostBridge: VsCodeApi | undefined;
let activeDiagramWidget: InterlisDiagramWidget | undefined;
let hostCommandHandlersInstalled = false;

function initializeHostBridge(vscode: VsCodeApi | undefined): void {
  hostBridge = vscode;
  initializeViewportStateApi(vscode);
}

function postHostMessage(message: DiagramHostMessage): void {
  if (!hostBridge) {
    console.error("INTERLIS diagram host bridge is unavailable", message);
    return;
  }
  try {
    hostBridge.postMessage(message);
  } catch (error) {
    console.error("INTERLIS diagram host bridge postMessage failed", error, message);
  }
}

function installHostCommandHandlers(): void {
  if (hostCommandHandlersInstalled) {
    return;
  }
  hostCommandHandlersInstalled = true;
  window.addEventListener("message", event => {
    activeDiagramWidget?.handleHostCommand(event?.data);
  });
}

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

@injectable()
class InterlisDiagramWidget extends GLSPDiagramWidget {
  private renderObserver?: MutationObserver;
  private loadWatchdog?: number;
  private loadAttempt = 0;
  private lastReportedReadyAttempt = 0;
  private statusOverlay?: HTMLDivElement;
  private statusTitle?: HTMLDivElement;
  private statusDetail?: HTMLDivElement;
  private statusBanner?: HTMLDivElement;
  private statusBannerTitle?: HTMLDivElement;
  private statusBannerDetail?: HTMLDivElement;
  private globalErrorHandlersInstalled = false;
  private startupPending = false;
  private activeStartupAttempt = 0;
  private hasRenderedOnce = false;

  protected override initializeHtml(): void {
    super.initializeHtml();
    activeDiagramWidget = this;
    installHostCommandHandlers();
    this.ensureStatusOverlay();
    this.ensureStatusBanner();
    this.installRenderObserver();
    this.installGlobalErrorHandlers();
  }

  override async loadDiagram(): Promise<void> {
    const attempt = ++this.loadAttempt;
    this.startupPending = true;
    this.activeStartupAttempt = attempt;
    this.hideStatusOverlay();
    this.armLoadWatchdog(attempt);

    try {
      await super.loadDiagram();
      if (this.loadAttempt !== attempt) {
        return;
      }
      if (this.hasRenderedGraph()) {
        this.handleRenderedGraph(attempt, "loadDiagram-resolved");
      }
    } catch (error: unknown) {
      if (this.loadAttempt !== attempt) {
        return;
      }
      this.clearLoadWatchdog();
      this.reportLoadFailure(
        error,
        "The diagram could not be initialized. Save the file or reopen the diagram.",
        this.shouldShowStartupFailure(attempt),
        "load-error"
      );
    }
  }

  handleHostCommand(command: unknown): void {
    if (!command || typeof command !== "object") {
      return;
    }

    const maybeCommand = command as Partial<DiagramWebviewCommand> & { type?: string };
    if (maybeCommand.type === "interlis:recoverStartup") {
      this.recoverFromHost(maybeCommand as DiagramRecoverCommand);
      return;
    }
    if (maybeCommand.type === "interlis:diagramStatus") {
      this.applyStatusCommand(maybeCommand as DiagramStatusCommand);
    }
  }

  private ensureStatusOverlay(): void {
    if (!this.containerDiv || this.statusOverlay) {
      return;
    }

    const overlay = document.createElement("div");
    overlay.className = "interlis-diagram-status";
    overlay.setAttribute("role", "status");
    overlay.setAttribute("aria-live", "polite");

    const card = document.createElement("div");
    card.className = "interlis-diagram-status-card";

    const title = document.createElement("div");
    title.className = "interlis-diagram-status-title";

    const detail = document.createElement("div");
    detail.className = "interlis-diagram-status-detail";

    card.appendChild(title);
    card.appendChild(detail);
    overlay.appendChild(card);
    this.containerDiv.appendChild(overlay);

    this.statusOverlay = overlay;
    this.statusTitle = title;
    this.statusDetail = detail;
  }

  private ensureStatusBanner(): void {
    if (!this.containerDiv || this.statusBanner) {
      return;
    }

    const banner = document.createElement("div");
    banner.className = "interlis-diagram-banner";
    banner.setAttribute("role", "status");
    banner.setAttribute("aria-live", "polite");

    const title = document.createElement("div");
    title.className = "interlis-diagram-banner-title";

    const detail = document.createElement("div");
    detail.className = "interlis-diagram-banner-detail";

    banner.appendChild(title);
    banner.appendChild(detail);
    this.containerDiv.appendChild(banner);

    this.statusBanner = banner;
    this.statusBannerTitle = title;
    this.statusBannerDetail = detail;
  }

  private installRenderObserver(): void {
    if (!this.containerDiv) {
      return;
    }

    this.renderObserver?.disconnect();
    this.renderObserver = new MutationObserver(() => {
      if (this.hasRenderedGraph()) {
        this.handleRenderedGraph(this.loadAttempt, "rendered-graph");
      }
    });
    this.renderObserver.observe(this.containerDiv, { childList: true, subtree: true });
  }

  private handleRenderedGraph(attempt: number, reason: string): void {
    if (attempt <= 0 || !this.hasRenderedGraph()) {
      return;
    }
    this.clearLoadWatchdog();
    this.hideStatusOverlay();
    this.hasRenderedOnce = true;
    this.activeStartupAttempt = 0;
    this.reportReady(attempt, reason);
  }

  private installGlobalErrorHandlers(): void {
    if (this.globalErrorHandlersInstalled) {
      return;
    }

    this.globalErrorHandlersInstalled = true;
    window.addEventListener("error", event => {
      const attempt = this.activeStartupAttempt;
      if (!this.shouldShowStartupFailure(attempt)) {
        return;
      }
      this.clearLoadWatchdog();
      this.reportLoadFailure(
        event.error ?? event.message,
        "The webview hit a startup error before the diagram was rendered.",
        true,
        "startup-error"
      );
    });
    window.addEventListener("unhandledrejection", event => {
      const attempt = this.activeStartupAttempt;
      if (!this.shouldShowStartupFailure(attempt)) {
        return;
      }
      this.clearLoadWatchdog();
      this.reportLoadFailure(
        event.reason,
        "The webview hit a startup error before the diagram was rendered.",
        true,
        "startup-error"
      );
    });
  }

  private armLoadWatchdog(attempt: number): void {
    this.clearLoadWatchdog();
    this.loadWatchdog = window.setTimeout(() => {
      if (this.loadAttempt !== attempt || this.hasRenderedGraph()) {
        return;
      }

      this.showStatusOverlay(
        "warning",
        "Diagram loading is taking longer than expected",
        "If the view stays blank, save the file or run INTERLIS: Force refresh active diagram."
      );
      this.reportLoadFailure(
        `No rendered graph appeared within ${DIAGRAM_LOAD_WARNING_DELAY_MS}ms.`,
        "Diagram load timed out",
        false,
        "timeout"
      );
      console.warn("DIAGRAM_LOAD delayed", this.clientId);
    }, DIAGRAM_LOAD_WARNING_DELAY_MS);
  }

  private clearLoadWatchdog(): void {
    if (this.loadWatchdog === undefined) {
      return;
    }
    window.clearTimeout(this.loadWatchdog);
    this.loadWatchdog = undefined;
  }

  private hasRenderedGraph(): boolean {
    const graph = this.containerDiv?.querySelector("svg.sprotty-graph");
    return !!graph && graph.childElementCount > 0;
  }

  private showStatusOverlay(kind: DiagramStatusKind, title: string, detail: string): void {
    this.ensureStatusOverlay();
    if (!this.statusOverlay || !this.statusTitle || !this.statusDetail) {
      return;
    }

    this.statusOverlay.dataset.state = kind;
    this.statusOverlay.classList.add("is-visible");
    this.statusTitle.textContent = title;
    this.statusDetail.textContent = detail;
  }

  private hideStatusOverlay(): void {
    if (!this.statusOverlay) {
      return;
    }

    this.statusOverlay.classList.remove("is-visible");
    delete this.statusOverlay.dataset.state;
  }

  private showStatusBanner(kind: DiagramStatusKind, title: string, detail: string): void {
    this.ensureStatusBanner();
    if (!this.statusBanner || !this.statusBannerTitle || !this.statusBannerDetail) {
      return;
    }

    this.statusBanner.dataset.state = kind;
    this.statusBanner.classList.add("is-visible");
    this.statusBannerTitle.textContent = title;
    this.statusBannerDetail.textContent = detail;
  }

  private hideStatusBanner(): void {
    if (!this.statusBanner) {
      return;
    }

    this.statusBanner.classList.remove("is-visible");
    delete this.statusBanner.dataset.state;
  }

  private shouldShowStartupFailure(attempt: number): boolean {
    return attempt > 0
      && this.activeStartupAttempt === attempt
      && this.startupPending
      && !this.hasRenderedGraph()
      && !this.hasRenderedOnce;
  }

  private reportReady(attempt: number, reason: string): void {
    if (attempt <= this.lastReportedReadyAttempt) {
      return;
    }
    this.lastReportedReadyAttempt = attempt;
    this.startupPending = false;
    postHostMessage({
      type: "interlis:diagramReady",
      clientId: this.clientId,
      sourceUri: this.diagramOptions.sourceUri,
      reason
    });
  }

  private reportLoadFailure(
    error: unknown,
    fallback: string,
    showOverlay: boolean,
    reason: DiagramLoadFailureReason
  ): void {
    const message = this.formatErrorMessage(error, fallback);
    const detail = this.formatErrorDetail(error, reason);
    postHostMessage({
      type: "interlis:diagramLoadFailed",
      clientId: this.clientId,
      sourceUri: this.diagramOptions.sourceUri,
      message,
      detail,
      reason
    });
    if (showOverlay) {
      this.showStatusOverlay("error", "Diagram startup failed", message);
    }
    this.startupPending = false;
    this.activeStartupAttempt = 0;
    console.error("DIAGRAM_LOAD failed", error);
  }

  private recoverFromHost(command: DiagramRecoverCommand): void {
    this.clearLoadWatchdog();
    this.hideStatusOverlay();
    const detail = command.reason === "timeout"
      ? "Retrying diagram startup after a timeout."
      : "Retrying diagram startup in the current editor tab.";
    this.showStatusOverlay("warning", "Retrying diagram startup", detail);
    window.setTimeout(() => {
      if (activeDiagramWidget !== this) {
        return;
      }
      void this.loadDiagram();
    }, 0);
  }

  private applyStatusCommand(command: DiagramStatusCommand): void {
    if (command.clear) {
      this.hideStatusBanner();
      return;
    }

    const title = command.title?.trim();
    const detail = command.detail?.trim();
    if (!title || !detail) {
      this.hideStatusBanner();
      return;
    }
    this.showStatusBanner(command.kind ?? "warning", title, detail);
  }

  private formatErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof Error) {
      return error.message || fallback;
    }
    if (typeof error === "string" && error.trim().length > 0) {
      return error.trim();
    }
    return fallback;
  }

  private formatErrorDetail(error: unknown, reason: DiagramLoadFailureReason): string | undefined {
    const prefix = reason === "timeout"
      ? "Timeout while waiting for the first rendered graph."
      : reason === "startup-error"
        ? "Unhandled startup error before the first diagram render."
        : "GLSP loadDiagram failed before the first diagram render.";
    if (error instanceof Error) {
      return `${prefix}\n${error.stack || error.message}`;
    }
    if (typeof error === "string" && error.trim().length > 0) {
      return `${prefix}\n${error.trim()}`;
    }
    try {
      return `${prefix}\n${JSON.stringify(error)}`;
    } catch {
      return prefix;
    }
  }
}

const interlisDiagramWidgetModule = new FeatureModule((bind, unbind, isBound, rebind) => {
  rebind(GLSPDiagramWidget).to(InterlisDiagramWidget).inSingletonScope();
});

class InterlisGlspWebviewStarter extends GLSPStarter {
  constructor() {
    super();
    initializeHostBridge((this.messenger as unknown as { vscode?: VsCodeApi }).vscode);
  }

  protected override acceptDiagramIdentifier(identifier: GLSPDiagramIdentifier): void {
    this.acceptDiagramIdentifierWithRetry(identifier, 0);
  }

  protected override createDiagramOptionsModule(identifier: GLSPDiagramIdentifier): ContainerModule {
    const glspClient = new WebviewGlspClient({ id: identifier.diagramType, messenger: this.messenger });
    return createDiagramOptionsModule(
      {
        clientId: identifier.clientId,
        diagramType: identifier.diagramType,
        glspClientProvider: async () => glspClient,
        sourceUri: decodeURIComponent(identifier.uri)
      },
      {
        needsClientLayout: false,
        needsServerLayout: true
      }
    );
  }

  protected override createContainer(...containerConfiguration: ContainerConfiguration): Container {
    return initializeDiagramContainer(
      new Container(),
      ...containerConfiguration,
      { add: [baseViewModule, interlisEdgeViewModule, interlisDiagramWidgetModule, interlisViewportPersistenceModule] }
    );
  }

  private acceptDiagramIdentifierWithRetry(identifier: GLSPDiagramIdentifier, attempt: number): void {
    if (!this.container) {
      this.initializeContainer(identifier);
      return;
    }

    const currentIdentifier = this.container.get(GLSPDiagramIdentifier);
    const sameDiagram = currentIdentifier.clientId === identifier.clientId
      && currentIdentifier.diagramType === identifier.diagramType
      && currentIdentifier.uri === identifier.uri;

    if (sameDiagram) {
      const diagramWidget = this.container.get(GLSPDiagramWidget);
      void diagramWidget.loadDiagram();
      return;
    }

    const targetContainer = document.getElementById(`${identifier.clientId}_container`);
    if (!targetContainer) {
      if (attempt >= MODEL_SWITCH_MAX_RETRIES) {
        postHostMessage({
          type: "interlis:diagramLoadFailed",
          clientId: identifier.clientId,
          sourceUri: decodeURIComponent(identifier.uri),
          message: "Diagram webview did not reinitialize in time",
          detail: `Missing container ${identifier.clientId}_container after switching diagrams.`,
          reason: "startup-error"
        });
        return;
      }

      window.setTimeout(() => {
        this.acceptDiagramIdentifierWithRetry(identifier, attempt + 1);
      }, MODEL_SWITCH_RETRY_DELAY_MS);
      return;
    }

    document.getElementById(`${currentIdentifier.clientId}_container`)?.replaceChildren();
    document.getElementById(`${currentIdentifier.clientId}_hidden`)?.remove();
    this.container = undefined;
    this.initializeContainer(identifier);
  }

  private initializeContainer(identifier: GLSPDiagramIdentifier): void {
    const diagramModule = this.createDiagramOptionsModule(identifier);
    this.container = this.createContainer(diagramModule, ...this.getContainerConfiguration());
    this.addVscodeBindings?.(this.container, identifier);
    this.container.get(GLSPDiagramWidget);
  }
}

new InterlisGlspWebviewStarter();
