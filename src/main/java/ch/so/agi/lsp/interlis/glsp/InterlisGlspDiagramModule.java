package ch.so.agi.lsp.interlis.glsp;

import org.eclipse.glsp.server.di.MultiBinding;
import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.gmodel.GModelDiagramModule;
import org.eclipse.glsp.server.operations.OperationHandler;

public class InterlisGlspDiagramModule extends GModelDiagramModule {
    @Override
    public String getDiagramType() {
        return InterlisGlspConstants.DIAGRAM_TYPE;
    }

    @Override
    protected Class<? extends DiagramConfiguration> bindDiagramConfiguration() {
        return InterlisGlspDiagramConfiguration.class;
    }

    @Override
    protected Class<? extends SourceModelStorage> bindSourceModelStorage() {
        return InterlisGlspSourceModelStorage.class;
    }

    @Override
    protected Class<? extends GModelFactory> bindGModelFactory() {
        return InterlisGlspModelFactory.class;
    }

    @Override
    protected void configureOperationHandlers(MultiBinding<OperationHandler<?>> operationHandlers) {
        // Read-only diagram for now.
    }
}
