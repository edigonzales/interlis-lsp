import * as vscode from "vscode";
import { LanguageClient, LanguageClientOptions, Executable, ServerOptions, State } from "vscode-languageclient/node";
import * as path from "path";
import { resolveJavaPath, resolveServerJarPath } from "./configuration";
import {
  cancelScheduledDiagramRefresh,
  forgetAutoOpenedDiagram,
  maybeAutoOpenDiagram,
  reconcileOpenDiagramAfterCompile,
  registerInterlisDiagramCommands,
  registerInterlisDiagramEditor,
  setDiagramDebugLogger
} from "./diagram/diagramEditor";

let client: LanguageClient | undefined;
let revealOutputOnNextLog = false;
const CARET_SENTINEL = "__INTERLIS_AUTOCLOSE_CARET__";
let umlPanel: vscode.WebviewPanel | undefined;
let htmlPanel: vscode.WebviewPanel | undefined;
let lastDiagramSource: vscode.Uri | undefined;
const autoOpenedDiagramUris = new Set<string>();

type PendingCaret = { version: number; position: vscode.Position };

const pendingCarets = new Map<string, PendingCaret>();

class TimestampedOutputChannel implements vscode.OutputChannel {
  readonly name: string;
  private atLineStart = true;

  constructor(private readonly delegate: vscode.OutputChannel) {
    this.name = delegate.name;
  }

  append(value: string): void {
    if (!value) {
      return;
    }
    this.delegate.append(this.formatChunk(value));
  }

  appendLine(value: string): void {
    if (!value) {
      this.delegate.appendLine(`${this.timestamp()} `);
      this.atLineStart = true;
      return;
    }
    this.delegate.appendLine(this.formatChunk(value));
    this.atLineStart = true;
  }

  replace(value: string): void {
    this.atLineStart = true;
    if (!value) {
      this.delegate.replace(value);
      return;
    }
    this.delegate.replace(this.formatChunk(value));
  }

  clear(): void {
    this.atLineStart = true;
    this.delegate.clear();
  }

  show(columnOrPreserveFocus?: vscode.ViewColumn | boolean, preserveFocus?: boolean): void {
    if (typeof columnOrPreserveFocus === "boolean" || columnOrPreserveFocus === undefined) {
      this.delegate.show(columnOrPreserveFocus);
      return;
    }
    this.delegate.show(columnOrPreserveFocus, preserveFocus);
  }

  hide(): void {
    this.delegate.hide();
  }

  dispose(): void {
    this.delegate.dispose();
  }

  private formatChunk(value: string): string {
    let formatted = "";
    for (let index = 0; index < value.length; index += 1) {
      if (this.atLineStart) {
        formatted += `${this.timestamp()} `;
        this.atLineStart = false;
      }
      const char = value[index];
      formatted += char;
      if (char === "\n") {
        this.atLineStart = true;
      }
    }
    return formatted;
  }

  private timestamp(): string {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, "0");
    const minutes = String(now.getMinutes()).padStart(2, "0");
    const seconds = String(now.getSeconds()).padStart(2, "0");
    const millis = String(now.getMilliseconds()).padStart(3, "0");
    return `[${hours}:${minutes}:${seconds}.${millis}]`;
  }
}

export async function activate(context: vscode.ExtensionContext) {
  const cfg = vscode.workspace.getConfiguration("interlisLsp");
  const jarPath = resolveServerJarPath(context, cfg.get<string>("server.jarPath"));
  const javaPath = resolveJavaPath(context, cfg.get<string>("javaPath"));
  const jvmArgs = cfg.get<string[]>("server.jvmArgs") ?? [];

  const output = vscode.window.createOutputChannel("INTERLIS LSP");
  const rawDebugOutput = vscode.window.createOutputChannel("INTERLIS LSP Debug");
  const debugOutput = new TimestampedOutputChannel(rawDebugOutput);
  context.subscriptions.push(output, rawDebugOutput);
  setDiagramDebugLogger(message => debugOutput.appendLine(message));
  context.subscriptions.push(new vscode.Disposable(() => setDiagramDebugLogger(undefined)));
  debugOutput.appendLine(`Using server JAR: ${jarPath}`);
  debugOutput.appendLine(`Using Java runtime: ${javaPath}`);
  if (jvmArgs.length > 0) {
    debugOutput.appendLine(`Using JVM args: ${jvmArgs.join(" ")}`);
  }

  const exec: Executable = {
    command: javaPath,
    args: [
      ...jvmArgs,
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
      modelRepositories: cfg.get<string>("modelRepositories") ?? "",
      diagram: {
        layout: {
          edgeRouting: cfg.get<string>("diagram.layout.edgeRouting") ?? "ORTHOGONAL"
        },
        showCardinalities: cfg.get<boolean>("diagram.showCardinalities") ?? true
      }
    },
    synchronize: { configurationSection: "interlisLsp" },
    middleware: caretMiddleware,
    outputChannel: output,
    traceOutputChannel: debugOutput
  };

  client = new LanguageClient("interlisLsp", "INTERLIS Language Server", serverOptions, clientOptions);
  context.subscriptions.push(client);
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

    client!.onNotification("interlis/debugLog", (p: { text?: string }) => {
      if (p?.text) {
        debugOutput.append(p.text);
      }
    });

    client!.onNotification("interlis/compileFinished", (p: { uri?: string; success?: boolean }) => {
      if (p?.uri) {
        try {
          reconcileOpenDiagramAfterCompile(vscode.Uri.parse(p.uri), p.success === true);
        } catch {
          // Ignore invalid URIs from optional client notifications.
        }
      }
    });
  };

  registerHandlersOnce(); // ensure handlers exist now
  client.onDidChangeState(e => { if (e.newState === State.Running) registerHandlersOnce(); });

  await client.start();
  try {
    await registerInterlisDiagramEditor(context, () => client);
    registerInterlisDiagramCommands(context);
  } catch (err: any) {
    vscode.window.showErrorMessage(`INTERLIS GLSP diagram integration failed to start: ${err?.message ?? err}`);
  }

  context.subscriptions.push(
    vscode.workspace.onDidChangeTextDocument(event => {
      const key = event.document.uri.toString();
      const pending = pendingCarets.get(key);
      if (pending) {
        if (event.document.version >= pending.version) {
          pendingCarets.delete(key);

          const active = vscode.window.activeTextEditor;
          if (active && active.document.uri.toString() === key) {
            const { position } = pending;
            const selection = new vscode.Selection(position, position);
            active.selection = selection;
            active.revealRange(new vscode.Range(position, position));
          }
        }
      }

    })
  );

  context.subscriptions.push(
    vscode.window.onDidChangeActiveTextEditor(editor => {
      void maybeAutoOpenDiagram(editor, autoOpenedDiagramUris);
    })
  );

  context.subscriptions.push(
    vscode.workspace.onDidCloseTextDocument(document => {
      cancelScheduledDiagramRefresh(document);
      forgetAutoOpenedDiagram(document, autoOpenedDiagramUris);
    })
  );

  void maybeAutoOpenDiagram(vscode.window.activeTextEditor, autoOpenedDiagramUris);

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
