import * as vscode from "vscode";
import {
  BaseJsonrpcGLSPClient,
  ClientState,
  createWebSocketConnection,
  type ActionMessage,
  type ActionMessageHandler,
  type DisposeClientSessionParameters,
  type GLSPClient,
  type InitializeClientSessionParameters,
  type InitializeParameters,
  type InitializeResult
} from "@eclipse-glsp/protocol";
import {
  SocketGlspVscodeServer,
  type SocketGlspVscodeServerOptions
} from "@eclipse-glsp/vscode-integration/node";
import type { MessageConnection } from "vscode-jsonrpc";
import { WebSocket } from "ws";

export type GlspTransportLogger = (message: string) => void;

/**
 * SocketGlspVscodeServer treats a closed connection as terminal. The stable
 * client below lets the VS Code connector and already initialized webviews
 * survive while the underlying JSON-RPC client is replaced.
 */
export class RecoverableGlspVscodeServer extends SocketGlspVscodeServer {
  private readonly stableClient: RecoverableGlspClient;
  private startPromise: Promise<void> | undefined;

  constructor(
    options: SocketGlspVscodeServerOptions,
    private readonly log: GlspTransportLogger = () => undefined
  ) {
    super(options);
    this.stableClient = new RecoverableGlspClient(
      () => this.start(),
      this.log
    );
  }

  override async createGLSPClient(): Promise<BaseJsonrpcGLSPClient> {
    const client = await super.createGLSPClient();
    this.stableClient.attach(client);
    return client;
  }

  override get glspClient(): Promise<GLSPClient> {
    return Promise.resolve(this.stableClient);
  }

  override get initializeResult(): Promise<InitializeResult> {
    return this.onReady.then(() => {
      const result = this.stableClient.initializeResult;
      if (!result) {
        throw new Error("GLSP server did not return initialization data.");
      }
      return result;
    });
  }

  override async start(): Promise<void> {
    if (this.startPromise) {
      return this.startPromise;
    }

    const current = this.stableClient.currentState;
    if (current === ClientState.Running) {
      return;
    }

    const promise = this.startUnderlyingClient();
    this.startPromise = promise;
    try {
      await promise;
    } finally {
      if (this.startPromise === promise) {
        this.startPromise = undefined;
      }
    }
  }

  override dispose(): void {
    this.stableClient.dispose();
    super.dispose();
  }

  protected override createWebSocketConnection(address: string): Promise<MessageConnection> {
    const webSocket = new WebSocket(address);
    let opened = false;

    webSocket.on("open", () => {
      opened = true;
      this.log(`GLSP_SOCKET event=open address=${address}`);
    });
    webSocket.on("error", (error: Error) => {
      this.log(`GLSP_SOCKET event=error address=${address} message=${formatError(error)}`);
      this.stableClient.notifyTransportFailure();
    });
    webSocket.on("close", (code: number, reason: Buffer) => {
      const closeReason = reason?.toString() || "<empty>";
      this.log(
        `GLSP_SOCKET event=close address=${address} code=${code} reason=${sanitizeLogValue(closeReason)} opened=${opened}`
      );
      this.stableClient.notifyTransportFailure();
    });

    return new Promise((resolve, reject) => {
      webSocket.once("open", () => {
        const socket = {
          send: (content: string) => webSocket.send(content),
          onMessage: (callback: (message: string) => void) => {
            webSocket.onmessage = (event: { data: unknown }) => callback(String(event.data));
          },
          onClose: (callback: (code: number, reason: string) => void) => {
            webSocket.onclose = (event: { code: number; reason: string }) => callback(event.code, event.reason);
          },
          onError: (callback: (error: Error) => void) => {
            webSocket.onerror = (event: { error?: unknown }) => {
              const error = "error" in event && event.error instanceof Error
                ? event.error
                : new Error("GLSP WebSocket connection failed.");
              callback(error);
            };
          },
          dispose: () => webSocket.close()
        };
        resolve(createWebSocketConnection(socket));
      });
      webSocket.once("error", (error: Error) => {
        if (!opened) {
          reject(error);
        }
      });
      webSocket.once("close", (code: number, reason: Buffer) => {
        if (!opened) {
          reject(new Error(`GLSP WebSocket closed before opening (code=${code}, reason=${reason?.toString() || "<empty>"}).`));
        }
      });
    });
  }

  private async startUnderlyingClient(): Promise<void> {
    await super.start();

    const state = this.stableClient.currentState;
    if (state !== ClientState.Running || !this.stableClient.initializeResult) {
      throw new Error(`GLSP transport could not be started (state=${ClientState[state] ?? state}).`);
    }

    this.log("GLSP_TRANSPORT event=running");
  }
}

class RecoverableGlspClient implements GLSPClient {
  readonly id = "interlis-vscode";

  private currentClient?: GLSPClient;
  private currentClientDisposables: vscode.Disposable[] = [];
  private _initializeResult: InitializeResult | undefined;
  private transportFailure = false;
  private disposed = false;
  private readonly actionMessageEmitter = new vscode.EventEmitter<ActionMessage>();
  private readonly currentStateEmitter = new vscode.EventEmitter<ClientState>();
  private readonly serverInitializedEmitter = new vscode.EventEmitter<InitializeResult>();

  constructor(
    private readonly restart: () => Promise<void>,
    private readonly log: GlspTransportLogger
  ) {}

  get currentState(): ClientState {
    return this.transportFailure
      ? ClientState.ServerError
      : this.currentClient?.currentState ?? ClientState.Initial;
  }

  get onCurrentStateChanged(): vscode.Event<ClientState> {
    return this.currentStateEmitter.event;
  }

  get initializeResult(): InitializeResult | undefined {
    return this._initializeResult ?? this.currentClient?.initializeResult;
  }

  get onServerInitialized(): vscode.Event<InitializeResult> {
    return this.serverInitializedEmitter.event;
  }

  attach(client: GLSPClient): void {
    if (this.disposed) {
      return;
    }
    this.currentClientDisposables.forEach(disposable => disposable.dispose());
    this.currentClientDisposables = [];
    this.currentClient = client;
    this.transportFailure = false;
    this._initializeResult = client.initializeResult;

    this.currentClientDisposables.push(
      client.onCurrentStateChanged(state => this.currentStateEmitter.fire(state)),
      client.onServerInitialized(result => {
        this._initializeResult = result;
        this.serverInitializedEmitter.fire(result);
      }),
      client.onActionMessage(message => this.actionMessageEmitter.fire(message))
    );

    this.currentStateEmitter.fire(client.currentState);
    this.log(`GLSP_TRANSPORT event=client-attached state=${ClientState[client.currentState] ?? client.currentState}`);
  }

  notifyTransportFailure(): void {
    if (this.disposed || this.transportFailure) {
      return;
    }
    this.transportFailure = true;
    this.currentStateEmitter.fire(ClientState.ServerError);
  }

  start(): Promise<void> {
    return this.restart();
  }

  async initializeServer(params: InitializeParameters): Promise<InitializeResult> {
    const client = await this.waitForRunningClient();
    const result = await client.initializeServer(params);
    this._initializeResult = result;
    return result;
  }

  async initializeClientSession(params: InitializeClientSessionParameters): Promise<void> {
    const client = await this.waitForRunningClient();
    await client.initializeClientSession(params);
  }

  async disposeClientSession(params: DisposeClientSessionParameters): Promise<void> {
    const client = this.currentClient;
    if (!client || client.currentState !== ClientState.Running) {
      return;
    }
    await client.disposeClientSession(params);
  }

  sendActionMessage(message: ActionMessage): void {
    const client = this.currentClient;
    if (!client || client.currentState !== ClientState.Running) {
      this.log(`GLSP_TRANSPORT action-dropped kind=${message.action.kind} state=${ClientState[this.currentState] ?? this.currentState}`);
      return;
    }
    client.sendActionMessage(message);
  }

  onActionMessage(handler: ActionMessageHandler, clientId?: string): vscode.Disposable {
    return this.actionMessageEmitter.event(message => {
      if (!clientId || message.clientId === clientId) {
        handler(message);
      }
    });
  }

  shutdownServer(): void {
    if (this.currentClient?.currentState === ClientState.Running) {
      this.currentClient.shutdownServer();
    }
  }

  async stop(): Promise<void> {
    await this.currentClient?.stop();
  }

  dispose(): void {
    this.disposed = true;
    this.currentClientDisposables.forEach(disposable => disposable.dispose());
    this.currentClientDisposables = [];
    this.actionMessageEmitter.dispose();
    this.currentStateEmitter.dispose();
    this.serverInitializedEmitter.dispose();
  }

  private async waitForRunningClient(): Promise<GLSPClient> {
    if (!this.currentClient || this.currentClient.currentState !== ClientState.Running) {
      await this.restart();
    }

    const client = this.currentClient;
    if (!client || client.currentState !== ClientState.Running) {
      throw new Error("GLSP transport is not running.");
    }
    return client;
  }
}

function formatError(error: unknown): string {
  if (error instanceof Error && error.message) {
    return sanitizeLogValue(error.message);
  }
  return sanitizeLogValue(String(error));
}

function sanitizeLogValue(value: string): string {
  return value.replace(/[\r\n\t ]+/g, "_");
}
