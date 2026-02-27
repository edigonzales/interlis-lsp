package ch.so.agi.lsp.interlis.glsp;

import org.apache.logging.log4j.Level;
import org.eclipse.glsp.server.di.ServerModule;
import org.eclipse.glsp.server.websocket.GLSPConfigurator;
import org.eclipse.glsp.server.websocket.GLSPServerEndpoint;
import org.eclipse.glsp.server.websocket.WebsocketServerLauncher;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;

import java.net.InetSocketAddress;

import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Equivalent to {@link WebsocketServerLauncher} but without writing startup
 * messages to stdout. This is required when the JVM process also serves
 * stdio-based LSP JSON-RPC on stdout.
 */
public class QuietWebsocketServerLauncher extends WebsocketServerLauncher {
    public QuietWebsocketServerLauncher(ServerModule serverModule, String endpointPath, Level websocketLogLevel) {
        super(serverModule, endpointPath, websocketLogLevel);
    }

    @Override
    public void start(String host, int port) {
        try {
            server = new org.eclipse.jetty.server.Server(new InetSocketAddress(host, port));
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            JakartaWebSocketServletContainerInitializer.configure(context, (servletContext, container) -> {
                ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(
                        GLSPServerEndpoint.class,
                        "/" + endpointPath);
                builder.configurator(new GLSPConfigurator(this::createInjector));
                container.addEndpoint(builder.build());
            });

            server.setHandler(context);
            server.start();

            // Never write to stdout here: stdout is reserved for LSP JSON-RPC.
            LOGGER.info("GLSP websocket server started at {}{}",
                    server.getURI(),
                    "/" + endpointPath);

            server.join();
        } catch (Exception ex) {
            LOGGER.error("GLSP websocket launcher failed: {}", ex.getMessage(), ex);
        }
    }
}
