package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.di.ServerModule;

import ch.so.agi.lsp.interlis.InterlisLanguageServer;

final class InterlisGlspServerModule extends ServerModule {
    private final InterlisLanguageServer languageServer;

    InterlisGlspServerModule(InterlisLanguageServer languageServer) {
        this.languageServer = languageServer;
    }

    @Override
    protected void configureAdditionals() {
        super.configureAdditionals();
        bind(InterlisLanguageServer.class).toInstance(languageServer);
    }
}
