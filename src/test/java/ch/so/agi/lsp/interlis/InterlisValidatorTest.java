package ch.so.agi.lsp.interlis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InterlisValidatorTest {

    @Test
    void returnsLogAndMessages() {
        InterlisValidator v = new InterlisValidator();
        InterlisValidator.ValidationOutcome out = v.validate("/tmp/example.ili");
        assertNotNull(out.getLogText());
        assertFalse(out.getLogText().isEmpty());
        assertNotNull(out.getMessages());
        assertFalse(out.getMessages().isEmpty());
    }
}
