package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.di.ServerModule;

/**
 * GLSP server module that installs the INTERLIS-specific diagram bindings.
 * This is the bridge between the generic GLSP launcher infrastructure and the
 * {@link InterlisDiagramModule}, ensuring that every client connection is
 * served with INTERLIS diagram services.
 */
final class InterlisServerModule extends ServerModule {
    InterlisServerModule() {
        configureDiagramModule(new InterlisDiagramModule());
    }
}
