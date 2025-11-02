import 'reflect-metadata';
import { Container } from 'inversify';
import { GLSPStarter, VSCODE_DEFAULT_MODULE_CONFIG } from '@eclipse-glsp/vscode-integration-webview';
import { initializeDiagramContainer, type ContainerConfiguration } from '@eclipse-glsp/client';
import { interlisDiagramModule } from './interlisDiagramModule';

class InterlisGlspWebviewStarter extends GLSPStarter {
  protected createContainer(...containerConfiguration: ContainerConfiguration): Container {
    const container = new Container();
    initializeDiagramContainer(container, ...containerConfiguration);
    return container;
  }

  protected getContainerConfiguration(): ContainerConfiguration {
    return [VSCODE_DEFAULT_MODULE_CONFIG, interlisDiagramModule];
  }
}

new InterlisGlspWebviewStarter();
