package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.di.ServerModule;

/**
 * Registers the INTERLIS diagram module with the GLSP server runtime.
 */
final class InterlisServerModule extends ServerModule {
    InterlisServerModule() {
        configureDiagramModule(new InterlisDiagramModule());
    }
}
