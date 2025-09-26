package ch.so.agi.lsp.interlis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InterlisValidatorTest {

    @Test
    void returnsLogAndMessages() {
        ClientSettings cfg = new ClientSettings();
        cfg.setModelRepositories("https://models.example.org,/opt/models");

        Ili2cUtil v = new Ili2cUtil(cfg);
        Ili2cUtil.CompilationOutcome out = v.compile("/tmp/example.ili");

        assertNotNull(out.getLogText());
        assertFalse(out.getLogText().isEmpty());
        assertNotNull(out.getMessages());
        assertFalse(out.getMessages().isEmpty());
    }
    
    @Test
    void clientSettingsParsesCommaSeparatedRepos() {
        ClientSettings cfg = new ClientSettings();
        cfg.setModelRepositories("  https://a.example,  /b/c  ,  ");
        var repos = cfg.getModelRepositoriesList();
        assertEquals(2, repos.size());
        assertEquals("https://a.example", repos.get(0));
        assertEquals("/b/c", repos.get(1));
    }

}
