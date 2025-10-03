package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisWorkspaceServiceTest {
    private static Method coerceMethod;

    @BeforeAll
    static void lookupCoerceMethod() throws Exception {
        coerceMethod = InterlisWorkspaceService.class.getDeclaredMethod("coerceArgToString", Object.class);
        coerceMethod.setAccessible(true);
    }

    private static String coerce(Object value) {
        try {
            return (String) coerceMethod.invoke(null, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void coerceExtractsUriFromNestedMap() {
        Map<String, Object> nestedUri = Map.of(
                "scheme", "file",
                "path", "/tmp/example.ili");
        Map<String, Object> params = Map.of("uri", nestedUri);

        assertEquals("/tmp/example.ili", coerce(params));
    }

    @Test
    void coerceExtractsUriFromTextDocumentMap() {
        Map<String, Object> params = Map.of(
                "textDocument", Map.of("uri", "file:///tmp/example.ili"));

        assertEquals("file:///tmp/example.ili", coerce(params));
    }
}
