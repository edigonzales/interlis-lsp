package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.di.DiagramModule;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.eclipse.glsp.server.model.GModelState;

/**
 * Configures the GLSP services that build and maintain the INTERLIS class diagram.
 */
public class InterlisDiagramModule extends DiagramModule {

    @Override
    protected Class<? extends DiagramConfiguration> bindDiagramConfiguration() {
        return InterlisDiagramConfiguration.class;
    }

    @Override
    protected Class<? extends SourceModelStorage> bindSourceModelStorage() {
        return InterlisSourceModelStorage.class;
    }

    @Override
    protected Class<? extends GModelFactory> bindGModelFactory() {
        return InterlisGModelFactory.class;
    }

    @Override
    protected Class<? extends GModelState> bindGModelState() {
        return DefaultGModelState.class;
    }

    @Override
    public String getDiagramType() {
        return InterlisGlspTypes.DIAGRAM_TYPE;
    }
}
