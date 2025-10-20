import 'reflect-metadata';

import { GlspEditorProvider } from '@eclipse-glsp/vscode-integration';
import { GlspVscodeConnector, SocketGlspVscodeServer, configureDefaultCommands } from '@eclipse-glsp/vscode-integration/node';
import * as vscode from 'vscode';

import { GlspSupport, GlspConnectionInfo } from './glspSupport';

const DIAGRAM_VIEW_TYPE = 'interlis.glsp.diagram';
const DIAGRAM_TYPE = 'interlis-class-diagram';

class InterlisGlspEditorProvider extends GlspEditorProvider {
    override diagramType = DIAGRAM_TYPE;

    constructor(
        private readonly context: vscode.ExtensionContext,
        connector: GlspVscodeConnector
    ) {
        super(connector);
    }

    override setUpWebview(
        _document: vscode.CustomDocument,
        webviewPanel: vscode.WebviewPanel,
        _token: vscode.CancellationToken,
        clientId: string
    ): void {
        const webview = webviewPanel.webview;
        webview.options = { enableScripts: true, retainContextWhenHidden: true };

        const extensionUri = this.context.extensionUri;
        const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, 'dist', 'webview.js'));
        const codiconsUri = webview.asWebviewUri(
            vscode.Uri.joinPath(extensionUri, 'node_modules', '@vscode', 'codicons', 'dist', 'codicon.css')
        );
        const glspCssUri = webview.asWebviewUri(
            vscode.Uri.joinPath(
                extensionUri,
                'node_modules',
                '@eclipse-glsp',
                'vscode-integration-webview',
                'css',
                'glsp-vscode.css'
            )
        );
        const balloonCssUri = webview.asWebviewUri(
            vscode.Uri.joinPath(extensionUri, 'node_modules', 'balloon-css', 'balloon.min.css')
        );

        const csp = [
            "default-src 'none'",
            `img-src ${webview.cspSource} https: data:`,
            `style-src ${webview.cspSource} 'unsafe-inline'`,
            `font-src ${webview.cspSource} https: data:`,
            `script-src ${webview.cspSource}`
        ].join('; ');

        webview.html = `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta http-equiv="Content-Security-Policy" content="${csp}" />
    <meta name="viewport" content="width=device-width, height=device-height" />
    <link rel="stylesheet" href="${codiconsUri}" />
    <link rel="stylesheet" href="${glspCssUri}" />
    <link rel="stylesheet" href="${balloonCssUri}" />
  </head>
  <body style="margin:0;padding:0;">
    <div id="${clientId}_container" style="height:100vh;width:100vw;"></div>
    <script src="${scriptUri}"></script>
  </body>
</html>`;
    }
}

function normalizePath(path: string): string {
    if (!path) {
        return '';
    }
    return path.startsWith('/') ? path.slice(1) : path;
}

interface ConnectorSetup {
    server: SocketGlspVscodeServer;
    connector: GlspVscodeConnector;
}

async function createConnector(info: GlspConnectionInfo | undefined): Promise<ConnectorSetup | undefined> {
    if (!info || !info.running) {
        return undefined;
    }

    const server = new SocketGlspVscodeServer({
        clientId: 'interlis.glsp.client',
        clientName: 'INTERLIS Class Diagram',
        connectionOptions: {
            protocol: 'ws',
            host: info.host || '127.0.0.1',
            port: info.port,
            path: normalizePath(info.path)
        }
    });

    await server.start();
    const connector = new GlspVscodeConnector({ server });
    return { server, connector };
}

export class InterlisGlspManager {
    private connector: GlspVscodeConnector | undefined;
    private editorRegistration: vscode.Disposable | undefined;

    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly glspSupport: GlspSupport
    ) {}

    async showDiagram(uri?: vscode.Uri): Promise<void> {
        const target = uri ?? vscode.window.activeTextEditor?.document.uri;
        if (!target) {
            void vscode.window.showInformationMessage('Open an INTERLIS file to show its GLSP diagram.');
            return;
        }

        const connector = await this.ensureInitialized();
        if (!connector) {
            return;
        }

        await vscode.commands.executeCommand('vscode.openWith', target, DIAGRAM_VIEW_TYPE, vscode.ViewColumn.Beside);
    }

    private async ensureInitialized(): Promise<GlspVscodeConnector | undefined> {
        if (this.connector) {
            return this.connector;
        }

        const info = this.glspSupport.info ?? (await this.glspSupport.refresh());
        if (!info || !info.running) {
            void vscode.window.showWarningMessage('The INTERLIS GLSP server is not running.');
            return undefined;
        }

        try {
            const setup = await createConnector(info);
            if (!setup) {
                return undefined;
            }

            this.connector = setup.connector;

            const provider = new InterlisGlspEditorProvider(this.context, this.connector);
            this.editorRegistration = vscode.window.registerCustomEditorProvider(
                DIAGRAM_VIEW_TYPE,
                provider,
                { webviewOptions: { retainContextWhenHidden: true } }
            );
            this.context.subscriptions.push(this.editorRegistration, this.connector, setup.server);

            configureDefaultCommands({
                extensionContext: this.context,
                connector: this.connector,
                diagramPrefix: 'interlis.glsp'
            });

            return this.connector;
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            void vscode.window.showErrorMessage(`Failed to connect to INTERLIS GLSP server: ${message}`);
            return undefined;
        }
    }
}

export function registerGlspFeatures(context: vscode.ExtensionContext, glspSupport: GlspSupport): InterlisGlspManager {
    const manager = new InterlisGlspManager(context, glspSupport);
    context.subscriptions.push(
        vscode.commands.registerCommand('interlis.uml.glsp.show', (uri?: vscode.Uri) => {
            void manager.showDiagram(uri);
        })
    );
    return manager;
}
