package ch.so.agi.lsp.interlis.glsp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import org.apache.logging.log4j.Level;
import org.eclipse.glsp.server.di.ServerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InterlisGlspRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGlspRuntime.class);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(120);

    private final String host;
    private final int port;
    private final String path;
    private final QuietWebsocketServerLauncher launcher;
    private final Thread launcherThread;

    private InterlisGlspRuntime(String host, int port, String path, QuietWebsocketServerLauncher launcher, Thread launcherThread) {
        this.host = host;
        this.port = port;
        this.path = path;
        this.launcher = launcher;
        this.launcherThread = launcherThread;
    }

    public static InterlisGlspRuntime startEmbedded() {
        String host = "127.0.0.1";
        int port = allocatePort();
        String path = InterlisGlspConstants.ENDPOINT_PATH;

        ServerModule serverModule = new ServerModule();
        serverModule.configureDiagramModule(new InterlisGlspDiagramModule());

        QuietWebsocketServerLauncher launcher = new QuietWebsocketServerLauncher(serverModule, path, Level.ERROR);
        Thread thread = new Thread(() -> {
            try {
                launcher.start(host, port);
            } catch (Exception ex) {
                LOG.error("Embedded GLSP launcher terminated with an error.", ex);
            }
        }, "interlis-glsp-websocket");
        thread.setDaemon(true);
        thread.start();

        InterlisGlspRuntime runtime = new InterlisGlspRuntime(host, port, path, launcher, thread);
        runtime.awaitReady();
        return runtime;
    }

    public void shutdown() {
        try {
            launcher.shutdown();
        } catch (Exception ex) {
            LOG.debug("Ignoring GLSP shutdown error.", ex);
        }

        try {
            launcherThread.join(800);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public GlspEndpoint endpoint() {
        return new GlspEndpoint(
                InterlisGlspConstants.ENDPOINT_PROTOCOL,
                host,
                port,
                path,
                InterlisGlspConstants.DIAGRAM_TYPE);
    }

    private void awaitReady() {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!launcherThread.isAlive()) {
                throw new IllegalStateException("Embedded GLSP launcher thread terminated during startup.");
            }
            if (isPortReachable(host, port)) {
                return;
            }
            sleep(POLL_INTERVAL);
        }
        throw new IllegalStateException("Timed out while waiting for embedded GLSP websocket startup.");
    }

    private static boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static int allocatePort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not allocate a free TCP port for embedded GLSP runtime.", ex);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for embedded GLSP startup.", interrupted);
        }
    }
}
