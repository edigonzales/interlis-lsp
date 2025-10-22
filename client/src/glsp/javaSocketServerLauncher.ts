import * as childProcess from "child_process";
import {
  GlspSocketServerLauncher,
  SocketServerLauncherOptions
} from "@eclipse-glsp/vscode-integration/node";

export interface JavaSocketServerLauncherOptions extends SocketServerLauncherOptions {
  readonly javaCommand: string;
}

export class JavaSocketServerLauncher extends GlspSocketServerLauncher {
  private readonly javaCommand: string;

  constructor(options: JavaSocketServerLauncherOptions) {
    const { javaCommand, ...baseOptions } = options;
    super(baseOptions);
    this.javaCommand = javaCommand;
  }

  protected startJavaProcess(): childProcess.ChildProcessWithoutNullStreams {
    if (!this.options.executable.endsWith("jar")) {
      throw new Error(
        `Could not launch Java GLSP server. The given executable is no JAR: ${this.options.executable}`
      );
    }

    const args = [
      "-jar",
      this.options.executable,
      "--port",
      `${this.options.socketConnectionOptions.port}`,
      "--host",
      `${this.options.socketConnectionOptions.host ?? "127.0.0.1"}`,
      ...this.options.additionalArgs
    ];

    if (this.options.socketConnectionOptions.host) {
      args.push("--host", `${this.options.socketConnectionOptions.host}`);
    }

    return childProcess.spawn(this.javaCommand, args);
  }
}
