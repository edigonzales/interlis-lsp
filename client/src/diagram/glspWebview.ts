import "reflect-metadata";
import { baseViewModule, ContainerConfiguration, initializeDiagramContainer } from "@eclipse-glsp/client";
import { GLSPStarter } from "@eclipse-glsp/vscode-integration-webview";
import { Container } from "inversify";

class InterlisGlspWebviewStarter extends GLSPStarter {
  protected override createContainer(...containerConfiguration: ContainerConfiguration): Container {
    return initializeDiagramContainer(
      new Container(),
      ...containerConfiguration,
      { add: [baseViewModule] }
    );
  }
}

new InterlisGlspWebviewStarter();
