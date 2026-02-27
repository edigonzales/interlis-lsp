package ch.so.agi.lsp.interlis.glsp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.elk.core.options.EdgeRouting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;

class InterlisElkLayoutEngineTest {
    private final ExposedInterlisElkLayoutEngine engine = new ExposedInterlisElkLayoutEngine();

    @AfterEach
    void cleanup() {
        System.clearProperty(InterlisElkLayoutEngine.EDGE_ROUTING_PROPERTY);
        InterlisGlspBridge.clear();
    }

    @Test
    void resolveEdgeRoutingUsesClientSettingsBeforeSystemProperty() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setEdgeRouting("POLYLINE");
        server.setClientSettings(settings);
        InterlisGlspBridge.bindLanguageServer(server);
        System.setProperty(InterlisElkLayoutEngine.EDGE_ROUTING_PROPERTY, "SPLINES");

        assertEquals(EdgeRouting.POLYLINE, engine.exposedResolveEdgeRouting());
    }

    @Test
    void resolveEdgeRoutingFallsBackToSystemProperty() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisGlspBridge.bindLanguageServer(server);
        System.setProperty(InterlisElkLayoutEngine.EDGE_ROUTING_PROPERTY, "SPLINES");

        assertEquals(EdgeRouting.SPLINES, engine.exposedResolveEdgeRouting());
    }

    @Test
    void resolveEdgeRoutingFallsBackToDefaultForInvalidValue() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setEdgeRouting("not-a-routing");
        server.setClientSettings(settings);
        InterlisGlspBridge.bindLanguageServer(server);

        assertEquals(EdgeRouting.ORTHOGONAL, engine.exposedResolveEdgeRouting());
    }

    private static final class ExposedInterlisElkLayoutEngine extends InterlisElkLayoutEngine {
        EdgeRouting exposedResolveEdgeRouting() {
            return resolveEdgeRouting();
        }
    }
}
