import * as vscode from "vscode";
import * as path from "path";
import * as fs from "fs";
import { LanguageClient, LanguageClientOptions, Executable, ServerOptions, State } from "vscode-languageclient/node";
import { GlspEditorProvider, GlspVscodeConnector, configureDefaultCommands } from "@eclipse-glsp/vscode-integration";
import { GlspSocketServerLauncher, SocketGlspVscodeServer } from "@eclipse-glsp/vscode-integration/node";
import { ActionMessage, RequestModelAction } from "@eclipse-glsp/protocol";
import { isInterlisDocument, refreshDiagramForDocument, type DiagramClientRegistry } from "./glsp/diagram-refresh";

let client: LanguageClient | undefined;
let revealOutputOnNextLog = false;
const CARET_SENTINEL = "__INTERLIS_AUTOCLOSE_CARET__";
let umlPanel: vscode.WebviewPanel | undefined;
let htmlPanel: vscode.WebviewPanel | undefined;
let lastDiagramSource: vscode.Uri | undefined;

interface GlspResources {
  launcher: GlspSocketServerLauncher;
  server: SocketGlspVscodeServer;
  connector: GlspVscodeConnector;
  provider: InterlisGlspEditorProvider;
}

let glspResources: GlspResources | undefined;
let glspInitialization: Promise<GlspResources> | undefined;

type PendingCaret = { version: number; position: vscode.Position };

const pendingCarets = new Map<string, PendingCaret>();

type SupportedPlatform = "darwin-arm64" | "darwin-x64" | "linux-x64" | "linux-arm64" | "win32-x64";

const PLATFORM_FOLDERS: Record<SupportedPlatform, string> = {
  "darwin-arm64": "darwin-arm64",
  "darwin-x64": "darwin-x64",
  "linux-x64": "linux-x64",
  "linux-arm64": "linux-arm64",
  "win32-x64": "win32-x64"
};

export async function activate(context: vscode.ExtensionContext) {
  const cfg = vscode.workspace.getConfiguration("interlisLsp");
  const jarPath = resolveJarPath(context, cfg.get<string>("server.jarPath"));
  const javaPath = resolveJavaPath(context, cfg.get<string>("javaPath"));

  // Single channel
  const OUTPUT_NAME = "INTERLIS LSP";
  const output = vscode.window.createOutputChannel(OUTPUT_NAME); // no { log: true }
  const exec: Executable = {
    command: javaPath,
    args: [
      // "-Dorg.slf4j.simpleLogger.logFile=/Users/stefan/tmp/interlis-lsp.log",
      // "-Dorg.slf4j.simpleLogger.showDateTime=true",
      // '-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS', // no quotes inside
      //"-agentlib:native-image-agent=config-output-dir=/Users/stefan/sources/interlis-lsp/src/main/resources/META-INF/native-image,config-write-period-secs=5",
      //"-agentlib:native-image-agent=config-merge-dir=/Users/stefan/sources/interlis-lsp/src/main/resources/META-INF/native-image,config-write-initial-delay-secs=5,config-write-period-secs=5",
      "-jar",
      jarPath
    ],
    // command: "/Users/stefan/sources/interlis-lsp/build/native/nativeCompile/interlis-lsp",
    // args: [
    //   "-Dorg.slf4j.simpleLogger.logFile=/Users/stefan/tmp/interlis-lsp.log",
    //   "-Dorg.slf4j.simpleLogger.showDateTime=true",
    //   '-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS', // no quotes inside
    // ],
    options: { env: process.env }
  };
  const serverOptions: ServerOptions = exec;

  const caretMiddleware: LanguageClientOptions["middleware"] = {
    provideOnTypeFormattingEdits: async (document, position, ch, options, token, next) => {
      const edits = await next(document, position, ch, options, token);
      if (!edits || edits.length === 0) {
        pendingCarets.delete(document.uri.toString());
        return edits;
      }

      let caret: vscode.Position | undefined;
      const sanitized = edits.map(edit => {
        if (!edit || typeof edit.newText !== "string") {
          return edit;
        }

        let newText = edit.newText;
        let localCaret: vscode.Position | undefined;
        let idx = newText.indexOf(CARET_SENTINEL);

        while (idx !== -1) {
          const before = newText.slice(0, idx);
          const beforeLines = before.split(/\r?\n/);
          const lineDelta = beforeLines.length - 1;
          const lastLine = beforeLines[beforeLines.length - 1] ?? "";
          const charDelta = lastLine.length;
          const line = edit.range.start.line + lineDelta;
          const character = lineDelta === 0
            ? edit.range.start.character + charDelta
            : charDelta;
          localCaret = new vscode.Position(line, character);
          newText = before + newText.slice(idx + CARET_SENTINEL.length);
          idx = newText.indexOf(CARET_SENTINEL);
        }

        if (localCaret) {
          caret = localCaret;
        }

        return newText === edit.newText ? edit : vscode.TextEdit.replace(edit.range, newText);
      });

      if (caret) {
        pendingCarets.set(document.uri.toString(), { version: document.version + 1, position: caret });
      } else {
        pendingCarets.delete(document.uri.toString());
      }

      return sanitized;
    }
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ language: "interlis", scheme: "file" }],
    initializationOptions: {
      modelRepositories: cfg.get<string>("modelRepositories") ?? ""
    },
    synchronize: { configurationSection: "interlisLsp" },
    middleware: caretMiddleware,
    outputChannel: output,
    traceOutputChannel: output
  };

  client = new LanguageClient("interlisLsp", "INTERLIS Language Server", serverOptions, clientOptions);
  context.subscriptions.push(client);
  await client.start();

  try {
    await ensureGlspInfrastructure(context);
  } catch (error: any) {
    console.error("Failed to initialize INTERLIS GLSP support", error);
  }

  context.subscriptions.push(
    vscode.workspace.onDidChangeTextDocument(event => {
      const key = event.document.uri.toString();
      const pending = pendingCarets.get(key);
      if (!pending) {
        return;
      }

      if (event.document.version < pending.version) {
        return;
      }

      pendingCarets.delete(key);

      const active = vscode.window.activeTextEditor;
      if (!active || active.document.uri.toString() !== key) {
        return;
      }

      const { position } = pending;
      const selection = new vscode.Selection(position, position);
      active.selection = selection;
      active.revealRange(new vscode.Range(position, position));
    })
  );

  context.subscriptions.push(
    vscode.workspace.onDidSaveTextDocument(document => {
      if (!glspResources) {
        return;
      }

      if (!isInterlisDocument(document)) {
        return;
      }

      refreshDiagramForDocument(glspResources.connector, glspResources.provider, document.uri);
    })
  );

  // Register notification handlers ONCE, immediately
  let handlersRegistered = false;
  const revealOutputChannel = (preserveEditorFocus = true) => {
    output.show(preserveEditorFocus);
    void ensurePanelVisible(preserveEditorFocus);
  };

  const registerHandlersOnce = () => {
    if (handlersRegistered) return;
    handlersRegistered = true;

    client!.onNotification("interlis/clearLog", () => {
      output.clear();
      if (revealOutputOnNextLog) {
        revealOutputChannel(true);
        revealOutputOnNextLog = false;
      }
    });

    client!.onNotification("interlis/log", (p: { text?: string }) => {
      if (p?.text) {
        output.append(p.text);
        revealOutputChannel(true);
      }
    });
  };

  registerHandlersOnce(); // ensure handlers exist now
  client.onDidChangeState(e => { if (e.newState === State.Running) registerHandlersOnce(); });

  async function showUmlHtml(html: string, title: string) {
    const column = vscode.ViewColumn.Beside;
    if (!umlPanel) {
      umlPanel = vscode.window.createWebviewPanel(
        "interlisUmlDiagram",
        "INTERLIS UML Diagram",
        column,
        { enableScripts: true, retainContextWhenHidden: true }
      );
      umlPanel.onDidDispose(() => { umlPanel = undefined; }, undefined, context.subscriptions);
      umlPanel.webview.onDidReceiveMessage(async message => {
        if (!message || message.type !== "downloadSvg" || typeof message.svg !== "string") {
          return;
        }

        const filename = typeof message.filename === "string" && message.filename.trim().length > 0
          ? message.filename.trim()
          : "diagram.svg";

        let defaultUri: vscode.Uri | undefined;
        if (lastDiagramSource && lastDiagramSource.scheme === "file") {
          const folder = path.dirname(lastDiagramSource.fsPath);
          defaultUri = vscode.Uri.file(path.join(folder, filename));
        }

        try {
          const options: vscode.SaveDialogOptions = {
            saveLabel: "Save UML diagram",
            filters: { SVG: ["svg"] }
          };
          if (defaultUri) {
            options.defaultUri = defaultUri;
          }

          const target = await vscode.window.showSaveDialog(options);
          if (!target) {
            return;
          }

          const encoder = new TextEncoder();
          await vscode.workspace.fs.writeFile(target, encoder.encode(message.svg));

          if (target.scheme === "file") {
            vscode.window.showInformationMessage(`Saved UML diagram to ${target.fsPath}`);
          } else {
            vscode.window.showInformationMessage("Saved UML diagram.");
          }
        } catch (err: any) {
          vscode.window.showErrorMessage(`Failed to save UML diagram: ${err?.message ?? err}`);
        }
      }, undefined, context.subscriptions);
    } else {
      umlPanel.reveal(umlPanel.viewColumn ?? vscode.ViewColumn.Beside, true);
    }

    umlPanel.title = title;
    umlPanel.webview.html = html;
  }

  // Manual compile command — rely ONLY on notifications for output
  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.compile.run", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      revealOutputOnNextLog = true; // show OUTPUT for manual runs
      const fileUri = editor.document.uri.toString();

      try {
        await client!.sendRequest("workspace/executeCommand", {
          command: "interlis.compile",
          arguments: [fileUri]
        });
        // DO NOT clear/append here — the server already did via notifications
      } catch (e: any) {
        vscode.window.showErrorMessage(`Compilation failed: ${e?.message ?? e}`);
        // Optional: bring Output to front to show any client-side errors
        output.show(true);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.uml.show", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      const fileUri = editor.document.uri.toString();

      try {
        const html = await client!.sendRequest("workspace/executeCommand", {
          command: "interlis.uml",
          arguments: [fileUri]
        }) as string;

        lastDiagramSource = editor.document.uri;

        await showUmlHtml(html, "INTERLIS UML Diagram (Mermaid)");
      } catch (e: any) {
        vscode.window.showErrorMessage(`UML generation failed: ${e?.message ?? e}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.uml.plant.show", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      const fileUri = editor.document.uri.toString();

      try {
        const html = await client!.sendRequest("workspace/executeCommand", {
          command: "interlis.uml.plant",
          arguments: [fileUri]
        }) as string;

        lastDiagramSource = editor.document.uri;

        await showUmlHtml(html, "INTERLIS UML Diagram (PlantUML)");
      } catch (e: any) {
        vscode.window.showErrorMessage(`PlantUML generation failed: ${e?.message ?? e}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.html.show", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      const fileUri = editor.document.uri.toString();

      try {
        const html = await client!.sendRequest<string>("interlis/exportHtml", { uri: fileUri });
        if (!html) {
          throw new Error("Server returned an empty document");
        }

        const column = vscode.ViewColumn.Beside;
        if (!htmlPanel) {
          htmlPanel = vscode.window.createWebviewPanel(
            "interlisModelDocumentation",
            "INTERLIS Documentation",
            column,
            { enableScripts: false, retainContextWhenHidden: true }
          );
          htmlPanel.onDidDispose(() => { htmlPanel = undefined; }, undefined, context.subscriptions);
        } else {
          htmlPanel.reveal(htmlPanel.viewColumn ?? column, true);
        }

        htmlPanel.webview.html = html;
      } catch (err: any) {
        vscode.window.showErrorMessage(`Failed to render INTERLIS documentation: ${err?.message ?? err}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.graphml.export", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      const documentUri = editor.document.uri;
      const fileUri = documentUri.toString();

      let defaultUri: vscode.Uri | undefined;
      if (documentUri.scheme === "file") {
        const folder = path.dirname(documentUri.fsPath);
        const base = path.basename(documentUri.fsPath, path.extname(documentUri.fsPath));
        defaultUri = vscode.Uri.file(path.join(folder, `${base}.graphml`));
      }

      const saveOptions: vscode.SaveDialogOptions = {
        saveLabel: "Export INTERLIS UML (GraphML)",
        filters: { "GraphML": ["graphml"] }
      };
      if (defaultUri) {
        saveOptions.defaultUri = defaultUri;
      }

      const target = await vscode.window.showSaveDialog(saveOptions);
      if (!target) {
        return;
      }

      try {
        const graphml = await client!.sendRequest<string>("interlis/exportGraphml", { uri: fileUri });
        if (!graphml) {
          throw new Error("Server returned an empty document");
        }

        const bytes = Buffer.from(graphml, "utf8");
        await vscode.workspace.fs.writeFile(target, bytes);

        if (target.scheme === "file") {
          vscode.window.showInformationMessage(`Saved GraphML UML diagram to ${target.fsPath}`);
        } else {
          vscode.window.showInformationMessage("Saved GraphML UML diagram.");
        }
      } catch (err: any) {
        vscode.window.showErrorMessage(`Failed to export GraphML UML diagram: ${err?.message ?? err}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.docx.export", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      const documentUri = editor.document.uri;
      const fileUri = documentUri.toString();

      let defaultUri: vscode.Uri | undefined;
      if (documentUri.scheme === "file") {
        const folder = path.dirname(documentUri.fsPath);
        const base = path.basename(documentUri.fsPath, path.extname(documentUri.fsPath));
        defaultUri = vscode.Uri.file(path.join(folder, `${base}.docx`));
      }

      const saveOptions: vscode.SaveDialogOptions = {
        saveLabel: "Export INTERLIS documentation",
        filters: { "Word Document": ["docx"] }
      };  
      if (defaultUri) {
        saveOptions.defaultUri = defaultUri;
      }

      const target = await vscode.window.showSaveDialog(saveOptions);
      if (!target) {
        return;
      }

      try {
        const base64 = await client!.sendRequest<string>("interlis/exportDocx", { uri: fileUri });
        if (!base64) {
          throw new Error("Server returned an empty document");
        }

        const bytes = Buffer.from(base64, "base64");
        await vscode.workspace.fs.writeFile(target, bytes);

        if (target.scheme === "file") {
          vscode.window.showInformationMessage(`Saved INTERLIS documentation to ${target.fsPath}`);
        } else {
          vscode.window.showInformationMessage("Saved INTERLIS documentation.");
        }
      } catch (err: any) {
        vscode.window.showErrorMessage(`Failed to export INTERLIS documentation: ${err?.message ?? err}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.glsp.open", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }

      const document = editor.document;
      if (!isInterlisDocument(document)) {
        vscode.window.showWarningMessage("INTERLIS GLSP diagrams are only available for .ili files.");
        return;
      }

      try {
        await ensureGlspInfrastructure(context);
        await vscode.commands.executeCommand(
          "vscode.openWith",
          document.uri,
          InterlisGlspEditorProvider.viewType,
          { preview: false, viewColumn: vscode.ViewColumn.Beside }
        );
      } catch (err: any) {
        vscode.window.showErrorMessage(`Failed to open GLSP diagram: ${err?.message ?? err}`);
      }
    })
  );
}

async function ensureGlspInfrastructure(context: vscode.ExtensionContext): Promise<GlspResources> {
  if (glspResources) {
    return glspResources;
  }

  if (glspInitialization) {
    return glspInitialization;
  }

  glspInitialization = (async () => {
    const configuration = vscode.workspace.getConfiguration("interlisLsp");
    const jarPath = resolveGlspJarPath(context, configuration.get<string>("glsp.jarPath"));
    const port = configuration.get<number>("glsp.port") ?? 5050;
    const host = "127.0.0.1";

    const launcher = new GlspSocketServerLauncher({
      executable: jarPath,
      socketConnectionOptions: { host, port }
    });

    try {
      await launcher.start();
    } catch (error) {
      launcher.dispose();
      throw error;
    }

    const server = new SocketGlspVscodeServer({
      clientId: "interlis-glsp-client",
      clientName: "INTERLIS GLSP",
      connectionOptions: { host, port }
    });

    try {
      await server.start();
    } catch (error) {
      launcher.dispose();
      throw error;
    }

    const connector = new GlspVscodeConnector({
      server,
      onBeforePropagateMessageToServer: (original, processed) => {
        const candidate = processed ?? original;
        return ensureRequestCarriesSourceUri(candidate);
      }
    });

    const provider = new InterlisGlspEditorProvider(context, connector);

    configureDefaultCommands({ extensionContext: context, diagramPrefix: "interlis.glsp", connector });

    const registration = vscode.window.registerCustomEditorProvider(
      InterlisGlspEditorProvider.viewType,
      provider,
      { supportsMultipleEditorsPerDocument: true, webviewOptions: { retainContextWhenHidden: true } }
    );

    context.subscriptions.push(launcher, server, connector, registration);

    glspResources = { launcher, server, connector, provider };
    return glspResources;
  })();

  try {
    return await glspInitialization;
  } finally {
    glspInitialization = undefined;
  }
}

function ensureRequestCarriesSourceUri(message: unknown): unknown {
  if (!glspResources) {
    return message;
  }

  if (ActionMessage.is(message) && RequestModelAction.is(message.action)) {
    const documentUri = glspResources.provider.getDocumentUri(message.clientId);
    if (documentUri) {
      const options = { ...(message.action.options ?? {}), sourceUri: documentUri.toString() };
      return { ...message, action: { ...message.action, options } };
    }
  }

  return message;
}

class InterlisGlspEditorProvider extends GlspEditorProvider implements DiagramClientRegistry {
  public static readonly viewType = "interlis.glsp.diagram";

  private readonly context: vscode.ExtensionContext;
  private readonly clientDocuments = new Map<string, vscode.Uri>();

  constructor(context: vscode.ExtensionContext, connector: GlspVscodeConnector) {
    super(connector);
    this.context = context;
  }

  get diagramType(): string {
    return "interlis-class-diagram";
  }

  getDocumentUri(clientId: string): vscode.Uri | undefined {
    return this.clientDocuments.get(clientId);
  }

  public getClientIdsForDocument(documentUri: vscode.Uri): readonly string[] {
    const target = documentUri.toString();
    const matches: string[] = [];
    for (const [clientId, uri] of this.clientDocuments.entries()) {
      if (uri.toString() === target) {
        matches.push(clientId);
      }
    }
    return matches;
  }

  public setUpWebview(
    document: vscode.CustomDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken,
    clientId: string
  ): void {
    this.clientDocuments.set(clientId, document.uri);
    webviewPanel.onDidDispose(() => this.clientDocuments.delete(clientId));

    const webview = webviewPanel.webview;
    webview.options = {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(this.context.extensionUri, "dist"),
        vscode.Uri.joinPath(this.context.extensionUri, "media"),
        vscode.Uri.joinPath(this.context.extensionUri, "media", "glsp")
      ]
    };

    const scriptUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, "dist", "interlis-glsp-webview.js")
    );
    const baseStyleUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, "media", "glsp", "glsp-vscode.css")
    );
    const customStyleUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, "media", "interlis-glsp.css")
    );
    const codiconStyleUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.context.extensionUri, "media", "glsp", "codicon.css")
    );
    const nonce = getNonce();

    webview.html = this.renderHtml(
      clientId,
      scriptUri,
      baseStyleUri,
      customStyleUri,
      codiconStyleUri,
      nonce,
      webview.cspSource
    );
  }

  private renderHtml(
    clientId: string,
    scriptUri: vscode.Uri,
    baseStyleUri: vscode.Uri,
    customStyleUri: vscode.Uri,
    codiconStyleUri: vscode.Uri,
    nonce: string,
    cspSource: string
  ): string {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${cspSource} 'unsafe-inline'; img-src ${cspSource} https: data:; script-src 'nonce-${nonce}'; font-src ${cspSource} https: data:;">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link rel="stylesheet" href="${codiconStyleUri}">
  <link rel="stylesheet" href="${baseStyleUri}">
  <link rel="stylesheet" href="${customStyleUri}">
  <title>INTERLIS GLSP Diagram</title>
</head>
<body>
  <div id="${clientId}_container" class="glsp-container"></div>
  <script nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`;
  }
}

function resolveJarPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  const override = configured?.trim();
  if (override) {
    return override;
  }

  const serverDir = context.asAbsolutePath("server");
  const bundled = path.join(serverDir, "interlis-lsp-all.jar");
  if (fs.existsSync(bundled)) {
    return bundled;
  }

  if (fs.existsSync(serverDir)) {
    const jar = fs.readdirSync(serverDir)
      .filter(file => file.endsWith("-all.jar"))
      .map(file => path.join(serverDir, file))
      .sort()
      .pop();
    if (jar && fs.existsSync(jar)) {
      return jar;
    }
  }

  const message = "INTERLIS LSP fat JAR not found. Configure `interlisLsp.server.jarPath` in the extension settings.";
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}

function resolveGlspJarPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  const override = configured?.trim();
  if (override) {
    return override;
  }

  const serverDir = context.asAbsolutePath("server");
  const bundled = path.join(serverDir, "interlis-glsp-all.jar");
  if (fs.existsSync(bundled)) {
    return bundled;
  }

  if (fs.existsSync(serverDir)) {
    const jar = fs.readdirSync(serverDir)
      .filter(file => file.startsWith("interlis-glsp") && file.endsWith("-all.jar"))
      .map(file => path.join(serverDir, file))
      .sort()
      .pop();
    if (jar && fs.existsSync(jar)) {
      return jar;
    }
  }

  const message = "INTERLIS GLSP fat JAR not found. Configure `interlisLsp.glsp.jarPath` in the extension settings.";
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}

function resolveJavaPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  const override = configured?.trim();
  if (override) {
    return override;
  }

  const platformKey = `${process.platform}-${process.arch}`;
  const folder = PLATFORM_FOLDERS[platformKey as SupportedPlatform];
  if (!folder) {
    const message = `Unsupported platform ${platformKey}. Configure interlisLsp.javaPath to point to a Java runtime.`;
    vscode.window.showErrorMessage(message);
    throw new Error(message);
  }

  const executableName = process.platform === "win32" ? "java.exe" : "java";
  const bundled = context.asAbsolutePath(path.join("server", "jre", folder, "bin", executableName));
  if (fs.existsSync(bundled)) {
    return bundled;
  }

  const message = "Bundled Java runtime not found. Configure `interlisLsp.javaPath` in the extension settings.";
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}

function getNonce(): string {
  const characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  let result = "";
  for (let i = 0; i < 32; i++) {
    result += characters.charAt(Math.floor(Math.random() * characters.length));
  }
  return result;
}

async function ensurePanelVisible(preserveEditorFocus = true) {
  await vscode.commands.executeCommand("workbench.action.focusPanel");
  if (preserveEditorFocus) {
    await vscode.commands.executeCommand("workbench.action.focusActiveEditorGroup");
  }
}

export function deactivate() {
  return client?.stop();
}
