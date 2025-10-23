import 'reflect-metadata';
import { Container, ContainerConfiguration } from 'inversify';
import { GLSPStarter, VSCODE_DEFAULT_MODULE_CONFIG } from '@eclipse-glsp/vscode-integration-webview';
import { initializeDiagramContainer } from '@eclipse-glsp/client';

class InterlisGlspWebviewStarter extends GLSPStarter {
  protected createContainer(...containerConfiguration: ContainerConfiguration): Container {
    const container = new Container();
    initializeDiagramContainer(container, ...containerConfiguration);
    return container;
  }

  protected getContainerConfiguration(): ContainerConfiguration {
    return [VSCODE_DEFAULT_MODULE_CONFIG];
  }
}

new InterlisGlspWebviewStarter();
