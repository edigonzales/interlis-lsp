package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.diagram.DiagramConfiguration;
import org.eclipse.glsp.server.di.DiagramModule;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;

final class InterlisGlspDiagramModule extends DiagramModule {
    private final ch.so.agi.lsp.interlis.InterlisLanguageServer languageServer;

    InterlisGlspDiagramModule(ch.so.agi.lsp.interlis.InterlisLanguageServer languageServer) {
        this.languageServer = languageServer;
    }

    @Override
    protected void configureAdditionals() {
        super.configureAdditionals();
        bind(ch.so.agi.lsp.interlis.InterlisLanguageServer.class).toInstance(languageServer);
    }

    @Override
    protected Class<? extends DiagramConfiguration> bindDiagramConfiguration() {
        return InterlisGlspDiagramConfiguration.class;
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
    public String getDiagramType() {
        return InterlisGlspConstants.DIAGRAM_TYPE;
    }
}
