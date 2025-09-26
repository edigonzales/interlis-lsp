package ch.so.agi.lsp.interlis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LanguageServerSmokeTest {

    @Test
    void validateCommandReturnsLog() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);
        Object result = handlers.validate("file:///tmp/example.ili").get();
        assertTrue(result instanceof String);
        String log = (String) result;
        assertTrue(log.contains("ERROR:"));
    }
    
    @Test
    void validateUsesServerClientSettings() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings cfg = new ClientSettings();
        cfg.setModelRepositories("https://models.example.org");
        server.setClientSettings(cfg);

        CommandHandlers handlers = new CommandHandlers(server);
        Object result = handlers.validate("file:///tmp/example.ili").get();

        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("ERROR:"));
    }
}
