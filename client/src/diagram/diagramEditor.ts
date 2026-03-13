import * as path from "path";
import * as vscode from "vscode";
import { RequestModelAction, TriggerLayoutAction } from "@eclipse-glsp/protocol";
import { GlspEditorProvider, GlspVscodeConnector, SocketGlspVscodeServer } from "@eclipse-glsp/vscode-integration/node";
import { LanguageClient } from "vscode-languageclient/node";

export const DIAGRAM_EDITOR_VIEW_TYPE = "interlis.diagramEditor";
const GLSP_ENDPOINT_REQUEST = "interlis/glspEndpoint";
const DEFAULT_DIAGRAM_TYPE = "interlis-uml";
const POST_REFRESH_KICK_DELAYS_MS = [0, 60, 180, 420] as const;
const REFRESH_RETRY_DELAYS_MS = [140, 360, 860] as const;

const CFG_AUTO_OPEN_BESIDE = "diagram.autoOpenBeside";

type GetClient = () => LanguageClient | undefined;
type DiagramDebugLogger = ((message: string) => void) | undefined;
type DiagramReadyMessage = {
  type: "interlis:diagramReady";
  clientId?: string;
  sourceUri?: string;
  reason?: string;
};
type DiagramLoadFailedMessage = {
  type: "interlis:diagramLoadFailed";
  clientId?: string;
  sourceUri?: string;
  message?: string;
  detail?: string;
};

type GlspEndpoint = {
  protocol?: string;
  host: string;
  port: number;
  path: string;
  diagramType?: string;
};

let glspServer: SocketGlspVscodeServer | undefined;
let glspConnector: GlspVscodeConnector | undefined;
let registrationPromise: Promise<void> | undefined;
let registeredDiagramType = DEFAULT_DIAGRAM_TYPE;
let lastInterlisSourceUri: vscode.Uri | undefined;
const pendingRefreshRetryTimers = new Map<string, ReturnType<typeof setTimeout>[]>();
const pendingRefreshSources = new Map<string, string>();
const readyDiagramClientIds = new Set<string>();
const knownDiagramClientUris = new Map<string, string>();
const panelMessageSubscriptions = new WeakMap<vscode.WebviewPanel, vscode.Disposable>();
let diagramDebugLogger: DiagramDebugLogger;

export function setDiagramDebugLogger(logger: DiagramDebugLogger): void {
  diagramDebugLogger = logger;
}

class InterlisGlspEditorProvider extends GlspEditorProvider {
  override diagramType: string;

  constructor(
    connector: GlspVscodeConnector,
    private readonly extensionUri: vscode.Uri,
    diagramType: string
  ) {
    super(connector);
    this.diagramType = diagramType;
  }

  override setUpWebview(
    document: vscode.CustomDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken,
    clientId: string
  ): void {
    const webview = webviewPanel.webview;
    markDiagramClientNotReady(clientId, document.uri, "webview-setup");
    ensureDiagramPanelMessageBridge(webviewPanel);

    webview.options = {
      enableScripts: true,
      localResourceRoots: [
        this.extensionUri,
        vscode.Uri.joinPath(this.extensionUri, "dist"),
        vscode.Uri.joinPath(this.extensionUri, "node_modules")
      ]
    };

    const title = path.basename(document.uri.path || document.uri.fsPath || "model.ili");
    webviewPanel.title = `INTERLIS Diagram: ${title}`;
    webviewPanel.webview.html = this.createWebviewHtml(webview, clientId, title);
    webviewPanel.onDidDispose(() => {
      clearDiagramClientState(clientId);
    });

    webviewPanel.onDidChangeViewState(event => {
      if (event.webviewPanel.visible) {
        sendKickLayoutMessageToPanel(event.webviewPanel);
        refreshDiagramByUri(document.uri, true, "panel-visible");
      }
    });
  }

  private createWebviewHtml(webview: vscode.Webview, clientId: string, title: string): string {
    const nonce = createNonce();
    const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, "dist", "glspWebview.js"));

    const stylesheetUris = [
      vscode.Uri.joinPath(this.extensionUri, "dist", "glspWebview.css")
    ].map(uri => webview.asWebviewUri(uri));

    const links = stylesheetUris
      .map(uri => `<link rel="stylesheet" href="${uri}" />`)
      .join("\n");

    return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource} data:; style-src ${webview.cspSource} 'unsafe-inline'; font-src ${webview.cspSource}; script-src 'nonce-${nonce}';" />
  <title>INTERLIS Diagram</title>
  ${links}
  <style>
    html, body {
      margin: 0;
      padding: 0;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background: #ffffff;
      color: #1f2733;
      font-family: "Segoe UI", -apple-system, BlinkMacSystemFont, sans-serif;
    }
    .interlis-shell {
      width: 100%;
      height: 100%;
      display: grid;
      grid-template-rows: auto 1fr;
      background: #ffffff;
    }
    .interlis-header {
      padding: 9px 12px;
      border-bottom: 1px solid #d6deec;
      background: #edf2fb;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.01em;
      color: #263247;
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
    }
    .interlis-diagram {
      width: 100%;
      height: 100%;
      display: flex;
      position: relative;
      overflow: hidden;
      min-height: 0;
      min-width: 0;
      background: #ffffff;
    }
    #${clientId}_container {
      display: flex;
      position: relative;
      overflow: hidden;
      min-width: 0;
      min-height: 0;
    }
    #${clientId}_container > #${clientId} {
      display: flex;
      flex: 1 1 auto;
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
      position: relative;
    }
    #${clientId}_container > #${clientId} > svg.sprotty-graph {
      display: block;
      flex: 1 1 auto;
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }
    #${clientId}_container > #${clientId},
    #${clientId}_container > #${clientId} > svg.sprotty-graph,
    #${clientId}_container .sprotty-graph {
      background: #ffffff !important;
    }
    .interlis-diagram-status {
      position: absolute;
      inset: 16px;
      display: none;
      align-items: center;
      justify-content: center;
      pointer-events: none;
      z-index: 30;
    }
    .interlis-diagram-status.is-visible {
      display: flex;
    }
    .interlis-diagram-status-card {
      max-width: 420px;
      padding: 14px 16px;
      border: 1px solid #d7deeb;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.96);
      box-shadow: 0 12px 28px rgba(35, 50, 79, 0.16);
      color: #243147;
      line-height: 1.45;
    }
    .interlis-diagram-status[data-state="warning"] .interlis-diagram-status-card {
      border-color: #d4b35d;
      background: rgba(255, 249, 232, 0.97);
    }
    .interlis-diagram-status[data-state="error"] .interlis-diagram-status-card {
      border-color: #d16a6a;
      background: rgba(255, 244, 244, 0.97);
    }
    .interlis-diagram-status-title {
      font-size: 13px;
      font-weight: 700;
      color: #243147;
    }
    .interlis-diagram-status[data-state="warning"] .interlis-diagram-status-title {
      color: #755212;
    }
    .interlis-diagram-status[data-state="error"] .interlis-diagram-status-title {
      color: #8a2a2a;
    }
    .interlis-diagram-status-detail {
      margin-top: 6px;
      font-size: 12px;
      color: #425167;
    }
    .interlis-container > path.sprotty-node,
    .interlis-container > rect.sprotty-node {
      fill: #e9f0ff;
      stroke: #6b86ba;
      stroke-width: 1.25;
    }
    .interlis-container-root > path.sprotty-node,
    .interlis-container-root > rect.sprotty-node {
      fill: #f2f4f8;
      stroke: #8d97aa;
      stroke-dasharray: 6 5;
    }
    .interlis-container-title {
      font-size: 13px;
      font-weight: 700;
      fill: #263247;
      pointer-events: none;
    }
    .interlis-class > path.sprotty-node,
    .interlis-class > rect.sprotty-node {
      fill: #ffffff;
      stroke: #708299;
      stroke-width: 1.2;
    }
    .interlis-class-title {
      font-size: 12px;
      font-weight: 700;
      fill: #182234;
      pointer-events: none;
    }
    .interlis-class-stereotype {
      font-size: 10px;
      font-weight: 600;
      fill: #3c5b89;
      pointer-events: none;
    }
    .interlis-class-attribute,
    .interlis-class-method {
      font-size: 10px;
      fill: #2a3a50;
      pointer-events: none;
    }
    text.interlis-container-title,
    text.interlis-class-title,
    text.interlis-class-stereotype,
    text.interlis-class-attribute,
    text.interlis-class-method,
    text.interlis-error-title,
    text.interlis-error-message,
    text.interlis-error-source {
      text-anchor: start;
    }
    .interlis-edge-association > path {
      stroke: #2c7f6d;
      stroke-width: 1.45;
      fill: none;
    }
    .interlis-edge-inheritance > path {
      stroke: #6b58c9;
      stroke-width: 1.55;
      stroke-dasharray: none;
      fill: none;
    }
    .interlis-edge-inheritance > path.interlis-inheritance-arrow {
      fill: none;
      stroke: #6b58c9;
      stroke-width: 1.55;
      stroke-linejoin: miter;
    }
    .interlis-edge-label,
    .interlis-edge-cardinality {
      font-size: 10px;
      font-weight: 600;
      fill: #33465f;
      pointer-events: none;
    }
    .interlis-error > path.sprotty-node,
    .interlis-error > rect.sprotty-node {
      fill: #fff5f5;
      stroke: #d25f5f;
      stroke-width: 1.4;
    }
    .interlis-error-title {
      font-size: 13px;
      font-weight: 700;
      fill: #8d1d1d;
    }
    .interlis-error-message,
    .interlis-error-source {
      font-size: 11px;
      fill: #572727;
    }
  </style>
</head>
<body>
  <div class="interlis-shell">
    <div class="interlis-header">INTERLIS GLSP Diagram: ${escapeHtml(title)}</div>
    <div class="interlis-diagram" id="${clientId}_container"></div>
  </div>
  <script nonce="${nonce}" src="${scriptUri}"></script>
  <script nonce="${nonce}">
    (() => {
      const containerId = "${clientId}_container";
      const kickLayout = () => {
        const container = document.getElementById(containerId);
        if (container) {
          container.classList.add("mouse-enter");
          container.classList.remove("mouse-leave");
        }
        try {
          window.dispatchEvent(new FocusEvent("focus"));
        } catch {
          window.dispatchEvent(new Event("focus"));
        }
        window.dispatchEvent(new Event("resize"));
      };

      [0, 40, 120, 300, 700, 1200].forEach(delayMs => setTimeout(kickLayout, delayMs));
      document.addEventListener("visibilitychange", () => {
        if (!document.hidden) {
          kickLayout();
        }
      });
      window.addEventListener("message", event => {
        if (event?.data?.type === "interlis:kickLayout") {
          [0, 50, 140, 320].forEach(delayMs => setTimeout(kickLayout, delayMs));
        }
      });
    })();
  </script>
</body>
</html>`;
  }
}

export async function registerInterlisDiagramEditor(context: vscode.ExtensionContext, getClient: GetClient): Promise<void> {
  if (registrationPromise) {
    return registrationPromise;
  }

  registrationPromise = (async () => {
    if (glspConnector) {
      return;
    }

    const client = getClient();
    if (!client) {
      throw new Error("Language server is not available.");
    }

    const endpoint = await client.sendRequest<GlspEndpoint>(GLSP_ENDPOINT_REQUEST);
    validateEndpoint(endpoint);

    const diagramType = endpoint.diagramType && endpoint.diagramType.trim().length > 0
      ? endpoint.diagramType.trim()
      : DEFAULT_DIAGRAM_TYPE;
    registeredDiagramType = diagramType;

    glspServer = new SocketGlspVscodeServer({
      clientId: "interlis-vscode",
      clientName: "INTERLIS VSCode",
      connectionOptions: {
        protocol: normalizeProtocol(endpoint.protocol),
        host: endpoint.host,
        port: endpoint.port,
        path: normalizePath(endpoint.path)
      }
    });
    await glspServer.start();

    glspConnector = new GlspVscodeConnector({
      server: glspServer,
      logging: false
    });

    const provider = new InterlisGlspEditorProvider(glspConnector, context.extensionUri, diagramType);

    context.subscriptions.push(glspServer);
    context.subscriptions.push(glspConnector);
    context.subscriptions.push(new vscode.Disposable(() => {
      glspServer = undefined;
      glspConnector = undefined;
      registrationPromise = undefined;
    }));
    context.subscriptions.push(
      vscode.window.registerCustomEditorProvider(
        DIAGRAM_EDITOR_VIEW_TYPE,
        provider,
        {
          supportsMultipleEditorsPerDocument: true,
          webviewOptions: { retainContextWhenHidden: true }
        }
      )
    );
  })().catch(error => {
    registrationPromise = undefined;
    throw error;
  });

  return registrationPromise;
}

export function registerInterlisDiagramCommands(context: vscode.ExtensionContext): void {
  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.diagram.open", async (resource?: unknown) => {
      const sourceUri = resolveInterlisSourceUri(resource);
      if (!sourceUri) {
        vscode.window.showWarningMessage("Open an .ili file first.");
        return;
      }
      lastInterlisSourceUri = sourceUri;
      await openInterlisDiagramBeside(sourceUri, true);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.diagram.refresh", () => {
      if (!glspConnector) {
        vscode.window.showWarningMessage("INTERLIS diagram editor is not ready.");
        return;
      }

      const activeTab = vscode.window.tabGroups.activeTabGroup.activeTab;
      if (!(activeTab?.input instanceof vscode.TabInputCustom)
        || activeTab.input.viewType !== DIAGRAM_EDITOR_VIEW_TYPE) {
        vscode.window.showWarningMessage("Open an INTERLIS diagram editor tab first.");
        return;
      }

      refreshDiagramByUri(activeTab.input.uri, true, "manual-refresh");
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.diagram.autoLayout", () => {
      if (!glspConnector) {
        vscode.window.showWarningMessage("INTERLIS diagram editor is not ready.");
        return;
      }

      const activeTab = vscode.window.tabGroups.activeTabGroup.activeTab;
      if (!(activeTab?.input instanceof vscode.TabInputCustom)
        || activeTab.input.viewType !== DIAGRAM_EDITOR_VIEW_TYPE) {
        vscode.window.showWarningMessage("Open an INTERLIS diagram editor tab first.");
        return;
      }

      glspConnector.dispatchAction(TriggerLayoutAction.create());
    })
  );
}

export async function maybeAutoOpenDiagram(editor: vscode.TextEditor | undefined, openedUris: Set<string>): Promise<void> {
  if (!editor) {
    return;
  }
  const document = editor.document;
  if (!isInterlisFileDocument(document)) {
    return;
  }
  lastInterlisSourceUri = document.uri;
  if (!isDiagramAutoOpenEnabled()) {
    return;
  }

  const key = document.uri.toString();
  if (openedUris.has(key)) {
    return;
  }
  if (isDiagramTabAlreadyOpen(document.uri)) {
    openedUris.add(key);
    return;
  }

  await openInterlisDiagramBeside(document.uri, true);
  openedUris.add(key);
}

export function forgetAutoOpenedDiagram(document: vscode.TextDocument, openedUris: Set<string>): void {
  if (!isInterlisFileDocument(document)) {
    return;
  }
  cancelScheduledDiagramRefresh(document);
  clearPendingRefresh(document.uri);
  clearRefreshRetryTimers(document.uri);
  openedUris.delete(document.uri.toString());
}

export function refreshOpenDiagramByUri(uri: vscode.Uri): boolean {
  return refreshDiagramByUri(uri, true, "compileFinished");
}

export function cancelScheduledDiagramRefresh(document: vscode.TextDocument): void {
  clearPendingRefresh(document.uri);
  clearRefreshRetryTimers(document.uri);
}

async function openInterlisDiagramBeside(uri: vscode.Uri, preserveFocus: boolean): Promise<void> {
  await vscode.commands.executeCommand(
    "vscode.openWith",
    uri,
    DIAGRAM_EDITOR_VIEW_TYPE,
    {
      viewColumn: vscode.ViewColumn.Beside,
      preserveFocus,
      preview: false
    } as vscode.TextDocumentShowOptions
  );
  refreshDiagramByUri(uri, true, "open");
}

function isDiagramTabAlreadyOpen(uri: vscode.Uri): boolean {
  const key = uri.toString();
  for (const group of vscode.window.tabGroups.all) {
    for (const tab of group.tabs) {
      const input = tab.input;
      if (!(input instanceof vscode.TabInputCustom)) {
        continue;
      }
      if (input.viewType === DIAGRAM_EDITOR_VIEW_TYPE && input.uri.toString() === key) {
        return true;
      }
    }
  }
  return false;
}

function isDiagramAutoOpenEnabled(): boolean {
  return vscode.workspace.getConfiguration("interlisLsp").get<boolean>(CFG_AUTO_OPEN_BESIDE) ?? true;
}

function ensureDiagramPanelMessageBridge(panel: vscode.WebviewPanel): void {
  if (panelMessageSubscriptions.has(panel)) {
    return;
  }

  const disposable = panel.webview.onDidReceiveMessage(message => {
    handleDiagramPanelMessage(message);
  });
  panelMessageSubscriptions.set(panel, disposable);
  panel.onDidDispose(() => {
    disposable.dispose();
    panelMessageSubscriptions.delete(panel);
    clearDiagramClientStatesForPanel(panel);
  });
}

function handleDiagramPanelMessage(message: unknown): void {
  if (isDiagramReadyMessage(message)) {
    handleDiagramReadyMessage(message);
    return;
  }
  if (isDiagramLoadFailedMessage(message)) {
    handleDiagramLoadFailedMessage(message);
  }
}

function handleDiagramReadyMessage(message: DiagramReadyMessage): void {
  if (!message.clientId) {
    return;
  }

  const uri = resolveDiagramMessageUri(message.clientId, message.sourceUri);
  if (!uri) {
    logDiagramDebug(`DIAGRAM_CLIENT status=ready clientId=${message.clientId} uri=<unknown>`);
    return;
  }

  readyDiagramClientIds.add(message.clientId);
  knownDiagramClientUris.set(message.clientId, uri.toString());
  const reasonSuffix = message.reason ? ` reason=${message.reason}` : "";
  logDiagramDebug(`DIAGRAM_CLIENT status=ready clientId=${message.clientId}${reasonSuffix} uri=${uri.toString()}`);
  flushPendingRefresh(uri, "ready");
}

function handleDiagramLoadFailedMessage(message: DiagramLoadFailedMessage): void {
  if (message.clientId) {
    readyDiagramClientIds.delete(message.clientId);
  }

  const uri = resolveDiagramMessageUri(message.clientId, message.sourceUri);
  const uriText = uri?.toString() ?? message.sourceUri ?? "<unknown>";
  const summary = message.message?.trim() || "Diagram load failed";
  const detail = message.detail?.trim();
  const detailSuffix = detail ? `\n${detail}` : "";
  logDiagramDebug(
    `DIAGRAM_LOAD failed clientId=${message.clientId ?? "<unknown>"} uri=${uriText} message=${summary}${detailSuffix}`
  );
}

function refreshDiagramByUri(uri: vscode.Uri, allowRetry = true, source = "unknown"): boolean {
  logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=requested uri=${uri.toString()}`);

  if (!glspConnector) {
    logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=skipped reason=no-connector uri=${uri.toString()}`);
    return false;
  }

  const clients = findDiagramClients(uri);
  for (const { clientId, client } of clients) {
    const ready = readyDiagramClientIds.has(clientId);
    logDiagramDebug(`DIAGRAM_CLIENT source=${source} clientId=${clientId} status=${ready ? "ready" : "not-ready"} uri=${uri.toString()}`);
  }
  const readyClients = clients.filter(({ clientId }) => readyDiagramClientIds.has(clientId));
  if (clients.length === 0) {
    if (allowRetry && isDiagramTabAlreadyOpen(uri)) {
      rememberPendingRefresh(uri, source);
      logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=queued reason=no-client uri=${uri.toString()}`);
      scheduleRefreshRetry(uri, source);
    } else {
      clearPendingRefresh(uri);
      logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=skipped reason=no-open-diagram uri=${uri.toString()}`);
    }
    return false;
  }

  if (readyClients.length === 0) {
    rememberPendingRefresh(uri, source);
    if (allowRetry) {
      logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=queued reason=client-not-ready clients=${clients.length} uri=${uri.toString()}`);
      scheduleRefreshRetry(uri, source);
    } else {
      logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=waiting reason=client-not-ready clients=${clients.length} uri=${uri.toString()}`);
    }
    return false;
  }

  if (readyClients.length === clients.length) {
    clearRefreshRetryTimers(uri);
    clearPendingRefresh(uri);
  } else if (allowRetry) {
    rememberPendingRefresh(uri, source);
    logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=partial ready=${readyClients.length} total=${clients.length} uri=${uri.toString()}`);
    scheduleRefreshRetry(uri, source);
  } else {
    logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=partial-wait ready=${readyClients.length} total=${clients.length} uri=${uri.toString()}`);
  }

  for (const { clientId, client } of readyClients) {
    glspConnector.dispatchAction(
      RequestModelAction.create({
        options: {
          sourceUri: uri.toString(),
          diagramType: registeredDiagramType
        }
      }),
      clientId
    );
    sendKickLayoutMessage(client);
  }

  logDiagramDebug(`DIAGRAM_REFRESH source=${source} status=dispatched clients=${readyClients.length} uri=${uri.toString()}`);
  return true;
}

function findDiagramClients(uri: vscode.Uri): Array<{ clientId: string; client: ConnectorClientLike }> {
  const key = uri.toString();
  const clientMap = getConnectorClientMap();
  if (!clientMap) {
    return [];
  }

  const result: Array<{ clientId: string; client: ConnectorClientLike }> = [];
  for (const [clientId, client] of clientMap.entries()) {
    if (client?.document?.uri?.toString() === key) {
      result.push({ clientId, client });
    }
  }
  return result;
}

function getConnectorClientMap(): Map<string, ConnectorClientLike> | undefined {
  if (!glspConnector) {
    return undefined;
  }

  const connector = glspConnector as unknown as { clientMap?: Map<string, ConnectorClientLike> };
  return connector.clientMap instanceof Map ? connector.clientMap : undefined;
}

function flushPendingRefresh(uri: vscode.Uri, trigger: string): void {
  const source = pendingRefreshSources.get(uri.toString());
  if (!source) {
    return;
  }
  refreshDiagramByUri(uri, false, `${source}:${trigger}`);
}

function rememberPendingRefresh(uri: vscode.Uri, source: string): void {
  const key = uri.toString();
  if (source.includes(":retry+") && pendingRefreshSources.has(key)) {
    return;
  }
  pendingRefreshSources.set(key, source);
}

function clearPendingRefresh(uri: vscode.Uri): void {
  pendingRefreshSources.delete(uri.toString());
}

function markDiagramClientNotReady(clientId: string, uri: vscode.Uri, reason: string): void {
  readyDiagramClientIds.delete(clientId);
  knownDiagramClientUris.set(clientId, uri.toString());
  logDiagramDebug(`DIAGRAM_CLIENT status=not-ready clientId=${clientId} reason=${reason} uri=${uri.toString()}`);
}

function clearDiagramClientState(clientId: string): void {
  readyDiagramClientIds.delete(clientId);
  knownDiagramClientUris.delete(clientId);
}

function clearDiagramClientStatesForPanel(panel: vscode.WebviewPanel): void {
  const clientMap = getConnectorClientMap();
  if (!clientMap) {
    return;
  }
  for (const [clientId, client] of clientMap.entries()) {
    if (client.webviewEndpoint?.webviewPanel === panel) {
      clearDiagramClientState(clientId);
    }
  }
}

function resolveDiagramMessageUri(clientId: string | undefined, sourceUri: string | undefined): vscode.Uri | undefined {
  const rawUri = sourceUri ?? (clientId ? knownDiagramClientUris.get(clientId) : undefined);
  if (!rawUri) {
    return undefined;
  }
  try {
    return vscode.Uri.parse(rawUri);
  } catch {
    return undefined;
  }
}

function isDiagramReadyMessage(message: unknown): message is DiagramReadyMessage {
  return typeof message === "object"
    && message !== null
    && (message as DiagramReadyMessage).type === "interlis:diagramReady";
}

function isDiagramLoadFailedMessage(message: unknown): message is DiagramLoadFailedMessage {
  return typeof message === "object"
    && message !== null
    && (message as DiagramLoadFailedMessage).type === "interlis:diagramLoadFailed";
}

function sendKickLayoutMessage(client: ConnectorClientLike): void {
  const panel = client.webviewEndpoint?.webviewPanel;
  if (!panel) {
    return;
  }
  sendKickLayoutMessageToPanel(panel);
}

function sendKickLayoutMessageToPanel(panel: vscode.WebviewPanel): void {
  for (const delayMs of POST_REFRESH_KICK_DELAYS_MS) {
    setTimeout(() => {
      void panel.webview.postMessage({ type: "interlis:kickLayout" });
    }, delayMs);
  }
}

function scheduleRefreshRetry(uri: vscode.Uri, source = "unknown"): void {
  const key = uri.toString();
  clearRefreshRetryTimers(uri);

  const timers: ReturnType<typeof setTimeout>[] = [];
  for (const delayMs of REFRESH_RETRY_DELAYS_MS) {
    const timer = setTimeout(() => {
      refreshDiagramByUri(uri, false, `${source}:retry+${delayMs}ms`);
    }, delayMs);
    timers.push(timer);
  }
  pendingRefreshRetryTimers.set(key, timers);
}

function clearRefreshRetryTimers(uri: vscode.Uri): void {
  const key = uri.toString();
  const retryTimers = pendingRefreshRetryTimers.get(key);
  if (!retryTimers) {
    return;
  }
  for (const timer of retryTimers) {
    clearTimeout(timer);
  }
  pendingRefreshRetryTimers.delete(key);
}

type ConnectorClientLike = {
  clientId: string;
  document?: { uri?: vscode.Uri };
  webviewEndpoint?: {
    webviewPanel?: vscode.WebviewPanel;
  };
};

function logDiagramDebug(message: string): void {
  diagramDebugLogger?.(message);
}

function resolveInterlisSourceUri(resource?: unknown): vscode.Uri | undefined {
  if (resource instanceof vscode.Uri && isInterlisFileUri(resource)) {
    return resource;
  }

  const editor = vscode.window.activeTextEditor;
  if (editor && isInterlisFileDocument(editor.document)) {
    return editor.document.uri;
  }

  const activeTab = vscode.window.tabGroups.activeTabGroup.activeTab;
  if (activeTab?.input instanceof vscode.TabInputText && isInterlisFileUri(activeTab.input.uri)) {
    return activeTab.input.uri;
  }
  if (activeTab?.input instanceof vscode.TabInputCustom && isInterlisFileUri(activeTab.input.uri)) {
    return activeTab.input.uri;
  }

  for (const visibleEditor of vscode.window.visibleTextEditors) {
    if (isInterlisFileDocument(visibleEditor.document)) {
      return visibleEditor.document.uri;
    }
  }

  return lastInterlisSourceUri && isInterlisFileUri(lastInterlisSourceUri)
    ? lastInterlisSourceUri
    : undefined;
}

function isInterlisFileDocument(document: vscode.TextDocument): boolean {
  return document.languageId === "interlis" && isInterlisFileUri(document.uri);
}

function isInterlisFileUri(uri: vscode.Uri): boolean {
  return uri.scheme === "file" && uri.fsPath.toLowerCase().endsWith(".ili");
}

function normalizePath(value: string): string {
  return value.replace(/^\/+/, "");
}

function normalizeProtocol(value: string | undefined): "ws" | "wss" {
  return value === "wss" ? "wss" : "ws";
}

function validateEndpoint(endpoint: GlspEndpoint | undefined): asserts endpoint is GlspEndpoint {
  if (!endpoint) {
    throw new Error("Missing GLSP endpoint response.");
  }
  if (!endpoint.host || endpoint.host.trim().length === 0) {
    throw new Error("GLSP endpoint host is missing.");
  }
  if (!Number.isFinite(endpoint.port) || endpoint.port <= 0) {
    throw new Error(`Invalid GLSP endpoint port: ${String(endpoint.port)}`);
  }
  if (!endpoint.path || endpoint.path.trim().length === 0) {
    throw new Error("GLSP endpoint path is missing.");
  }
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function createNonce(): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  let value = "";
  for (let i = 0; i < 16; i++) {
    value += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return value;
}
