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
const MODEL_SNIPPET_PLACEHOLDER_CONTEXT = "interlis.modelSnippetPlaceholder";
const TEXT_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?(?:TEXT|MTEXT)\s*$/i;
const TEXT_LENGTH_VALUE_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?(?:TEXT|MTEXT)\s*\*\s*$/i;
const NUMERIC_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?([-+]?[0-9]+(?:\.[0-9]+)?)\s*$/;
const NUMERIC_UPPER_BOUND_TAIL_PATTERN = /:\s*(?:MANDATORY\s+)?[-+]?[0-9]+(?:\.[0-9]+)?\s*\.\.\s*$/;
const DOMAIN_TEXT_TAIL_PATTERN =
  /^\s*DOMAIN\s+(?:[A-Za-z_][A-Za-z0-9_]*|UUIDOID)(?:\s*\(\s*(?:ABSTRACT|FINAL|GENERIC)\s*\))?(?:\s+EXTENDS\s+[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)?\s*=\s*(?:MANDATORY\s+)?(?:TEXT|MTEXT)\s*$/i;
const DOMAIN_TEXT_LENGTH_VALUE_TAIL_PATTERN =
  /^\s*DOMAIN\s+(?:[A-Za-z_][A-Za-z0-9_]*|UUIDOID)(?:\s*\(\s*(?:ABSTRACT|FINAL|GENERIC)\s*\))?(?:\s+EXTENDS\s+[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)?\s*=\s*(?:MANDATORY\s+)?(?:TEXT|MTEXT)\s*\*\s*$/i;
const DOMAIN_NUMERIC_TAIL_PATTERN =
  /^\s*DOMAIN\s+(?:[A-Za-z_][A-Za-z0-9_]*|UUIDOID)(?:\s*\(\s*(?:ABSTRACT|FINAL|GENERIC)\s*\))?(?:\s+EXTENDS\s+[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)?\s*=\s*(?:MANDATORY\s+)?([-+]?[0-9]+(?:\.[0-9]+)?)\s*$/;
const DOMAIN_NUMERIC_UPPER_BOUND_TAIL_PATTERN =
  /^\s*DOMAIN\s+(?:[A-Za-z_][A-Za-z0-9_]*|UUIDOID)(?:\s*\(\s*(?:ABSTRACT|FINAL|GENERIC)\s*\))?(?:\s+EXTENDS\s+[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)?\s*=\s*(?:MANDATORY\s+)?[-+]?[0-9]+(?:\.[0-9]+)?\s*\.\.\s*$/;
const DOTTED_NAME_REGEX = "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*";
const TEMPLATE_URL_SETTING = "interlisLsp.template.url";
const DEFAULT_TEMPLATE_URL = "https://geo.so.ch/models/AGI/SO_AGI_Modellvorlage_20260324.ili";
const TEMPLATE_FETCH_TIMEOUT_MS = 3000;
const UNIT_DECLARATION_PREFIX_REGEX =
  `^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\])?(?:\\s*\\(\\s*ABSTRACT\\s*\\))?(?:\\s+EXTENDS\\s+${DOTTED_NAME_REGEX})?\\s*=\\s*`;
const CONTAINER_BODY_PREFIX_PATTERN = /^\s*[A-Za-z_][A-Za-z0-9_]*\s*$/;
const HEADER_AFTER_NAME_TRIGGER_PATTERN = /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*\s+$/i;
const BLOCK_HEADER_AFTER_NAME_TRIGGER_PATTERN = /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*\s+[A-Za-z_]*$/i;
const HEADER_MODIFIER_VALUE_TRIGGER_PATTERN = /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*\s*\(\s*[A-Za-z_]*$/i;
const HEADER_MODIFIER_CLOSE_TRIGGER_PATTERN = /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*\s*\(\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\s*$/i;
const HEADER_AFTER_MODIFIER_TRIGGER_PATTERN = /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*\s*\(\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\s*\)\s*$/i;
const BLOCK_HEADER_AFTER_MODIFIER_TRIGGER_PATTERN =
  /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*\s+\(\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\s*\)\s+[A-Za-z_]*$/i;
const HEADER_EXTENDS_OPEN_TRIGGER_PATTERN = /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*(?:\s*\(\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\s*\))?\s+EXTENDS\s*$/i;
const HEADER_AFTER_EXTENDS_TARGET_TRIGGER_PATTERN =
  /^\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\s+[A-Za-z_][A-Za-z0-9_]*(?:\s*\(\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\s*\))?\s+EXTENDS\s+[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*\s+$/i;
const UNIT_HEADER_AFTER_NAME_WITH_ABBR_TRIGGER_PATTERN =
  /^\s*UNIT\s+[A-Za-z_][A-Za-z0-9_]*\s*\[\s*[A-Za-z_][A-Za-z0-9_]*\s*\]\s+$/i;
const UNIT_BLOCK_HEADER_AFTER_NAME_WITH_ABBR_TRIGGER_PATTERN =
  /^\s*UNIT\s+[A-Za-z_][A-Za-z0-9_]*\s*\[\s*[A-Za-z_][A-Za-z0-9_]*\s*\]\s+[A-Za-z_]*$/i;
const UNIT_HEADER_MODIFIER_VALUE_WITH_ABBR_TRIGGER_PATTERN =
  /^\s*UNIT\s+[A-Za-z_][A-Za-z0-9_]*\s*\[\s*[A-Za-z_][A-Za-z0-9_]*\s*\]\s*\(\s*[A-Za-z_]*$/i;
const UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_TRIGGER_PATTERN =
  /^\s*UNIT\s+[A-Za-z_][A-Za-z0-9_]*\s*\[\s*[A-Za-z_][A-Za-z0-9_]*\s*\]\s*\(\s*ABSTRACT\s*$/i;
const UNIT_HEADER_AFTER_MODIFIER_WITH_ABBR_TRIGGER_PATTERN =
  /^\s*UNIT\s+[A-Za-z_][A-Za-z0-9_]*\s*\[\s*[A-Za-z_][A-Za-z0-9_]*\s*\]\s*\(\s*ABSTRACT\s*\)\s*$/i;
const UNIT_BLOCK_HEADER_AFTER_MODIFIER_WITH_ABBR_TRIGGER_PATTERN =
  /^\s*UNIT\s+[A-Za-z_][A-Za-z0-9_]*\s*\[\s*[A-Za-z_][A-Za-z0-9_]*\s*\]\s*\(\s*ABSTRACT\s*\)\s+[A-Za-z_]*$/i;
const UNIT_HEADER_EXTENDS_OPEN_WITH_ABBR_TRIGGER_PATTERN =
  new RegExp(`^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\](?:\\s*\\(\\s*ABSTRACT\\s*\\))?\\s+EXTENDS\\s*$`, "i");
const UNIT_HEADER_AFTER_EXTENDS_TARGET_WITH_ABBR_TRIGGER_PATTERN =
  new RegExp(`^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\](?:\\s*\\(\\s*ABSTRACT\\s*\\))?\\s+EXTENDS\\s+${DOTTED_NAME_REGEX}\\s+$`, "i");
const DOMAIN_RHS_TRIGGER_PATTERN =
  /^\s*DOMAIN\s+(?:[A-Za-z_][A-Za-z0-9_]*|UUIDOID)(?:\s*\(\s*(ABSTRACT|FINAL|GENERIC)\s*\))?(?:\s+EXTENDS\s+[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)?\s*=\s*$/i;
const UNIT_RHS_TRIGGER_PATTERN = new RegExp(`${UNIT_DECLARATION_PREFIX_REGEX}$`, "i");
const UNIT_BRACKET_TARGET_TRIGGER_PATTERN = new RegExp(`${UNIT_DECLARATION_PREFIX_REGEX}\\[\\s*(?:${DOTTED_NAME_REGEX}\\.?)?\\s*$`, "i");
const UNIT_COMPOSED_TARGET_TRIGGER_PATTERN = new RegExp(`${UNIT_DECLARATION_PREFIX_REGEX}\\((?:[^;)]*(?:\\*\\*|\\*|/)\\s*)?(?:${DOTTED_NAME_REGEX}\\.?)?\\s*$`, "i");
const UNIT_COMPOSED_OPERATOR_TRIGGER_PATTERN = new RegExp(`${UNIT_DECLARATION_PREFIX_REGEX}\\([^;)]*${DOTTED_NAME_REGEX}\\s*$`, "i");
const METAATTRIBUTE_ROOT_TRIGGER_PATTERN = /^\s*!!@\s*[A-Za-z0-9_.]*$/i;
const METAATTRIBUTE_VALUE_TRIGGER_PATTERN = /^\s*!!@\s*[A-Za-z0-9_.]+\s*=\s*.*$/i;
const CONTAINER_BODY_AUTO_TRIGGER_LABELS = new Set<string>([
  "MODEL",
  "TOPIC",
  "CLASS",
  "STRUCTURE",
  "DOMAIN",
  "UNIT",
  "FUNCTION",
  "CONTEXT",
  "LINE FORM",
  "ASSOCIATION",
  "VIEW",
  "GRAPHIC",
  "CONSTRAINTS",
  "SIGN BASKET",
  "REFSYSTEM BASKET",
  "MODEL NAME (LANG) AT ... VERSION ... = ... END NAME.",
  "TOPIC NAME = ... END NAME;",
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
const BLANK_INTERLIS_PREFIX = "The INTERLIS file is empty.";

type PendingCaret = { version: number; position: vscode.Position };

class TemplateFetchHttpError extends Error {
  constructor(
    readonly url: string,
    readonly status: number,
    readonly statusText: string
  ) {
    super(`Failed to load INTERLIS template from ${url}: HTTP ${status}${statusText ? ` ${statusText}` : ""}.`);
    this.name = "TemplateFetchHttpError";
  }
}

function isBlankInterlisDocument(document: vscode.TextDocument | undefined): boolean {
  return !!document && document.getText().trim().length === 0;
}

function showBlankInterlisMessage(detail: string): void {
  vscode.window.showInformationMessage(`${BLANK_INTERLIS_PREFIX} ${detail}`);
}

const pendingCarets = new Map<string, PendingCaret>();
const pendingTailSuggestTimers = new Map<string, ReturnType<typeof setTimeout>>();
const pendingHeaderSuggestTimers = new Map<string, ReturnType<typeof setTimeout>>();
const pendingSelectionTailSuggestTimers = new Map<string, ReturnType<typeof setTimeout>>();
const pendingSelectionHeaderSuggestTimers = new Map<string, ReturnType<typeof setTimeout>>();
const recentSuggestFingerprints = new Map<string, string>();
const recentTailEditVersions = new Map<string, number>();
const recentTailEditCleanupTimers = new Map<string, ReturnType<typeof setTimeout>>();
const recentHeaderEditVersions = new Map<string, number>();
const recentHeaderEditCleanupTimers = new Map<string, ReturnType<typeof setTimeout>>();

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
          void reconcileOpenDiagramAfterCompile(vscode.Uri.parse(p.uri), p.success === true);
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
    vscode.commands.registerCommand("interlis.snippet.nextPlaceholder", async () => {
      const before = vscode.window.activeTextEditor;
      const uri = before?.document.uri.toString();
      await vscode.commands.executeCommand("jumpToNextSnippetPlaceholder");
      if (!before || before.document.languageId !== "interlis" || !uri) {
        return;
      }
      setTimeout(() => {
        const active = vscode.window.activeTextEditor;
        if (!active || active.document.uri.toString() !== uri) {
          return;
        }
        void (async () => {
          await updateModelSnippetPlaceholderContext(active, true);
          if (isModelSnippetPlaceholderContext(active)) {
            return;
          }
          await maybeTriggerSnippetPlaceholderSuggest(active);
        })();
      }, 25);
    })
  );

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
      scheduleHeaderSuggest(event);
      const active = vscode.window.activeTextEditor;
      if (active && active.document.uri.toString() === key) {
        void updateModelSnippetPlaceholderContext(active, true);
      }
    })
  );

  context.subscriptions.push(
    vscode.window.onDidChangeTextEditorSelection(event => {
      void updateModelSnippetPlaceholderContext(event.textEditor, true);
      scheduleSelectionTailSuggest(event);
      scheduleSelectionHeaderSuggest(event);
    })
  );

  context.subscriptions.push(
    vscode.window.onDidChangeActiveTextEditor(editor => {
      void updateModelSnippetPlaceholderContext(editor, true);
      void maybeAutoOpenDiagram(editor);
    })
  );

  context.subscriptions.push(
    vscode.workspace.onDidCloseTextDocument(document => {
      cancelScheduledDiagramRefresh(document);
      forgetAutoOpenedDiagram(document);
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
      const selectionHeaderTimer = pendingSelectionHeaderSuggestTimers.get(key);
      if (selectionHeaderTimer) {
        clearTimeout(selectionHeaderTimer);
        pendingSelectionHeaderSuggestTimers.delete(key);
      }
      const headerTimer = pendingHeaderSuggestTimers.get(key);
      if (headerTimer) {
        clearTimeout(headerTimer);
        pendingHeaderSuggestTimers.delete(key);
      }
      const cleanupTimer = recentTailEditCleanupTimers.get(key);
      if (cleanupTimer) {
        clearTimeout(cleanupTimer);
        recentTailEditCleanupTimers.delete(key);
      }
      const headerCleanupTimer = recentHeaderEditCleanupTimers.get(key);
      if (headerCleanupTimer) {
        clearTimeout(headerCleanupTimer);
        recentHeaderEditCleanupTimers.delete(key);
      }
      recentHeaderEditVersions.delete(key);
      recentSuggestFingerprints.delete(key);
      recentTailEditVersions.delete(key);
      void updateModelSnippetPlaceholderContext(vscode.window.activeTextEditor, true);
    })
  );

  void updateModelSnippetPlaceholderContext(vscode.window.activeTextEditor, true);
  void maybeAutoOpenDiagram(vscode.window.activeTextEditor);

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

  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.template.new", async () => {
      try {
        const templateUrl = resolveTemplateUrl();
        const content = await fetchTemplateContent(templateUrl);
        const document = await vscode.workspace.openTextDocument({
          language: "interlis",
          content
        });
        await vscode.window.showTextDocument(document);
      } catch (err: any) {
        vscode.window.showErrorMessage(toTemplateErrorMessage(err));
      }
    })
  );

  // Manual compile command — rely ONLY on notifications for output
  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.compile.run", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) { vscode.window.showWarningMessage("Open an .ili file first."); return; }
      if (isBlankInterlisDocument(editor.document)) {
        showBlankInterlisMessage("Add content and save before compiling.");
        return;
      }

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
      if (isBlankInterlisDocument(editor.document)) {
        showBlankInterlisMessage("Add content and save before generating a diagram.");
        return;
      }

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
      if (isBlankInterlisDocument(editor.document)) {
        showBlankInterlisMessage("Add content and save before generating a diagram.");
        return;
      }

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
      if (isBlankInterlisDocument(editor.document)) {
        showBlankInterlisMessage("Add content and save before rendering documentation.");
        return;
      }

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
      if (isBlankInterlisDocument(editor.document)) {
        showBlankInterlisMessage("Add content and save before exporting GraphML.");
        return;
      }

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
      if (isBlankInterlisDocument(editor.document)) {
        showBlankInterlisMessage("Add content and save before exporting documentation.");
        return;
      }

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

function resolveTemplateUrl(): string {
  const cfg = vscode.workspace.getConfiguration("interlisLsp");
  const configured = cfg.get<string>("template.url");
  const candidate = configured?.trim() ? configured.trim() : DEFAULT_TEMPLATE_URL;

  let parsed: URL;
  try {
    parsed = new URL(candidate);
  } catch {
    throw new Error(`Invalid setting \`${TEMPLATE_URL_SETTING}\`: expected an absolute http or https URL.`);
  }

  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error(`Invalid setting \`${TEMPLATE_URL_SETTING}\`: expected an absolute http or https URL.`);
  }

  return parsed.toString();
}

async function fetchTemplateContent(templateUrl: string): Promise<string> {
  const abortController = new AbortController();
  const timeout = setTimeout(() => abortController.abort(), TEMPLATE_FETCH_TIMEOUT_MS);

  try {
    const response = await fetch(templateUrl, {
      signal: abortController.signal
    });

    if (!response.ok) {
      throw new TemplateFetchHttpError(templateUrl, response.status, response.statusText);
    }

    const content = await response.text();
    if (content.trim().length === 0) {
      throw new Error(`Failed to load INTERLIS template from ${templateUrl}: received an empty response body.`);
    }

    return content;
  } catch (err: any) {
    if (err?.name === "AbortError") {
      throw new Error(`Failed to load INTERLIS template from ${templateUrl}: request timed out after ${TEMPLATE_FETCH_TIMEOUT_MS} ms.`);
    }
    throw err;
  } finally {
    clearTimeout(timeout);
  }
}

function toTemplateErrorMessage(err: unknown): string {
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return `Failed to load INTERLIS template: ${String(err)}`;
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

function scheduleHeaderSuggest(event: vscode.TextDocumentChangeEvent): void {
  if (!isEligibleHeaderSuggestChange(event)) {
    return;
  }

  rememberRecentHeaderEdit(event.document);

  const key = event.document.uri.toString();
  const existing = pendingHeaderSuggestTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    pendingHeaderSuggestTimers.delete(key);
    void maybeTriggerHeaderSuggest(event.document, event.contentChanges[0]?.text ?? "");
  }, 25);
  pendingHeaderSuggestTimers.set(key, timer);
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

function isEligibleHeaderSuggestChange(event: vscode.TextDocumentChangeEvent): boolean {
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

function isEligibleSelectionHeaderSuggestEvent(event: vscode.TextEditorSelectionChangeEvent): boolean {
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
  return recentHeaderEditVersions.get(event.textEditor.document.uri.toString()) === event.textEditor.document.version;
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
  if (isModelSnippetPlaceholderContext(editor)) {
    return;
  }

  const selection = editor.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  const expectedLabels = expectedTailLabels(prefix);
  if (!expectedLabels) {
    return;
  }
  if (!hasAllowedTailSuffix(prefix, suffix)) {
    return;
  }
  if (!await hasExpectedTailSuggestions(document, selection.active, expectedLabels)) {
    return;
  }

  rememberSuggestFingerprint(document, selection.active, "tail");
  await refreshSuggestWidget();
}

async function maybeTriggerDomainRhsSuggestForEditor(editor: vscode.TextEditor): Promise<void> {
  const document = editor.document;
  const key = document.uri.toString();
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== key || active.document.version !== document.version) {
    return;
  }
  if (editor.selections.length !== 1 || !editor.selection.isEmpty) {
    return;
  }

  const selection = editor.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  if (!isDomainRhsTriggerContext(prefix, suffix)) {
    return;
  }
  if (hasRecentSuggestFingerprint(document, selection.active, "domain-rhs")) {
    return;
  }

  const expectedLabels = expectedHeaderLabels(prefix, suffix);
  if (!expectedLabels || !await hasExpectedTailSuggestions(document, selection.active, expectedLabels)) {
    return;
  }

  rememberSuggestFingerprint(document, selection.active, "domain-rhs");
  await refreshSuggestWidget();
}

async function maybeTriggerUnitRhsSuggestForEditor(editor: vscode.TextEditor): Promise<void> {
  const document = editor.document;
  const key = document.uri.toString();
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== key || active.document.version !== document.version) {
    return;
  }
  if (editor.selections.length !== 1 || !editor.selection.isEmpty) {
    return;
  }

  const selection = editor.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  const isUnitContext = isUnitRhsTriggerContext(prefix, suffix)
    || isUnitBracketTargetContext(prefix, suffix)
    || isUnitComposedTargetContext(prefix, suffix)
    || isUnitComposedOperatorContext(prefix, suffix);
  if (!isUnitContext) {
    return;
  }
  if (hasRecentSuggestFingerprint(document, selection.active, "unit-rhs")) {
    return;
  }

  const expectedLabels = expectedHeaderLabels(prefix, suffix);
  const hasSuggestions = expectedLabels
    ? await hasExpectedTailSuggestions(document, selection.active, expectedLabels)
    : await hasAnySuggestions(document, selection.active);
  if (!hasSuggestions) {
    return;
  }

  rememberSuggestFingerprint(document, selection.active, "unit-rhs");
  await refreshSuggestWidget();
}

async function maybeTriggerDeclarationRhsSuggestForEditor(editor: vscode.TextEditor): Promise<void> {
  if (isModelSnippetPlaceholderContext(editor)) {
    return;
  }
  await maybeTriggerDomainRhsSuggestForEditor(editor);
  await maybeTriggerUnitRhsSuggestForEditor(editor);
}

async function maybeTriggerSnippetPlaceholderSuggest(editor: vscode.TextEditor): Promise<void> {
  if (isModelSnippetPlaceholderContext(editor)) {
    return;
  }
  await maybeTriggerDeclarationRhsSuggestForEditor(editor);
}

async function updateModelSnippetPlaceholderContext(editor: vscode.TextEditor | undefined, suppressSuggest: boolean): Promise<void> {
  const enabled = isModelSnippetPlaceholderContext(editor);
  await vscode.commands.executeCommand("setContext", MODEL_SNIPPET_PLACEHOLDER_CONTEXT, enabled);
  if (enabled && suppressSuggest) {
    await vscode.commands.executeCommand("hideSuggestWidget");
  }
}

function isModelSnippetPlaceholderContext(editor: vscode.TextEditor | undefined): boolean {
  if (!editor || editor.document.languageId !== "interlis" || editor.selections.length !== 1) {
    return false;
  }
  return isModelSnippetHeaderPlaceholderPosition(editor.document, editor.selection.active);
}

function isModelSnippetHeaderPlaceholderPosition(document: vscode.TextDocument, position: vscode.Position): boolean {
  if (position.line < 0 || position.line >= document.lineCount) {
    return false;
  }

  const line = document.lineAt(position.line).text;
  const modelPrefixMatch = line.match(/^(\s*MODEL\s+)/);
  if (modelPrefixMatch) {
    const nameStart = modelPrefixMatch[1].length;
    const langStart = line.indexOf(" (", nameStart);
    const langEnd = langStart >= 0 ? line.indexOf(")", langStart + 2) : -1;
    if (langStart > nameStart) {
      if (position.character >= nameStart && position.character <= langStart) {
        return true;
      }
      if (langEnd > langStart && position.character >= langStart + 2 && position.character <= langEnd) {
        return true;
      }
    }
  }

  if (isQuotedModelSnippetField(line, position.character, /^\s*AT\s+"/)) {
    return true;
  }
  return isQuotedModelSnippetField(line, position.character, /^\s*VERSION\s+"/);
}

function isQuotedModelSnippetField(line: string, character: number, prefixPattern: RegExp): boolean {
  const prefixMatch = line.match(prefixPattern);
  if (!prefixMatch) {
    return false;
  }
  const valueStart = prefixMatch[0].length;
  const valueEnd = line.lastIndexOf("\"");
  return valueEnd > valueStart && character >= valueStart && character <= valueEnd;
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

function rememberRecentHeaderEdit(document: vscode.TextDocument): void {
  const key = document.uri.toString();
  recentHeaderEditVersions.set(key, document.version);

  const existing = recentHeaderEditCleanupTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    if (recentHeaderEditVersions.get(key) === document.version) {
      recentHeaderEditVersions.delete(key);
    }
    if (recentHeaderEditCleanupTimers.get(key) === timer) {
      recentHeaderEditCleanupTimers.delete(key);
    }
  }, 250);
  recentHeaderEditCleanupTimers.set(key, timer);
}

function scheduleSelectionHeaderSuggest(event: vscode.TextEditorSelectionChangeEvent): void {
  if (!isEligibleSelectionHeaderSuggestEvent(event)) {
    return;
  }

  const key = event.textEditor.document.uri.toString();
  const existing = pendingSelectionHeaderSuggestTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }

  const timer = setTimeout(() => {
    if (pendingSelectionHeaderSuggestTimers.get(key) === timer) {
      pendingSelectionHeaderSuggestTimers.delete(key);
    }
    void maybeTriggerDeclarationRhsSuggestForEditor(event.textEditor);
  }, 25);
  pendingSelectionHeaderSuggestTimers.set(key, timer);
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
  if (hasRecentSuggestFingerprint(document, active.selection.active, "container-body")) {
    return;
  }

  const selection = active.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  if (!shouldTriggerContainerBodySuggest(prefix, suffix, insertedText)) {
    return;
  }
  if (!await hasContainerBodySuggestions(document, selection.active)) {
    return;
  }

  rememberSuggestFingerprint(document, selection.active, "container-body");
  await refreshSuggestWidget();
}

async function maybeTriggerHeaderSuggest(document: vscode.TextDocument, insertedText: string): Promise<void> {
  const key = document.uri.toString();
  const active = vscode.window.activeTextEditor;
  if (!active || active.document.uri.toString() !== key || active.document.version !== document.version) {
    return;
  }
  if (active.selections.length !== 1 || !active.selection.isEmpty) {
    return;
  }
  if (isModelSnippetPlaceholderContext(active)) {
    return;
  }

  const selection = active.selection;
  const line = document.lineAt(selection.active.line).text;
  const prefix = line.slice(0, selection.active.character);
  const suffix = line.slice(selection.active.character);
  if (!shouldTriggerHeaderSuggest(prefix, suffix, insertedText)) {
    return;
  }
  const triggerKind = headerSuggestTriggerKind(prefix, suffix);
  if (hasRecentSuggestFingerprint(document, selection.active, triggerKind)) {
    return;
  }

  const expectedLabels = expectedHeaderLabels(prefix, suffix);
  const hasSuggestions = expectedLabels
    ? await hasExpectedTailSuggestions(document, selection.active, expectedLabels)
    : await hasAnySuggestions(document, selection.active);
  if (!hasSuggestions) {
    return;
  }

  rememberSuggestFingerprint(document, selection.active, triggerKind);
  await refreshSuggestWidget();
}

async function refreshSuggestWidget(): Promise<void> {
  await vscode.commands.executeCommand("hideSuggestWidget");
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
  await vscode.commands.executeCommand("editor.action.triggerSuggest");
}

function shouldTriggerTextTail(prefix: string, insertedText: string): boolean {
  void insertedText;
  return TEXT_TAIL_PATTERN.test(prefix) || DOMAIN_TEXT_TAIL_PATTERN.test(prefix);
}

function shouldTriggerTextLengthValueTail(prefix: string, insertedText: string): boolean {
  if (!TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix) && !DOMAIN_TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix)) {
    return false;
  }
  const trimmed = insertedText.trim();
  return trimmed.endsWith("*");
}

function shouldTriggerNumericTail(prefix: string, insertedText: string): boolean {
  const trimmed = insertedText.trim();
  const literal = matchNumericTailLiteral(prefix);
  if (!trimmed || !literal) {
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
  if (!NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix) && !DOMAIN_NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix)) {
    return false;
  }
  const trimmed = insertedText.trim();
  return trimmed.endsWith(".");
}

function expectedTailLabels(prefix: string): string[] | null {
  if (TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix) || DOMAIN_TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix)) {
    return ["<length>"];
  }
  if (TEXT_TAIL_PATTERN.test(prefix) || DOMAIN_TEXT_TAIL_PATTERN.test(prefix)) {
    return ["*", "* <length>"];
  }
  if (NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix) || DOMAIN_NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix)) {
    return ["<upper>"];
  }
  if (NUMERIC_TAIL_PATTERN.test(prefix) || DOMAIN_NUMERIC_TAIL_PATTERN.test(prefix)) {
    return ["..", ".. <upper>"];
  }
  return null;
}

function matchNumericTailLiteral(prefix: string): string {
  return prefix.match(NUMERIC_TAIL_PATTERN)?.[1]
    ?? prefix.match(DOMAIN_NUMERIC_TAIL_PATTERN)?.[1]
    ?? "";
}

function isDomainTailContext(prefix: string): boolean {
  return DOMAIN_TEXT_TAIL_PATTERN.test(prefix)
    || DOMAIN_TEXT_LENGTH_VALUE_TAIL_PATTERN.test(prefix)
    || DOMAIN_NUMERIC_TAIL_PATTERN.test(prefix)
    || DOMAIN_NUMERIC_UPPER_BOUND_TAIL_PATTERN.test(prefix);
}

function hasAllowedTailSuffix(prefix: string, suffix: string): boolean {
  const trimmedSuffix = suffix.trim();
  if (trimmedSuffix.length === 0) {
    return true;
  }
  return trimmedSuffix === ";" && isDomainTailContext(prefix);
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

function shouldTriggerHeaderSuggest(prefix: string, suffix: string, insertedText: string): boolean {
  if (!insertedText || /\r|\n/.test(insertedText)) {
    return false;
  }
  if (isMetaAttributeRootContext(prefix, suffix) || isMetaAttributeValueContext(prefix, suffix)) {
    return true;
  }
  const trimmedSuffix = suffix.trim();
  const fixedEqualsSuffix = isFixedEqualsHeaderSuffix(trimmedSuffix);
  if (isDomainRhsTriggerContext(prefix, suffix)) {
    return true;
  }
  if (isUnitRhsTriggerContext(prefix, suffix)
      || isUnitBracketTargetContext(prefix, suffix)
      || isUnitComposedTargetContext(prefix, suffix)
      || isUnitComposedOperatorContext(prefix, suffix)) {
    return true;
  }
  if (trimmedSuffix.length > 0 && !fixedEqualsSuffix) {
    return false;
  }
  if (fixedEqualsSuffix) {
    if (HEADER_AFTER_EXTENDS_TARGET_TRIGGER_PATTERN.test(prefix)
        || UNIT_HEADER_AFTER_EXTENDS_TARGET_WITH_ABBR_TRIGGER_PATTERN.test(prefix)) {
      return false;
    }
    return BLOCK_HEADER_AFTER_NAME_TRIGGER_PATTERN.test(prefix)
      || UNIT_BLOCK_HEADER_AFTER_NAME_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
      || HEADER_MODIFIER_VALUE_TRIGGER_PATTERN.test(prefix)
      || UNIT_HEADER_MODIFIER_VALUE_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
      || HEADER_MODIFIER_CLOSE_TRIGGER_PATTERN.test(prefix)
      || UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
      || BLOCK_HEADER_AFTER_MODIFIER_TRIGGER_PATTERN.test(prefix)
      || UNIT_BLOCK_HEADER_AFTER_MODIFIER_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
      || HEADER_EXTENDS_OPEN_TRIGGER_PATTERN.test(prefix)
      || UNIT_HEADER_EXTENDS_OPEN_WITH_ABBR_TRIGGER_PATTERN.test(prefix);
  }
  return HEADER_AFTER_NAME_TRIGGER_PATTERN.test(prefix)
    || UNIT_HEADER_AFTER_NAME_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
    || HEADER_MODIFIER_VALUE_TRIGGER_PATTERN.test(prefix)
    || UNIT_HEADER_MODIFIER_VALUE_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
    || HEADER_MODIFIER_CLOSE_TRIGGER_PATTERN.test(prefix)
    || UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
    || HEADER_AFTER_MODIFIER_TRIGGER_PATTERN.test(prefix)
    || UNIT_HEADER_AFTER_MODIFIER_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
    || HEADER_EXTENDS_OPEN_TRIGGER_PATTERN.test(prefix)
    || UNIT_HEADER_EXTENDS_OPEN_WITH_ABBR_TRIGGER_PATTERN.test(prefix)
    || HEADER_AFTER_EXTENDS_TARGET_TRIGGER_PATTERN.test(prefix)
    || UNIT_HEADER_AFTER_EXTENDS_TARGET_WITH_ABBR_TRIGGER_PATTERN.test(prefix);
}

function expectedHeaderLabels(prefix: string, suffix: string): string[] | null {
  const fixedEqualsSuffix = isFixedEqualsHeaderSuffix(suffix.trim());
  if (isMetaAttributeRootContext(prefix, suffix) || isMetaAttributeValueContext(prefix, suffix)) {
    return null;
  }
  if (isDomainRhsTriggerContext(prefix, suffix)) {
    return ["TEXT", "BOOLEAN", "NUMERIC"];
  }
  if (isUnitRhsTriggerContext(prefix, suffix)) {
    return ["[BaseUnit]", "1000 [BaseUnit]"];
  }
  if (isUnitBracketTargetContext(prefix, suffix)
      || isUnitComposedTargetContext(prefix, suffix)
      || isUnitComposedOperatorContext(prefix, suffix)) {
    return null;
  }
  const afterNameMatch = prefix.match(fixedEqualsSuffix ? BLOCK_HEADER_AFTER_NAME_TRIGGER_PATTERN : HEADER_AFTER_NAME_TRIGGER_PATTERN);
  if (afterNameMatch) {
    return headerAfterNameLabels(afterNameMatch[1], !fixedEqualsSuffix, hasUnitHeaderAbbreviation(prefix));
  }
  if ((fixedEqualsSuffix ? UNIT_BLOCK_HEADER_AFTER_NAME_WITH_ABBR_TRIGGER_PATTERN : UNIT_HEADER_AFTER_NAME_WITH_ABBR_TRIGGER_PATTERN).test(prefix)) {
    return headerAfterNameLabels("UNIT", !fixedEqualsSuffix, true);
  }
  if (HEADER_MODIFIER_VALUE_TRIGGER_PATTERN.test(prefix) || UNIT_HEADER_MODIFIER_VALUE_WITH_ABBR_TRIGGER_PATTERN.test(prefix)) {
    return headerModifierValueLabels(prefix);
  }
  if (HEADER_MODIFIER_CLOSE_TRIGGER_PATTERN.test(prefix) || UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_TRIGGER_PATTERN.test(prefix)) {
    return [")"];
  }
  if ((fixedEqualsSuffix ? BLOCK_HEADER_AFTER_MODIFIER_TRIGGER_PATTERN : HEADER_AFTER_MODIFIER_TRIGGER_PATTERN).test(prefix)
      || (fixedEqualsSuffix ? UNIT_BLOCK_HEADER_AFTER_MODIFIER_WITH_ABBR_TRIGGER_PATTERN : UNIT_HEADER_AFTER_MODIFIER_WITH_ABBR_TRIGGER_PATTERN).test(prefix)) {
    return fixedEqualsSuffix ? ["EXTENDS"] : ["EXTENDS", "="];
  }
  if (HEADER_AFTER_EXTENDS_TARGET_TRIGGER_PATTERN.test(prefix) || UNIT_HEADER_AFTER_EXTENDS_TARGET_WITH_ABBR_TRIGGER_PATTERN.test(prefix)) {
    return ["="];
  }
  return null;
}

function headerAfterNameLabels(declarationKind: string | undefined, includeEquals: boolean, hasAbbreviation = false): string[] {
  const normalized = (declarationKind ?? "").toUpperCase();
  if (normalized === "TOPIC") {
    return includeEquals ? ["(ABSTRACT)", "(FINAL)", "EXTENDS", "="] : ["(ABSTRACT)", "(FINAL)", "EXTENDS"];
  }
  if (normalized === "DOMAIN") {
    return includeEquals
      ? ["(ABSTRACT)", "(FINAL)", "(GENERIC)", "EXTENDS", "="]
      : ["(ABSTRACT)", "(FINAL)", "(GENERIC)", "EXTENDS"];
  }
  if (normalized === "UNIT") {
    const labels = hasAbbreviation ? ["(ABSTRACT)", "EXTENDS"] : ["[Name]", "(ABSTRACT)", "EXTENDS"];
    return includeEquals ? [...labels, "="] : labels;
  }
  return includeEquals
    ? ["(ABSTRACT)", "(EXTENDED)", "(FINAL)", "EXTENDS", "="]
    : ["(ABSTRACT)", "(EXTENDED)", "(FINAL)", "EXTENDS"];
}

function headerModifierValueLabels(prefix: string): string[] {
  const declarationMatch = prefix.match(HEADER_MODIFIER_VALUE_TRIGGER_PATTERN);
  const declarationKind = (declarationMatch?.[1] ?? "").toUpperCase();
  if (declarationKind === "TOPIC") {
    return ["ABSTRACT", "FINAL"];
  }
  if (declarationKind === "DOMAIN") {
    return ["ABSTRACT", "FINAL", "GENERIC"];
  }
  if (declarationKind === "UNIT") {
    return ["ABSTRACT"];
  }
  return ["ABSTRACT", "EXTENDED", "FINAL"];
}

function isFixedEqualsHeaderSuffix(trimmedSuffix: string): boolean {
  return /^=\s*;?$/.test(trimmedSuffix);
}

function isDomainRhsTriggerContext(prefix: string, suffix: string): boolean {
  const trimmedSuffix = suffix.trim();
  return DOMAIN_RHS_TRIGGER_PATTERN.test(prefix) && (trimmedSuffix.length === 0 || trimmedSuffix === ";");
}

function isUnitRhsTriggerContext(prefix: string, suffix: string): boolean {
  const trimmedSuffix = suffix.trim();
  return UNIT_RHS_TRIGGER_PATTERN.test(prefix) && (trimmedSuffix.length === 0 || trimmedSuffix === ";");
}

function isUnitBracketTargetContext(prefix: string, suffix: string): boolean {
  const trimmedSuffix = suffix.trim();
  return UNIT_BRACKET_TARGET_TRIGGER_PATTERN.test(prefix) && (trimmedSuffix.length === 0 || trimmedSuffix === ";");
}

function isUnitComposedTargetContext(prefix: string, suffix: string): boolean {
  const trimmedSuffix = suffix.trim();
  return UNIT_COMPOSED_TARGET_TRIGGER_PATTERN.test(prefix)
    && (trimmedSuffix.length === 0 || trimmedSuffix === ";" || trimmedSuffix === ")");
}

function isUnitComposedOperatorContext(prefix: string, suffix: string): boolean {
  const trimmedSuffix = suffix.trim();
  return UNIT_COMPOSED_OPERATOR_TRIGGER_PATTERN.test(prefix)
    && (trimmedSuffix.length === 0 || trimmedSuffix === ";" || trimmedSuffix === ")");
}

function headerSuggestTriggerKind(prefix: string, suffix: string): string {
  if (isMetaAttributeRootContext(prefix, suffix)) {
    return "metaattr-root";
  }
  if (isMetaAttributeValueContext(prefix, suffix)) {
    return "metaattr-value";
  }
  if (isDomainRhsTriggerContext(prefix, suffix)) {
    return "domain-rhs";
  }
  if (isUnitRhsTriggerContext(prefix, suffix)
      || isUnitBracketTargetContext(prefix, suffix)
      || isUnitComposedTargetContext(prefix, suffix)
      || isUnitComposedOperatorContext(prefix, suffix)) {
    return "unit-rhs";
  }
  return "header";
}

function hasUnitHeaderAbbreviation(prefix: string): boolean {
  return /\[[^\]]+\]/.test(prefix);
}

function isMetaAttributeRootContext(prefix: string, suffix: string): boolean {
  return METAATTRIBUTE_ROOT_TRIGGER_PATTERN.test(prefix) && suffix.trim().length === 0;
}

function isMetaAttributeValueContext(prefix: string, suffix: string): boolean {
  return METAATTRIBUTE_VALUE_TRIGGER_PATTERN.test(prefix) && suffix.trim().length === 0;
}

async function hasContainerBodySuggestions(document: vscode.TextDocument, position: vscode.Position): Promise<boolean> {
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

async function hasAnySuggestions(document: vscode.TextDocument,
                                 position: vscode.Position): Promise<boolean> {
  try {
    const completions = await vscode.commands.executeCommand<vscode.CompletionList | vscode.CompletionItem[]>(
      "vscode.executeCompletionItemProvider",
      document.uri,
      position
    );
    const items = Array.isArray(completions) ? completions : completions?.items ?? [];
    return items.length > 0;
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
  return CONTAINER_BODY_AUTO_TRIGGER_LABELS.has(normalizeCompletionLabel(item.label).toUpperCase());
}

function normalizeCompletionLabel(label: string | vscode.CompletionItemLabel): string {
  return typeof label === "string" ? label : label.label;
}

function suggestFingerprint(document: vscode.TextDocument,
                            position: vscode.Position,
                            triggerKind: string): string {
  return `${document.version}:${position.line}:${position.character}:${triggerKind}`;
}

function hasRecentSuggestFingerprint(document: vscode.TextDocument,
                                     position: vscode.Position,
                                     triggerKind: string): boolean {
  return recentSuggestFingerprints.get(document.uri.toString()) === suggestFingerprint(document, position, triggerKind);
}

function rememberSuggestFingerprint(document: vscode.TextDocument,
                                    position: vscode.Position,
                                    triggerKind: string): void {
  recentSuggestFingerprints.set(document.uri.toString(), suggestFingerprint(document, position, triggerKind));
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
