import 'reflect-metadata';

import {
    ConsoleLogger,
    ContainerConfiguration,
    DefaultTypes,
    LogLevel,
    TYPES,
    configureDefaultModelElements,
    configureModelElement,
    editLabelFeature,
    GLabel,
    GLabelView,
    initializeDiagramContainer
} from '@eclipse-glsp/client';
import { GLSPStarter } from '@eclipse-glsp/vscode-integration-webview';
import { Container, ContainerModule } from 'inversify';

class InterlisStarter extends GLSPStarter {
    createContainer(...configs: ContainerConfiguration): Container {
        return initializeDiagramContainer(
            new Container(),
            new ContainerModule((bind, unbind, isBound, rebind) => {
                const ctx = { bind, unbind, isBound, rebind };
                configureDefaultModelElements(ctx);
                configureModelElement(ctx, DefaultTypes.LABEL, GLabel, GLabelView, { enable: [editLabelFeature] });
                rebind(TYPES.ILogger).to(ConsoleLogger).inSingletonScope();
                rebind(TYPES.LogLevel).toConstantValue(LogLevel.warn);
            }),
            ...configs
        );
    }
}

new InterlisStarter();
