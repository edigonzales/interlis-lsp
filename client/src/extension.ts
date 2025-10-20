import * as vscode from "vscode";
import * as path from "path";
import * as fs from "fs";
import { LanguageClient, LanguageClientOptions, Executable, ServerOptions, State } from "vscode-languageclient/node";
import { GlspSupport } from "./glspSupport";

let client: LanguageClient | undefined;
let revealOutputOnNextLog = false;
const CARET_SENTINEL = "__INTERLIS_AUTOCLOSE_CARET__";
let umlPanel: vscode.WebviewPanel | undefined;
let htmlPanel: vscode.WebviewPanel | undefined;
let lastDiagramSource: vscode.Uri | undefined;

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

  const glspSupport = new GlspSupport(client);
  void glspSupport.refresh();

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
    vscode.commands.registerCommand("interlis.glsp.info", async () => {
      const info = await glspSupport.refresh();
      if (!info) {
        return;
      }
      const rawPath = info.path ?? "";
      const normalizedPath = rawPath.length === 0 ? "" : rawPath.startsWith("/") ? rawPath : `/${rawPath}`;
      const endpoint = `ws://${info.host || "127.0.0.1"}:${info.port}${normalizedPath}`;
      const status = info.running ? "running" : "not running";
      void vscode.window.showInformationMessage(`INTERLIS GLSP server is ${status} at ${endpoint}`);
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

async function ensurePanelVisible(preserveEditorFocus = true) {
  await vscode.commands.executeCommand("workbench.action.focusPanel");
  if (preserveEditorFocus) {
    await vscode.commands.executeCommand("workbench.action.focusActiveEditorGroup");
  }
}

export function deactivate() {
  return client?.stop();
}
