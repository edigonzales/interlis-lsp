import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";

type SupportedPlatform = "darwin-arm64" | "darwin-x64" | "linux-x64" | "linux-arm64" | "win32-x64";

const PLATFORM_FOLDERS: Record<SupportedPlatform, string> = {
  "darwin-arm64": "darwin-arm64",
  "darwin-x64": "darwin-x64",
  "linux-x64": "linux-x64",
  "linux-arm64": "linux-arm64",
  "win32-x64": "win32-x64"
};

export function resolveServerJarPath(context: vscode.ExtensionContext, configured: string | undefined): string {
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

export function resolveJavaPath(context: vscode.ExtensionContext, configured: string | undefined): string {
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

export function resolveIli2GpkgJarPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  //console.log("**** " + configured);
  const override = configured?.trim();
  if (override) {
    return override;
  }

  const toolsDir = context.asAbsolutePath("ili2gpkg");
  //console.log("****2 " + toolsDir);

  const defaultJar = path.join(toolsDir, "ili2gpkg.jar");
  if (fs.existsSync(defaultJar)) {
    return defaultJar;
  }

  //console.log("****3 ");

  if (fs.existsSync(toolsDir)) {
    const jar = fs.readdirSync(toolsDir)
      .filter(file => file.toLowerCase().endsWith(".jar"))
      .map(file => path.join(toolsDir, file))
      .sort()
      .pop();
    if (jar && fs.existsSync(jar)) {
      return jar;
    }
  }

  const message = "ili2gpkg JAR not found. Configure `interlisLsp.ili2gpkgJarPath` in the extension settings.";
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}
