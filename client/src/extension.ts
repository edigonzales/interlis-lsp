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
const TEXT_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?(?:TEXT|MTEXT)\s*$/i;
const TEXT_LENGTH_VALUE_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?(?:TEXT|MTEXT)\s*\*\s*$/i;
const NUMERIC_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?([-+]?[0-9]+(?:\.[0-9]+)?)\s*$/;
const NUMERIC_UPPER_BOUND_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?[-+]?[0-9]+(?:\.[0-9]+)?\s*\.\.\s*$/;
const CONTAINER_BODY_PREFIX_PATTERN = /^\s*[A-Za-z_][A-Za-z0-9_]*\s*$/;
const TOPIC_BODY_AUTO_TRIGGER_LABELS = new Set<string>([
  "CLASS",
  "STRUCTURE",
  "ASSOCIATION",
  "VIEW",
  "GRAPHIC",
  "DOMAIN",
  "UNIT",
  "FUNCTION",
  "CONTEXT",
  "CONSTRAINTS",
  "SIGN BASKET",
  "REFSYSTEM BASKET",
  "CLASS NAME = ... END NAME;",
  "STRUCTURE NAME = ... END NAME;",
  "ASSOCIATION NAME = ... END NAME;",
  "VIEW NAME = ... END NAME;",
  "GRAPHIC NAME = ... END NAME;",
  "DOMAIN NAME = ...;",
  "UNIT NAME = ...;",
  "CONTEXT NAME = ...;",
  "CONSTRAINTS OF ... = ... END;",
  "SIGN BASKET ...",
  "REFSYSTEM BASKET ..."
]);
let umlPanel: vscode.WebviewPanel | undefined;
let htmlPanel: vscode.WebviewPanel | undefined;
let lastDiagramSource: vscode.Uri | undefined;
const autoOpenedDiagramUris = new Set<string>();

type PendingCaret = { version: number; position: vscode.Position };

const pendingCarets = new Map<string, PendingCaret>();
const pendingTailSuggestTimers = new Map<string, ReturnType<typeof setTimeout>>();
const pendingSelectionTailSuggestTimers = new Map<string, ReturnType<typeof setTimeout>>();
const tailSuggestVersions = new Map<string, number>();
const recentTailEditVersions = new Map<string, number>();
const recentTailEditCleanupTimers = new Map<string, ReturnType<typeof setTimeout>>();

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

      scheduleTailSuggest(event);
      scheduleContainerBodySuggest(event);
    })
  );

  context.subscriptions.push(
    vscode.window.onDidChangeTextEditorSelection(event => {
      scheduleSelectionTailSuggest(event);
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
      const key = document.uri.toString();
      const timer = pendingTailSuggestTimers.get(key);
      if (timer) {
        clearTimeout(timer);
        pendingTailSuggestTimers.delete(key);
      }
      const selectionTimer = pendingSelectionTailSuggestTimers.get(key);
      if (selectionTimer) {
        clearTimeout(selectionTimer);
        pendingSelectionTailSuggestTimers.delete(key);
      }
      const cleanupTimer = recentTailEditCleanupTimers.get(key);
      if (cleanupTimer) {
        clearTimeout(cleanupTimer);
        recentTailEditCleanupTimers.delete(key);
      }
      tailSuggestVersions.delete(key);
      recentTailEditVersions.delete(key);
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

function scheduleTailSuggest(event: vscode.TextDocumentChangeEvent): void {
  if (!isEligibleTailSuggestChange(event)) {
    return;
  }

  rememberRecentTailEdit(event.document);

  const key = event.document.uri.toString();
  const existing = pendingTailSuggestTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    const change = event.contentChanges[0];
    if (!change) {
      pendingTailSuggestTimers.delete(key);
      return;
    }
    void maybeTriggerTailSuggest(event.document, change);
    if (!shouldScheduleTailRetry(change)) {
      pendingTailSuggestTimers.delete(key);
      return;
    }
    const retryTimer = setTimeout(() => {
      if (pendingTailSuggestTimers.get(key) === retryTimer) {
        pendingTailSuggestTimers.delete(key);
      }
      void maybeTriggerTailSuggest(event.document, change);
    }, 100);
    pendingTailSuggestTimers.set(key, retryTimer);
  }, 25);
  pendingTailSuggestTimers.set(key, timer);
}

function scheduleSelectionTailSuggest(event: vscode.TextEditorSelectionChangeEvent): void {
  if (!isEligibleSelectionTailSuggestEvent(event)) {
    return;
  }

  const key = event.textEditor.document.uri.toString();
  const existing = pendingSelectionTailSuggestTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    if (pendingSelectionTailSuggestTimers.get(key) === timer) {
      pendingSelectionTailSuggestTimers.delete(key);
    }
    void maybeTriggerTailSuggestForEditor(event.textEditor);
  }, 25);
  pendingSelectionTailSuggestTimers.set(key, timer);
}

function scheduleContainerBodySuggest(event: vscode.TextDocumentChangeEvent): void {
  if (!isEligibleContainerBodySuggestChange(event)) {
    return;
  }

  const key = event.document.uri.toString();
  const existing = pendingTailSuggestTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    pendingTailSuggestTimers.delete(key);
    void maybeTriggerContainerBodySuggest(event.document, event.contentChanges[0]?.text ?? "");
  }, 25);
  pendingTailSuggestTimers.set(key, timer);
}

function isEligibleTailSuggestChange(event: vscode.TextDocumentChangeEvent): boolean {
  if (!event || event.document.languageId !== "interlis" || event.contentChanges.length !== 1) {
    return false;
  }
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== event.document.uri.toString()) {
    return false;
  }
  const change = event.contentChanges[0];
  if (!change || !change.text || /\r|\n/.test(change.text)) {
    return false;
  }
  return change.range.start.line === change.range.end.line;
}

function isEligibleContainerBodySuggestChange(event: vscode.TextDocumentChangeEvent): boolean {
  if (!event || event.document.languageId !== "interlis" || event.contentChanges.length !== 1) {
    return false;
  }
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== event.document.uri.toString()) {
    return false;
  }
  const change = event.contentChanges[0];
  if (!change || !change.text || /\r|\n/.test(change.text)) {
    return false;
  }
  return change.range.start.line === change.range.end.line;
}

function isEligibleSelectionTailSuggestEvent(event: vscode.TextEditorSelectionChangeEvent): boolean {
  if (!event || event.textEditor.document.languageId !== "interlis") {
    return false;
  }
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== event.textEditor.document.uri.toString()) {
    return false;
  }
  if (event.selections.length !== 1 || !event.selections[0]?.isEmpty) {
    return false;
  }
  return recentTailEditVersions.get(event.textEditor.document.uri.toString()) === event.textEditor.document.version;
}

async function maybeTriggerTailSuggest(document: vscode.TextDocument,
                                      change: vscode.TextDocumentContentChangeEvent): Promise<void> {
  void change;
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== document.uri.toString()) {
    return;
  }
  await maybeTriggerTailSuggestForEditor(active);
}

async function maybeTriggerTailSuggestForEditor(editor: vscode.TextEditor): Promise<void> {
  const document = editor.document;
  const key = document.uri.toString();
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== key || active.document.version !== document.version) {
    return;
  }
  if (editor.selections.length !== 1 || !editor.selection.isEmpty) {
    return;
  }
  if (tailSuggestVersions.get(key) === document.version) {
    return;
  }

  const selection = editor.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  if (suffix.trim().length > 0) {
    return;
  }
  const expectedLabels = expectedTailLabels(prefix);
  if (!expectedLabels) {
    return;
  }
  if (!await hasExpectedTailSuggestions(document, selection.active, expectedLabels)) {
    return;
  }

  tailSuggestVersions.set(key, document.version);
  await refreshSuggestWidget();
}

function rememberRecentTailEdit(document: vscode.TextDocument): void {
  const key = document.uri.toString();
  recentTailEditVersions.set(key, document.version);

  const existing = recentTailEditCleanupTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    if (recentTailEditVersions.get(key) === document.version) {
      recentTailEditVersions.delete(key);
    }
    if (recentTailEditCleanupTimers.get(key) === timer) {
      recentTailEditCleanupTimers.delete(key);
    }
  }, 250);
  recentTailEditCleanupTimers.set(key, timer);
}

async function maybeTriggerContainerBodySuggest(document: vscode.TextDocument, insertedText: string): Promise<void> {
  const key = document.uri.toString();
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== key || active.document.version !== document.version) {
    return;
  }
  if (active.selections.length !== 1 || !active.selection.isEmpty) {
    return;
  }
  if (tailSuggestVersions.get(key) === document.version) {
    return;
  }

  const selection = active.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  if (!shouldTriggerContainerBodySuggest(prefix, suffix, insertedText)) {
    return;
  }
  if (!await hasTopicBodySuggestions(document, selection.active)) {
    return;
  }

  tailSuggestVersions.set(key, document.version);
  await refreshSuggestWidget();
}

async function refreshSuggestWidget(): Promise<void> {
  await vscode.commands.executeCommand("hideSuggestWidget");
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
  await vscode.commands.executeCommand("editor.action.triggerSuggest");
}

function shouldTriggerTextTail(prefix: string, insertedText: string): boolean {
  void insertedText;
  return TEXT_TAIL_PATTERN.test(prefix);
}

function shouldTriggerTextLengthValueTail(prefix: string, insertedText: string): boolean {
  if (!TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix)) {
    return false;
  }
  const trimmed = insertedText.trim();
  return trimmed.endsWith("*");
}

function shouldTriggerNumericTail(prefix: string, insertedText: string): boolean {
  const trimmed = insertedText.trim();
  if (!trimmed || !NUMERIC_TAIL_PATTERN.test(prefix)) {
    return false;
  }
  const literal = prefix.match(NUMERIC_TAIL_PATTERN)?.[1] ?? "";
  if (!literal) {
    return false;
  }
  if (trimmed.length > 1) {
    return true;
  }
  if (!/[0-9]/.test(trimmed)) {
    return false;
  }
  if (literal.length === 1 || literal === `-${trimmed}` || literal === `+${trimmed}`) {
    return true;
  }
  return literal.includes(".") && literal.endsWith(trimmed);
}

function shouldTriggerNumericUpperBoundTail(prefix: string, insertedText: string): boolean {
  if (!NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix)) {
    return false;
  }
  const trimmed = insertedText.trim();
  return trimmed.endsWith(".");
}

function expectedTailLabels(prefix: string): string[] | null {
  if (TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix)) {
    return ["<length>"];
  }
  if (TEXT_TAIL_PATTERN.test(prefix)) {
    return ["*", "* <length>"];
  }
  if (NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix)) {
    return ["<upper>"];
  }
  if (NUMERIC_TAIL_PATTERN.test(prefix)) {
    return ["..", ".. <upper>"];
  }
  return null;
}

function shouldScheduleTailRetry(change: vscode.TextDocumentContentChangeEvent): boolean {
  const insertedText = change?.text ?? "";
  return insertedText.length > 1 || (change.rangeLength ?? 0) > 0;
}

function shouldTriggerContainerBodySuggest(prefix: string, suffix: string, insertedText: string): boolean {
  if (!insertedText.trim() || /\r|\n/.test(insertedText)) {
    return false;
  }
  if (suffix.trim().length > 0) {
    return false;
  }
  return CONTAINER_BODY_PREFIX_PATTERN.test(prefix);
}

async function hasTopicBodySuggestions(document: vscode.TextDocument, position: vscode.Position): Promise<boolean> {
  try {
    const completions = await vscode.commands.executeCommand<vscode.CompletionList | vscode.CompletionItem[]>(
      "vscode.executeCompletionItemProvider",
      document.uri,
      position
    );
    const items = Array.isArray(completions) ? completions : completions?.items ?? [];
    return items.some(isTopicBodySuggestionItem);
  } catch {
    return false;
  }
}

async function hasExpectedTailSuggestions(document: vscode.TextDocument,
                                          position: vscode.Position,
                                          expectedLabels: string[]): Promise<boolean> {
  if (!expectedLabels || expectedLabels.length === 0) {
    return false;
  }
  try {
    const completions = await vscode.commands.executeCommand<vscode.CompletionList | vscode.CompletionItem[]>(
      "vscode.executeCompletionItemProvider",
      document.uri,
      position
    );
    const items = Array.isArray(completions) ? completions : completions?.items ?? [];
    const available = new Set(items.map(item => normalizeCompletionLabel(item.label).toUpperCase()));
    return expectedLabels.every(label => available.has(label.toUpperCase()));
  } catch {
    return false;
  }
}

function isTopicBodySuggestionItem(item: vscode.CompletionItem | undefined): boolean {
  if (!item) {
    return false;
  }
  if (item.kind !== vscode.CompletionItemKind.Keyword && item.kind !== vscode.CompletionItemKind.Snippet) {
    return false;
  }
  return TOPIC_BODY_AUTO_TRIGGER_LABELS.has(normalizeCompletionLabel(item.label).toUpperCase());
}

function normalizeCompletionLabel(label: string | vscode.CompletionItemLabel): string {
  return typeof label === "string" ? label : label.label;
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
