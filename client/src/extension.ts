import * as vscode from "vscode";
import { LanguageClient, LanguageClientOptions, Executable, ServerOptions, State } from "vscode-languageclient/node";

let client: LanguageClient | undefined;
let revealOutputOnNextLog = false;
const CARET_SENTINEL = "__INTERLIS_AUTOCLOSE_CARET__";

type PendingCaret = { version: number; position: vscode.Position };

const pendingCarets = new Map<string, PendingCaret>();

export async function activate(context: vscode.ExtensionContext) {
  const cfg = vscode.workspace.getConfiguration("interlisLsp");
  const jarPath = vscode.workspace.getConfiguration().get<string>("interlisLsp.server.jarPath")!;
  const javaPath = vscode.workspace.getConfiguration().get<string>("interlisLsp.javaPath") || "java";

  // Single channel
  const OUTPUT_NAME = "INTERLIS LSP";
  const output = vscode.window.createOutputChannel(OUTPUT_NAME); // no { log: true }
  const exec: Executable = {
    command: javaPath,
    args: [
      "-Dorg.slf4j.simpleLogger.logFile=/Users/stefan/tmp/interlis-lsp.log",
      "-Dorg.slf4j.simpleLogger.showDateTime=true",
      '-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS', // no quotes inside
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
  const registerHandlersOnce = () => {
    if (handlersRegistered) return;
    handlersRegistered = true;

    client!.onNotification("interlis/clearLog", () => {
      output.clear();
      if (revealOutputOnNextLog) {
        output.show(true);
        revealOutputOnNextLog = false;
      }
    });

    client!.onNotification("interlis/log", (p: { text?: string }) => {
      if (p?.text) {
        output.append(p.text);
        ensurePanelVisible();
      }
    });
  };

  registerHandlersOnce(); // ensure handlers exist now
  client.onDidChangeState(e => { if (e.newState === State.Running) registerHandlersOnce(); });

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
