package ch.so.agi.glsp.interlis;

import org.eclipse.glsp.server.di.ServerModule;
import org.eclipse.glsp.server.launch.DefaultCLIParser;
import org.eclipse.glsp.server.launch.SocketGLSPServerLauncher;
import org.eclipse.glsp.server.utils.LaunchUtil;

/**
 * Minimal launcher for the INTERLIS GLSP server.
 */
public final class InterlisGlspServerLauncher {
    private InterlisGlspServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        DefaultCLIParser cli = new DefaultCLIParser(args, "interlis-glsp");
        LaunchUtil.configure(cli);

        if (cli.isHelp()) {
            return;
        }

        LaunchUtil.configureLogger(cli.isConsoleLog(), cli.parseLogDir(), cli.parseLogLevel());

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
