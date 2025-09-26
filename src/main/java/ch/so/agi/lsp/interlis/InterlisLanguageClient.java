package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface InterlisLanguageClient extends LanguageClient {
    @JsonNotification("interlis/clearLog")
    void clearLog();

    @JsonNotification("interlis/log")
    void log(LogParams params);

    class LogParams {
        public String text;
        public LogParams() {}
        public LogParams(String text) { this.text = text; }
    }
}
