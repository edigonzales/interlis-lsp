import { GlspEditorProvider, GlspVscodeConnector } from "@eclipse-glsp/vscode-integration";
import * as vscode from "vscode";

export class InterlisDiagramProvider extends GlspEditorProvider {
  public readonly diagramType = "interlis-uml-diagram";
  private readonly extensionContext: vscode.ExtensionContext;
  private serverInfo: string = "";

  constructor(extensionContext: vscode.ExtensionContext, connector: GlspVscodeConnector) {
    super(connector);
    this.extensionContext = extensionContext;
  }

  updateServerInfo(info: string): void {
    this.serverInfo = info;
  }

  override setUpWebview(
    _document: vscode.CustomDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken,
    clientId: string
  ): void {
    const webview = webviewPanel.webview;
    const extensionUri = this.extensionContext.extensionUri;

    webview.options = {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(extensionUri, "dist"),
        vscode.Uri.joinPath(extensionUri, "media"),
        vscode.Uri.joinPath(extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css")
      ]
    };

    const scriptUri = webview.asWebviewUri(
      vscode.Uri.joinPath(extensionUri, "dist", "webview", "interlisDiagram.js")
    );

    const cssFiles = [
      "glsp-vscode.css",
      "diagram.css",
      "tool-palette.css",
      "features.css",
      "command-palette.css",
      "decoration.css"
    ].map(file =>
      webview.asWebviewUri(
        vscode.Uri.joinPath(extensionUri, "node_modules", "@eclipse-glsp", "vscode-integration-webview", "css", file)
      )
    );

    const codiconCss = webview.asWebviewUri(
      vscode.Uri.joinPath(extensionUri, "node_modules", "@vscode", "codicons", "dist", "codicon.css")
    );
    const bundleCss = webview.asWebviewUri(
      vscode.Uri.joinPath(extensionUri, "dist", "webview", "interlisDiagram.css")
    );
    const customCss = webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, "media", "interlis-glsp.css"));
    const nonce = this.createNonce();
    const csp = this.buildCsp(webview, nonce);

    const cssLinks = [codiconCss, ...cssFiles, bundleCss, customCss]
      .map(uri => `<link rel="stylesheet" href="${uri}">`)
      .join("\n          ");

    const statusMarkup = this.serverInfo
      ? `<span class="interlis-glsp-status">${this.escapeHtml(this.serverInfo)}</span>`
      : "";

    webview.html = `<!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta http-equiv="Content-Security-Policy" content="${csp}" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>INTERLIS UML Diagram</title>
          ${cssLinks}
        </head>
        <body>
          <div class="interlis-glsp-root">
            <header class="interlis-glsp-header">
              <h2>INTERLIS UML Diagram</h2>
              ${statusMarkup}
            </header>
            <section class="interlis-glsp-canvas">
              <div class="interlis-glsp-diagram">
                <div id="${clientId}"></div>
                <div id="${clientId}_hidden" style="display:none;"></div>
              </div>
            </section>
          </div>
          <script nonce="${nonce}" src="${scriptUri}"></script>
        </body>
      </html>`;
  }

  private createNonce(): string {
    const charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    let nonce = "";
    for (let i = 0; i < 32; i++) {
      nonce += charset.charAt(Math.floor(Math.random() * charset.length));
    }
    return nonce;
  }

  private escapeHtml(input: string): string {
    return input.replace(/[&<>"']/g, char =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
      }[char as "&" | "<" | ">" | '"' | "'"] ?? char)
    );
  }

  private buildCsp(webview: vscode.Webview, nonce: string): string {
    const source = webview.cspSource;
    return [
      "default-src 'none'",
      `img-src ${source} https: data:`,
      `font-src ${source} https: data:`,
      `style-src ${source} 'unsafe-inline'`,
      `script-src 'nonce-${nonce}'`
    ].join("; ");
  }
}
