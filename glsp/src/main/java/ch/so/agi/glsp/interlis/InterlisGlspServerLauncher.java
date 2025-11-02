package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.launch.SocketGLSPServerLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the INTERLIS GLSP server and exposes it over a websocket endpoint that can be
 * consumed by the VS Code extension.
 */
public class InterlisGlspServerLauncher extends SocketGLSPServerLauncher {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5050;
    private static final Logger LOGGER = LoggerFactory.getLogger(InterlisGlspServerLauncher.class);

    /**
     * Creates a launcher that wires the {@link InterlisDiagramModule} into the GLSP runtime.
     */
    public InterlisGlspServerLauncher() {
        super(new InterlisGlspServerModule());
    }

    /**
     * Starts the GLSP server using default host/port values. The command line parameters follow
     * the GLSP conventions: <code>--host</code> and <code>--port</code> may be used to override
     * the defaults.
     *
     * @param args optional CLI arguments
     */
    public static void main(final String[] args) {
        final InterlisGlspServerLauncher launcher = new InterlisGlspServerLauncher();
        try {
            launcher.start(DEFAULT_HOST, DEFAULT_PORT);
        } catch (final Exception exception) {
            LOGGER.error("Failed to start INTERLIS GLSP server on {}:{}", DEFAULT_HOST, DEFAULT_PORT, exception);
            System.exit(1);
        }
    }
}
