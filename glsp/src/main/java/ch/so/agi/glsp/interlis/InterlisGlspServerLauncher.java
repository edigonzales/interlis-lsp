package ch.so.agi.glsp.interlis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.glsp.server.di.ServerModule;
import org.eclipse.glsp.server.launch.DefaultCLIParser;
import org.eclipse.glsp.server.launch.SocketGLSPServerLauncher;
import org.eclipse.glsp.server.utils.LaunchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point that boots the INTERLIS GLSP server when the VS Code extension
 * spawns the Java process. The launcher wires the {@link InterlisServerModule}
 * into the GLSP runtime and forwards the socket configuration parsed from the
 * command line arguments.
 */
public final class InterlisGlspServerLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGlspServerLauncher.class);

    private InterlisGlspServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        DefaultCLIParser cli = new DefaultCLIParser(args, "interlis-glsp");
        LaunchUtil.configure(cli);

        if (cli.isHelp()) {
            return;
        }

        Path logDir = prepareLogDirectory(cli.parseLogDir());

        LaunchUtil.configureLogger(cli.isConsoleLog(), logDir != null ? logDir.toString() : null,
                cli.parseLogLevel());

        int port = cli.parsePort();
        String host = cli.parseHostname();
        if (!LaunchUtil.isValidPort(port)) {
            throw new IllegalArgumentException("Invalid GLSP server port: " + port);
        }

        ServerModule module = new InterlisServerModule();
        SocketGLSPServerLauncher launcher = new SocketGLSPServerLauncher(module);
        launcher.start(host, port);
    }

    private static Path prepareLogDirectory(String requestedPath) {
        Path candidate = null;
        if (requestedPath != null && !requestedPath.isBlank()) {
            candidate = Path.of(requestedPath);
            try {
                Files.createDirectories(candidate);
            } catch (IOException ex) {
                LOG.info("Unable to use requested GLSP log directory {} ({}); falling back to a temporary location.",
                        candidate, ex.getMessage());
                candidate = null;
            }
        }

        if (candidate == null) {
            Path fallback = Path.of(System.getProperty("java.io.tmpdir"), "interlis-glsp", "logs");
            try {
                Files.createDirectories(fallback);
                candidate = fallback;
            } catch (IOException ex) {
                LOG.warn("Falling back to console logging only; unable to create GLSP log directory {}: {}", fallback,
                        ex.getMessage());
                candidate = null;
            }
        }

        if (candidate != null) {
            LOG.info("Writing GLSP logs to {}", candidate.toAbsolutePath());
        }
        return candidate;
    }
}
