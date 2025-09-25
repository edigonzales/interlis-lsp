package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.ExecutionException;

public class LspServerLauncher {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        InterlisLanguageServer server = new InterlisLanguageServer();
        Launcher<LanguageClient> launcher = Launcher.createLauncher(
                server, LanguageClient.class, System.in, System.out);

        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
