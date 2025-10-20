package ch.so.agi.glsp.interlis;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.glsp.server.di.DiagramModule;
import org.eclipse.glsp.server.di.ServerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.so.agi.lsp.interlis.InterlisLanguageServer;

public final class InterlisGlspServer {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGlspServer.class);

    private final InterlisLanguageServer languageServer;
    private final String host;
    private final int port;
    private final EmbeddedWebsocketServerLauncher launcher;

    private volatile CompletableFuture<Boolean> startFuture;
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
        this.launcher = new EmbeddedWebsocketServerLauncher(serverModule, InterlisGlspConstants.WEBSOCKET_PATH);
    }

    public synchronized CompletableFuture<Boolean> startAsync() {
        if (started) {
            return CompletableFuture.completedFuture(true);
        }
        if (startFuture != null && !startFuture.isDone()) {
            return startFuture;
        }

        launcher.startBackground(host, port);

        startFuture = CompletableFuture.supplyAsync(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (launcher.isRunning()) {
                    synchronized (InterlisGlspServer.this) {
                        if (!started) {
                            started = true;
                            LOG.info("Started INTERLIS GLSP server on {}:{}{}", host, port, pathWithSlash());
                            languageServer.logToClient(String.format(
                                    "INTERLIS GLSP server listening on ws://%s:%d%s\n", host, port, pathWithSlash()));
                        }
                    }
                    return true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            LOG.error("Timed out waiting for INTERLIS GLSP server to report running state");
            languageServer.logToClient("Timed out starting INTERLIS GLSP server\n");
            synchronized (InterlisGlspServer.this) {
                started = false;
            }
            return false;
        });

        return startFuture;
    }

    public void start() {
        startAsync();
    }

    public synchronized void stop() {
        if (!started) {
            launcher.stopBackground();
            if (startFuture != null && !startFuture.isDone()) {
                startFuture.cancel(true);
            }
            startFuture = null;
            return;
        }
        try {
            launcher.stopBackground();
        } finally {
            started = false;
            if (startFuture != null && !startFuture.isDone()) {
                startFuture.cancel(true);
            }
            startFuture = null;
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

    public CompletableFuture<Boolean> getStartFuture() {
        return startFuture;
    }

    private String pathWithSlash() {
        String path = InterlisGlspConstants.WEBSOCKET_PATH;
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
