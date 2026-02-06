package ch.so.agi.lsp.interlis.glsp;

import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;

/**
 * Shares the LSP server instance with the embedded GLSP runtime.
 */
public final class InterlisGlspBridge {
    private static volatile InterlisLanguageServer languageServer;

    private InterlisGlspBridge() {
    }

    public static void bindLanguageServer(InterlisLanguageServer server) {
        languageServer = server;
    }

    public static InterlisLanguageServer getLanguageServer() {
        return languageServer;
    }

    public static void clear() {
        languageServer = null;
    }
}
