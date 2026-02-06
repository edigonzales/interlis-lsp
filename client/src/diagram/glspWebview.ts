import "reflect-metadata";
import { ContainerConfiguration, initializeDiagramContainer } from "@eclipse-glsp/client";
import { GLSPStarter } from "@eclipse-glsp/vscode-integration-webview";
import { Container } from "inversify";

class InterlisGlspWebviewStarter extends GLSPStarter {
  protected override createContainer(...containerConfiguration: ContainerConfiguration): Container {
    return initializeDiagramContainer(new Container(), ...containerConfiguration);
  }
}

new InterlisGlspWebviewStarter();
