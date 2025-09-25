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
}
