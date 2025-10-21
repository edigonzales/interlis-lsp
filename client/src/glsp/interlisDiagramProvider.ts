import { GlspEditorProvider, GlspVscodeConnector } from "@eclipse-glsp/vscode-integration";
import * as vscode from "vscode";

export class InterlisDiagramProvider extends GlspEditorProvider {
  public readonly diagramType = "interlis-uml-diagram";
  private serverInfo: string = "";

  constructor(_extensionContext: vscode.ExtensionContext, connector: GlspVscodeConnector) {
    super(connector);
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
    webview.options = { enableScripts: true };

    const infoHtml = this.serverInfo
      ? `<p style="margin-top:0.5rem;">${this.serverInfo}</p>`
      : "<p style=\"margin-top:0.5rem;\">GLSP server ready.</p>";

    webview.html = `
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline';" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>INTERLIS UML Diagram</title>
        </head>
        <body style="font-family: var(--vscode-font-family); background: var(--vscode-editor-background); color: var(--vscode-editor-foreground);">
          <main style="padding: 1.5rem; line-height: 1.5;">
            <h2 style="margin-top:0;">INTERLIS UML Diagram (preview)</h2>
            <p>This placeholder confirms that the GLSP connector is active for client <code>${clientId}</code>.</p>
            ${infoHtml}
            <p style="margin-top:1.5rem;">
              The current prototype renders class information on the server. Future updates will display the real diagram here.
            </p>
          </main>
        </body>
      </html>
    `;
  }
}
