package ch.so.agi.lsp.interlis.server;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface InterlisLanguageClient extends LanguageClient {
    @JsonNotification("interlis/clearLog")
    void clearLog();

    @JsonNotification("interlis/log")
    void log(LogParams params);

    @JsonNotification("interlis/debugLog")
    void debugLog(LogParams params);

    @JsonNotification("interlis/compileFinished")
    void compileFinished(CompileFinishedParams params);

    class LogParams {
        public String text;
        public LogParams() {}
        public LogParams(String text) { this.text = text; }
    }

    class CompileFinishedParams {
        public String uri;
        public boolean success;

        public CompileFinishedParams() {}

        public CompileFinishedParams(String uri, boolean success) {
            this.uri = uri;
            this.success = success;
        }
    }
}
