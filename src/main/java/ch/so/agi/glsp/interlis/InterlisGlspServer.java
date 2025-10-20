package ch.so.agi.glsp.interlis;

import java.util.Objects;

import org.eclipse.glsp.server.di.DiagramModule;
import org.eclipse.glsp.server.di.ServerModule;
import org.eclipse.glsp.server.websocket.WebsocketServerLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.so.agi.lsp.interlis.InterlisLanguageServer;

public final class InterlisGlspServer {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGlspServer.class);

    private final InterlisLanguageServer languageServer;
    private final String host;
    private final int port;
    private final WebsocketServerLauncher launcher;

    private volatile boolean started;

    public InterlisGlspServer(InterlisLanguageServer languageServer) {
        this.languageServer = Objects.requireNonNull(languageServer, "languageServer");
        this.host = System.getProperty("interlis.glsp.host", "127.0.0.1");
        int configuredPort = Integer.getInteger("interlis.glsp.port", 7057);
        if (configuredPort <= 0 || configuredPort > 65535) {
            LOG.warn("Configured interlis.glsp.port={} out of range, using default 7057", configuredPort);
            configuredPort = 7057;
        }
        this.port = configuredPort;

        DiagramModule diagramModule = new InterlisGlspDiagramModule(languageServer);
        ServerModule serverModule = new InterlisGlspServerModule(languageServer);
        serverModule.configureDiagramModule(diagramModule);
        this.launcher = new WebsocketServerLauncher(serverModule, InterlisGlspConstants.WEBSOCKET_PATH);
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            launcher.start(host, port);
            started = true;
            LOG.info("Started INTERLIS GLSP server on {}:{}{}", host, port, pathWithSlash());
        } catch (RuntimeException ex) {
            started = false;
            LOG.error("Failed to start INTERLIS GLSP server", ex);
            languageServer.logToClient("Failed to start INTERLIS GLSP server: " + ex.getMessage() + "\n");
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            launcher.shutdown();
        } finally {
            started = false;
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getEndpointPath() {
        return InterlisGlspConstants.WEBSOCKET_PATH;
    }

    public boolean isStarted() {
        return started;
    }

    private String pathWithSlash() {
        String path = InterlisGlspConstants.WEBSOCKET_PATH;
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
