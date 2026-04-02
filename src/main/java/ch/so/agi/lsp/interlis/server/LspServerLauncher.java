package ch.so.agi.lsp.interlis.server;

import ch.so.agi.lsp.interlis.glsp.InterlisGlspBridge;
import ch.so.agi.lsp.interlis.glsp.InterlisGlspRuntime;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.concurrent.ExecutionException;

public class LspServerLauncher {
    private static final String REMOTE_ENDPOINT_LOGGER = "org.eclipse.lsp4j.jsonrpc.RemoteEndpoint";
    private static final String UNMATCHED_CANCEL_PREFIX = "Unmatched cancel notification for request id ";

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        quietLsp4jCancellationWarnings();
        // Keep the original stdout exclusively for LSP JSON-RPC messages.
        PrintStream lspProtocolOut = System.out;
        // Redirect all incidental System.out prints from third-party libs away from the LSP transport.
        System.setOut(System.err);
        Logger log = LoggerFactory.getLogger(LspServerLauncher.class);

        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisGlspBridge.bindLanguageServer(server);

        InterlisGlspRuntime glspRuntime = null;
        try {
            glspRuntime = InterlisGlspRuntime.startEmbedded();
            server.setGlspEndpoint(glspRuntime.endpoint());
            log.info("Embedded GLSP server started on {}:{}{}",
                    server.getGlspEndpoint().getHost(),
                    server.getGlspEndpoint().getPort(),
                    "/" + server.getGlspEndpoint().getPath());
        } catch (Throwable ex) {
            log.error("Failed to start embedded GLSP server. Diagram editor features will be unavailable.", ex);
        }

        Launcher<InterlisLanguageClient> launcher = Launcher.createLauncher(
                server, InterlisLanguageClient.class, System.in, lspProtocolOut);

        server.connect(launcher.getRemoteProxy());
        try {
            launcher.startListening().get();
        } finally {
            if (glspRuntime != null) {
                glspRuntime.shutdown();
            }
            InterlisGlspBridge.clear();
        }
    }

    private static void quietLsp4jCancellationWarnings() {
        java.util.logging.Logger remoteEndpointLogger = java.util.logging.Logger.getLogger(REMOTE_ENDPOINT_LOGGER);
        remoteEndpointLogger.setLevel(Level.SEVERE);

        Filter suppressUnmatchedCancel = record -> !isUnmatchedCancelWarning(record);
        remoteEndpointLogger.setFilter(mergeFilter(remoteEndpointLogger.getFilter(), suppressUnmatchedCancel));

        for (Handler handler : java.util.logging.Logger.getLogger("").getHandlers()) {
            handler.setFilter(mergeFilter(handler.getFilter(), suppressUnmatchedCancel));
        }
    }

    private static boolean isUnmatchedCancelWarning(LogRecord record) {
        if (record == null) {
            return false;
        }

        String message = record.getMessage();
        if (message == null || !message.startsWith(UNMATCHED_CANCEL_PREFIX)) {
            return false;
        }

        String loggerName = record.getLoggerName();
        return REMOTE_ENDPOINT_LOGGER.equals(loggerName);
    }

    private static Filter mergeFilter(Filter existing, Filter additional) {
        if (existing == null) {
            return additional;
        }
        if (additional == null) {
            return existing;
        }
        return record -> existing.isLoggable(record) && additional.isLoggable(record);
    }
}
