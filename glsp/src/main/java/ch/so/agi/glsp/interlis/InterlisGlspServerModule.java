package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.di.DiagramModule;
import org.eclipse.glsp.server.di.ServerModule;

/**
 * Registers the INTERLIS diagram module with the GLSP server injector.
 */
public class InterlisGlspServerModule extends ServerModule {

    /**
     * Creates a module that exposes the INTERLIS diagram module to the GLSP runtime.
     */
    public InterlisGlspServerModule() {
        configureDiagramModule(createDiagramModule());
    }

    private DiagramModule createDiagramModule() {
        return new InterlisDiagramModule();
    }
}
