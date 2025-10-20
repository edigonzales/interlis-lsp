package ch.so.agi.glsp.interlis;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.glsp.server.di.ServerModule;
import org.eclipse.glsp.server.websocket.WebsocketServerLauncher;

/**
 * Thin wrapper around {@link WebsocketServerLauncher} that runs the Jetty server
 * on a background thread so the caller does not block forever on {@link #start}.
 */
final class EmbeddedWebsocketServerLauncher extends WebsocketServerLauncher {
    private volatile CompletableFuture<Void> runner;

    EmbeddedWebsocketServerLauncher(ServerModule serverModule, String endpointPath) {
        super(Objects.requireNonNull(serverModule, "serverModule"),
                Objects.requireNonNull(endpointPath, "endpointPath"));
    }

    synchronized void startBackground(String host, int port) {
        if (runner != null && !runner.isDone()) {
            return;
        }
        runner = CompletableFuture.runAsync(() -> super.start(host, port));
    }

    synchronized void stopBackground() {
        super.shutdown();
        if (runner != null && !runner.isDone()) {
            runner.cancel(true);
        }
        runner = null;
    }

    boolean isRunning() {
        return server != null && server.isRunning();
    }
}
