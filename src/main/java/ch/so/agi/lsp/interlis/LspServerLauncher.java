package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.ExecutionException;

public class LspServerLauncher {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        InterlisLanguageServer server = new InterlisLanguageServer();
        // Start the embedded GLSP endpoint as early as possible so clients can
        // connect before the LSP handshake finishes. The initializer still
        // guards startup for in-process tests that instantiate the server
        // directly without going through this launcher.
        server.getGlspServer().startAsync();
        
        Launcher<InterlisLanguageClient> launcher = Launcher.createLauncher(
                server, InterlisLanguageClient.class, System.in, System.out);

        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
