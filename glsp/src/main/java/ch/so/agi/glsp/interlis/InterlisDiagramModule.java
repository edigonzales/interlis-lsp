package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.actions.ActionHandler;
import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.di.MultiBinding;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.gmodel.GModelDiagramModule;
import org.eclipse.glsp.server.operations.OperationHandler;

import ch.so.agi.glsp.interlis.model.InterlisGModelFactory;
import ch.so.agi.glsp.interlis.model.InterlisSourceModelStorage;

/**
 * Registers the INTERLIS-specific model storage, gmodel factory and diagram
 * configuration with the GLSP dependency injection container. The module is
 * referenced by {@link InterlisServerModule} so that every diagram session is
 * backed by our INTERLIS services.
 */
public final class InterlisDiagramModule extends GModelDiagramModule {
    public static final String DIAGRAM_TYPE = "interlis-uml-diagram";

    @Override
    public String getDiagramType() {
        return DIAGRAM_TYPE;
    }

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
    protected void configureActionHandlers(MultiBinding<ActionHandler> bindings) {
        super.configureActionHandlers(bindings);
    }

    @Override
    protected void configureOperationHandlers(MultiBinding<OperationHandler<?>> bindings) {
        super.configureOperationHandlers(bindings);
    }
}
