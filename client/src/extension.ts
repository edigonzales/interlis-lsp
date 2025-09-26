import * as vscode from "vscode";
import { LanguageClient, LanguageClientOptions, Executable, ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export async function activate(context: vscode.ExtensionContext) {
  const cfg = vscode.workspace.getConfiguration();
  const jarPath = cfg.get<string>("interlisLsp.server.jarPath")!;
  const javaPath = cfg.get<string>("interlisLsp.javaPath")!;

  const output = vscode.window.createOutputChannel("INTERLIS LSP");

  const serverExec: Executable = {
    command: javaPath,
    // oder =System.err
    args: [
      "-Dorg.slf4j.simpleLogger.logFile=/Users/stefan/tmp/interlis-lsp.log", 
      "-Dorg.slf4j.simpleLogger.showDateTime=true",
      "-Dorg.slf4j.simpleLogger.dateTimeFormat=\"yyyy-MM-dd HH:mm:ss.SSS\"",
      "-jar",
      jarPath
    ],
    options: { env: process.env }
  };
  const serverOptions: ServerOptions = serverExec;

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ language: "interlis", scheme: "file" }],
    // send current settings once at startup
    initializationOptions: {
      modelRepositories: cfg.get<string>("interlisLsp.modelRepositories") ?? ""
    },
    synchronize: { 
      fileEvents: vscode.workspace.createFileSystemWatcher("**/*.ili"),
      configurationSection: "interlisLsp" 
    },
    outputChannel: output,
    traceOutputChannel: output
  };

  client = new LanguageClient("interlisLsp", "INTERLIS Language Server", serverOptions, clientOptions);
  context.subscriptions.push(client);
  await client.start();

  // Our client-side command uses a different id and calls the server command
  context.subscriptions.push(
    vscode.commands.registerCommand("interlis.validate.run", async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) {
        vscode.window.showWarningMessage("Open an .ili file first.");
        return;
      }
      const fileUri = editor.document.uri.toString();
      try {
        const log = await client!.sendRequest("workspace/executeCommand", {
          command: "interlis.validate",          // <-- server command id
          arguments: [fileUri]
        });
        const chan = vscode.window.createOutputChannel("INTERLIS LSP – Log");
        chan.clear();
        chan.appendLine(String(log ?? ""));
        chan.show(true);
      } catch (e: any) {
        vscode.window.showErrorMessage(`Validation failed: ${e?.message ?? e}`);
      }
    })
  );
}

export function deactivate() {
  return client?.stop();
}
