import * as path from "path";
import * as vscode from "vscode";
import { GlspEditorProvider, GlspVscodeConnector, SocketGlspVscodeServer } from "@eclipse-glsp/vscode-integration/node";
import { LanguageClient } from "vscode-languageclient/node";

export const DIAGRAM_EDITOR_VIEW_TYPE = "interlis.diagramEditor";
const GLSP_ENDPOINT_REQUEST = "interlis/glspEndpoint";
const DEFAULT_DIAGRAM_TYPE = "interlis-uml";

const CFG_AUTO_OPEN_BESIDE = "diagram.autoOpenBeside";

type GetClient = () => LanguageClient | undefined;

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
  }

  private createWebviewHtml(webview: vscode.Webview, clientId: string, title: string): string {
    const nonce = createNonce();
    const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, "dist", "glspWebview.js"));

    const stylesheetUris = [
      vscode.Uri.joinPath(this.extensionUri, "dist", "glspWebview.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "sprotty", "css", "sprotty.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", "glsp-vscode.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", "diagram.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", "features.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", "command-palette.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", "tool-palette.css"),
      vscode.Uri.joinPath(this.extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", "decoration.css")
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
      min-height: 0;
      min-width: 0;
      background: #ffffff;
    }
    .interlis-diagram .sprotty,
    .interlis-diagram .sprotty svg,
    .interlis-diagram .sprotty-graph {
      background: #ffffff !important;
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
    vscode.commands.registerCommand("interlis.diagram.open", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== "interlis") {
        vscode.window.showWarningMessage("Open an .ili file first.");
        return;
      }
      await openInterlisDiagramBeside(editor.document.uri, true);
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
  openedUris.delete(document.uri.toString());
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

function isInterlisFileDocument(document: vscode.TextDocument): boolean {
  return document.languageId === "interlis" && document.uri.scheme === "file";
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
