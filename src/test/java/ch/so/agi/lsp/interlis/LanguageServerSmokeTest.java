package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.workspace.CommandHandlers;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LanguageServerSmokeTest {

    @Test
    void validateCommandReturnsLog() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);
        Object result = handlers.compile("file:///tmp/example.ili").get();
        assertTrue(result instanceof String);
        String log = (String) result;
        //assertTrue(log.contains("ERROR:"));
    }
    
    @Test
    void validateUsesServerClientSettings() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings cfg = new ClientSettings();
        cfg.setModelRepositories("https://models.example.org");
        server.setClientSettings(cfg);

        CommandHandlers handlers = new CommandHandlers(server);
        Object result = handlers.compile("file:///tmp/example.ili").get();

        assertTrue(result instanceof String);
        //assertTrue(((String) result).contains("ERROR:"));
    }

    @Test
    void initializeAdvertisesReferencesAndPrepareRename() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        InitializeResult result = server.initialize(new InitializeParams()).get();

        assertNotNull(result.getCapabilities().getReferencesProvider());
        assertTrue(result.getCapabilities().getReferencesProvider().getLeft());
        assertNotNull(result.getCapabilities().getCompletionProvider());
        assertEquals(java.util.List.of(".", ":", " "),
                result.getCapabilities().getCompletionProvider().getTriggerCharacters());
        assertNotNull(result.getCapabilities().getRenameProvider());
        assertTrue(result.getCapabilities().getRenameProvider().isRight());
        assertTrue(result.getCapabilities().getRenameProvider().getRight().getPrepareProvider());
    }
}
