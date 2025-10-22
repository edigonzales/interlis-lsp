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

        String logDirSetting = cli.parseLogDir();
        Path logDir = null;
        if (logDirSetting != null && !logDirSetting.isBlank()) {
            logDir = Path.of(logDirSetting);
            try {
                Files.createDirectories(logDir);
            } catch (IOException ex) {
                LOG.warn("Failed to create GLSP log directory {}: {}", logDir, ex.getMessage());
                logDir = null;
            }
        }

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
}
