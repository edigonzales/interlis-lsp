import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as vscode from "vscode";
import { spawn } from "child_process";
import { resolveIli2GpkgJarPath } from "./configuration";

type Ili2GpkgOptions = {
  dbfile: string;
  defaultSrsCode?: string;
  createEnumTabs: boolean;
  nameByTopic: boolean;
  createFk: boolean;
  createGeomIdx: boolean;
  createUnique: boolean;
  models?: string;
  modeldir?: string;
};

const STATE_KEY = "ili2gpkg.lastOptions";

export function registerIli2GpkgCommands(context: vscode.ExtensionContext, javaPath: string) {
  const ili2dbOutput = vscode.window.createOutputChannel("ILI2DB");
  context.subscriptions.push(ili2dbOutput);

  const disposable = vscode.commands.registerCommand("interlis.ili2gpkg.createSchema", async () => {
    const ili2gpkgConfig = vscode.workspace.getConfiguration("ili2gpkg");
    let jarPath: string;
    try {
      jarPath = resolveIli2GpkgJarPath(context, ili2gpkgConfig.get<string>("jarPath"));
    } catch (err) {
      return;
    }

    const lastOptions = context.globalState.get<Ili2GpkgOptions>(STATE_KEY) ?? {
      createEnumTabs: false,
      nameByTopic: false,
      createFk: false,
      createGeomIdx: false,
      createUnique: false
    };

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
      ...lastOptions,
      models: activeModelName ?? lastOptions.models ?? "",
      modeldir: computedModelDir ?? lastOptions.modeldir ?? modelRepositories,
      dbfile: lastOptions.dbfile ?? (activeDir ? path.join(activeDir, "interlis.gpkg") : "")
    };

    const dbfile = await promptString("GeoPackage file name", defaults.dbfile, true);
    if (!dbfile) {
      return;
    }

    const defaultSrsCode = await promptString("Default SRS code", defaults.defaultSrsCode ?? "", false);
    if (defaultSrsCode === undefined) {
      return;
    }
    const createEnumTabs = await promptBoolean("Create enum tables (--createEnumTabs)", defaults.createEnumTabs);
    if (createEnumTabs === undefined) {
      return;
    }
    const nameByTopic = await promptBoolean("Name tables by topic (--nameByTopic)", defaults.nameByTopic);
    if (nameByTopic === undefined) {
      return;
    }
    const createFk = await promptBoolean("Create foreign keys (--createFk)", defaults.createFk);
    if (createFk === undefined) {
      return;
    }
    const createGeomIdx = await promptBoolean("Create geometry index (--createGeomIdx)", defaults.createGeomIdx);
    if (createGeomIdx === undefined) {
      return;
    }
    const createUnique = await promptBoolean("Create unique constraints (--createUnique)", defaults.createUnique);
    if (createUnique === undefined) {
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
      dbfile,
      defaultSrsCode: defaultSrsCode?.trim() || undefined,
      createEnumTabs,
      nameByTopic,
      createFk,
      createGeomIdx,
      createUnique,
      models: models?.trim() || undefined,
      modeldir: modeldir?.trim() || undefined
    };

    await context.globalState.update(STATE_KEY, options);

    const logPath = path.join(os.tmpdir(), `ili2gpkg-${Date.now()}.log`);
    await removeIfExists(logPath);
    ili2dbOutput.clear();

    const args: string[] = ["-jar", jarPath, "--dbfile", options.dbfile];
    if (options.defaultSrsCode) {
      args.push("--defaultSrsCode", options.defaultSrsCode);
    }
    if (options.createEnumTabs) {
      args.push("--createEnumTabs");
    }
    if (options.nameByTopic) {
      args.push("--nameByTopic");
    }
    if (options.createFk) {
      args.push("--createFk");
    }
    if (options.createGeomIdx) {
      args.push("--createGeomIdx");
    }
    if (options.createUnique) {
      args.push("--createUnique");
    }
    if (options.models) {
      args.push("--models", options.models);
    }
    if (options.modeldir) {
      args.push("--modeldir", options.modeldir);
    }
    args.push("--log", logPath, "--schemaimport");

    const commandPreview = [javaPath, ...args.map(quoteArg)].join(" ");
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

      child.stdout.on("data", data => ili2dbOutput.append(data.toString()));
      child.stderr.on("data", data => ili2dbOutput.append(data.toString()));

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

async function promptString(placeHolder: string, value: string, required: boolean): Promise<string | undefined> {
  return vscode.window.showInputBox({
    prompt: placeHolder,
    value,
    validateInput: input => (required && !input.trim() ? "This field is required" : undefined)
  });
}

async function promptBoolean(placeHolder: string, value: boolean): Promise<boolean | undefined> {
  const items = [
    { label: "Yes", value: true },
    { label: "No", value: false }
  ];
  const picked = await vscode.window.showQuickPick(items, {
    placeHolder,
    canPickMany: false,
    activeItem: items.find(item => item.value === value)
  });

  return picked?.value;
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
