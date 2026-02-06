package ch.so.agi.lsp.interlis.server;

import ch.so.agi.lsp.interlis.glsp.InterlisGlspBridge;
import ch.so.agi.lsp.interlis.glsp.InterlisGlspRuntime;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.ExecutionException;

public class LspServerLauncher {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
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
}
