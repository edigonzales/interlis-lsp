import "reflect-metadata";

import { Container } from "inversify";
import { ContainerConfiguration, initializeDiagramContainer } from "@eclipse-glsp/client";
import { GLSPStarter } from "@eclipse-glsp/vscode-integration-webview";

import { interlisDiagramModule } from "./interlisDiagramModule";

class InterlisDiagramStarter extends GLSPStarter {
  protected override createContainer(...containerConfiguration: ContainerConfiguration): Container {
    const container = new Container();
    return initializeDiagramContainer(container, ...containerConfiguration, interlisDiagramModule);
  }
}

new InterlisDiagramStarter();
