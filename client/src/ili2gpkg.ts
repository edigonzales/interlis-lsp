import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as vscode from "vscode";
import { spawn } from "child_process";
import { resolveIli2GpkgJarPath } from "./configuration";

type Ili2GpkgOptions = {
  configPath: string;
  dbfile: string;
  models?: string;
  modeldir?: string;
};

const STATE_KEY = "ili2gpkg.lastOptions";
const DEFAULT_CONFIG_RELATIVE_PATH = path.join("resources", "ili2gpkg", "default-config.ini");

export function registerIli2GpkgCommands(context: vscode.ExtensionContext, javaPath: string) {
  const ili2dbOutput = vscode.window.createOutputChannel("ILI2DB");
  context.subscriptions.push(ili2dbOutput);

  const disposable = vscode.commands.registerCommand("interlis.ili2gpkg.createSchema", async () => {
    const ili2gpkgConfig = vscode.workspace.getConfiguration("interlisLsp");
    let jarPath: string;
    try {
      jarPath = resolveIli2GpkgJarPath(context, ili2gpkgConfig.get<string>("ili2gpkgJarPath"));
    } catch (err) {
      return;
    }

    const bundledConfigPath = resolveBundledConfigPath(context);
    if (!bundledConfigPath) {
      return;
    }

    const lastOptions = context.globalState.get<Ili2GpkgOptions>(STATE_KEY);

    const activeEditor = vscode.window.activeTextEditor;
    const activeUri = activeEditor?.document.uri;
    const activeDir = activeUri?.scheme === "file" ? path.dirname(activeUri.fsPath) : undefined;
    const activeModelName = activeUri?.scheme === "file"
      ? path.basename(activeUri.fsPath, path.extname(activeUri.fsPath))
      : undefined;
    const interlisCfg = vscode.workspace.getConfiguration("interlisLsp");
    const modelRepositories = interlisCfg.get<string>("modelRepositories") ?? "";
    const computedModelDir = activeDir
      ? [activeDir, modelRepositories].filter(Boolean).join(";")
      : undefined;

    const defaults: Ili2GpkgOptions = {
      configPath: lastOptions?.configPath ?? bundledConfigPath,
      dbfile: lastOptions?.dbfile ?? (activeDir ? path.join(activeDir, "interlis.gpkg") : ""),
      models: activeModelName ?? lastOptions?.models ?? "",
      modeldir: computedModelDir ?? lastOptions?.modeldir ?? modelRepositories
    };

    const configPath = await pickConfigFile(defaults.configPath, bundledConfigPath);
    if (!configPath) {
      return;
    }

    const dbfile = await pickDbFile(defaults.dbfile);
    if (!dbfile) {
      return;
    }

    const models = await promptString("Models (--models)", defaults.models ?? "", false);
    if (models === undefined) {
      return;
    }
    const modeldir = await promptString("Model directory (--modeldir)", defaults.modeldir ?? "", false);
    if (modeldir === undefined) {
      return;
    }

    const options: Ili2GpkgOptions = {
      configPath,
      dbfile,
      models: models?.trim() || undefined,
      modeldir: modeldir?.trim() || undefined
    };

    await context.globalState.update(STATE_KEY, options);

    const logPath = computeLogPath(dbfile);
    await removeIfExists(logPath);
    ili2dbOutput.clear();

    const args: string[] = ["-jar", jarPath, "--metaConfig", options.configPath, "--dbfile", options.dbfile];
    if (options.models) {
      args.push("--models", options.models);
    }
    if (options.modeldir) {
      args.push("--modeldir", `"${options.modeldir}"`);
    }
    args.push("--log", logPath, "--schemaimport");

    //const commandPreview = [javaPath, ...args.map(quoteArg)].join(" ");
    const commandPreview = [javaPath, ...args].join(" ");
    const confirmation = await vscode.window.showInformationMessage(
      "Review ili2gpkg command",
      { modal: true, detail: commandPreview },
      "Run"
    );
    if (confirmation !== "Run") {
      return;
    }

    const executionCwd = activeDir && fs.existsSync(activeDir) ? activeDir : undefined;
    let exitCode: number;
    try {
      const child = spawn(javaPath, args, { cwd: executionCwd });

      exitCode = await new Promise(resolve => {
        child.on("error", () => resolve(-1));
        child.on("close", code => resolve(code ?? -1));
      });
    } catch (err: any) {
      ili2dbOutput.appendLine(`Failed to start ili2gpkg: ${err?.message ?? err}`);
      ili2dbOutput.show(true);
      return;
    }

    await displayLog(logPath, ili2dbOutput);

    if (exitCode === 0) {
      vscode.window.showInformationMessage("ili2gpkg finished successfully.");
    } else {
      vscode.window.showErrorMessage(`ili2gpkg failed with exit code ${exitCode}.`);
    }
  });

  context.subscriptions.push(disposable);
}

function resolveBundledConfigPath(context: vscode.ExtensionContext): string | undefined {
  const resolved = context.asAbsolutePath(DEFAULT_CONFIG_RELATIVE_PATH);
  if (!fs.existsSync(resolved)) {
    vscode.window.showErrorMessage("The bundled ili2gpkg default configuration is missing.");
    return undefined;
  }
  return resolved;
}

async function promptString(placeHolder: string, value: string, required: boolean): Promise<string | undefined> {
  return vscode.window.showInputBox({
    prompt: placeHolder,
    value,
    validateInput: input => (required && !input.trim() ? "This field is required" : undefined)
  });
}

async function pickConfigFile(lastPath: string, fallbackPath: string): Promise<string | undefined> {
  const defaultUri = fs.existsSync(lastPath)
    ? vscode.Uri.file(lastPath)
    : fs.existsSync(fallbackPath)
      ? vscode.Uri.file(fallbackPath)
      : undefined;

  const selection = await vscode.window.showOpenDialog({
    canSelectFiles: true,
    canSelectMany: false,
    filters: { "ili2gpkg config": ["ini"] },
    openLabel: "Use ini file",
    title: "Select ili2gpkg configuration (.ini)",
    defaultUri
  });

  if (selection?.[0]) {
    return selection[0].fsPath;
  }

  return fs.existsSync(fallbackPath) ? fallbackPath : undefined;
}

async function pickDbFile(previousPath?: string): Promise<string | undefined> {
  const defaultUri = previousPath ? vscode.Uri.file(previousPath) : undefined;
  const saveUri = await vscode.window.showSaveDialog({
    defaultUri,
    filters: {
      "GeoPackage": ["gpkg"],
      "SQLite": ["sqlite", "db"],
      "All files": ["*"]
    },
    title: "Select GeoPackage file",
    saveLabel: "Use this file"
  });

  return saveUri?.fsPath;
}

function computeLogPath(dbfile: string): string {
  const directory = path.dirname(dbfile);
  if (directory && fs.existsSync(directory)) {
    return path.join(directory, `ili2gpkg-${Date.now()}.log`);
  }
  return path.join(os.tmpdir(), `ili2gpkg-${Date.now()}.log`);
}

function quoteArg(arg: string): string {
  if (/[\s"]/u.test(arg)) {
    return `"${arg.replace(/"/gu, '\\"')}"`;
  }
  return arg;
}

async function removeIfExists(target: string) {
  try {
    await fs.promises.rm(target, { force: true });
  } catch {
    // ignore
  }
}

async function displayLog(logPath: string, channel: vscode.OutputChannel) {
  try {
    const content = await fs.promises.readFile(logPath, "utf8");
    channel.append(content);
    channel.show(true);
  } catch (err: any) {
    channel.appendLine(`Failed to read ili2gpkg log: ${err?.message ?? err}`);
    channel.show(true);
  }
}
