import type { Disposable, GLSPClient } from "@eclipse-glsp/protocol";
import {
  InitializeNotification,
  WebviewEndpoint,
  WebviewReadyNotification,
  type WebviewEndpointOptions
} from "@eclipse-glsp/vscode-integration/node";

export type ReloadableWebviewEndpointHooks = {
  onWebviewReady?: () => void;
  onIdentifierSent?: (phase: "initial" | "reload") => void;
};

/**
 * Keeps the host-side GLSP endpoint alive while a VS Code webview reloads.
 *
 * The stock WebviewEndpoint resolves its ready promise only once and sends
 * the diagram identifier only from initialize(). A webview moved to another
 * VS Code window sends ready again, so the identifier has to be sent for each
 * subsequent ready notification as well.
 */
export class ReloadableWebviewEndpoint extends WebviewEndpoint {
  private endpointInitialized = false;
  private readyBeforeInitialize = false;
  private identifierSent = false;

  constructor(
    options: WebviewEndpointOptions,
    private readonly hooks: ReloadableWebviewEndpointHooks = {}
  ) {
    super(options);

    this.toDispose.push(
      this.messenger.onNotification(
        WebviewReadyNotification,
        () => {
          this.hooks.onWebviewReady?.();

          if (!this.endpointInitialized) {
            this.readyBeforeInitialize = true;
            return;
          }

          this.sendIdentifier();
        },
        { sender: this.messageParticipant }
      )
    );
  }

  override initialize(glspClient: GLSPClient): Disposable {
    // The base implementation installs all request/notification handlers and
    // invokes sendDiagramIdentifier(). That method is overridden below so the
    // ready listener remains the single sender and cannot duplicate the first
    // identifier.
    const disposable = super.initialize(glspClient);
    this.endpointInitialized = true;

    if (this.readyBeforeInitialize) {
      this.readyBeforeInitialize = false;
      this.sendIdentifier();
    }

    return disposable;
  }

  protected override async sendDiagramIdentifier(): Promise<void> {
    // Identifier delivery is coordinated by the ready listener above. This
    // prevents the one-shot base implementation from sending a duplicate
    // identifier during initialize().
  }

  private sendIdentifier(): void {
    const phase = this.identifierSent ? "reload" : "initial";
    this.identifierSent = true;
    this.messenger.sendNotification(
      InitializeNotification,
      this.messageParticipant,
      this.diagramIdentifier
    );
    this.hooks.onIdentifierSent?.(phase);
  }
}
