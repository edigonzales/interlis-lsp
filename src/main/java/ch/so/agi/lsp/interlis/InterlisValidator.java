package ch.so.agi.lsp.interlis;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps calls into the ili2c compiler. Replace placeholders with real ili2c API calls.
 */
public class InterlisValidator {

    public static class Message {
        public enum Severity { ERROR, WARNING, INFO }
        private final Severity severity;
        private final String fileUriOrPath;
        private final int line;   // 1-based
        private final int column; // 1-based
        private final String text;

        public Message(Severity severity, String fileUriOrPath, int line, int column, String text) {
            this.severity = severity;
            this.fileUriOrPath = fileUriOrPath;
            this.line = line;
            this.column = column;
            this.text = text;
        }
        public Severity getSeverity() { return severity; }
        public String getFileUriOrPath() { return fileUriOrPath; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getText() { return text; }
    }

    public static class ValidationOutcome {
        private final String logText;
        private final List<Message> messages;
        public ValidationOutcome(String logText, List<Message> messages) {
            this.logText = logText;
            this.messages = messages;
        }
        public String getLogText() { return logText; }
        public List<Message> getMessages() { return messages; }
    }

    /** Validate an .ili file by calling ili2c's Java API (no external process!). */
    public ValidationOutcome validate(String fileUriOrPath) {
        StringBuilder log = new StringBuilder();
        List<Message> messages = new ArrayList<>();
        
        System.out.println("Hallo Welt.");

        // --- BEGIN PLACEHOLDER: Replace with real ili2c calls ---
        log.append("ERROR: ").append(fileUriOrPath).append(":5:10 Unexpected token 'MODEL'").append('\n');
        messages.add(new Message(Message.Severity.ERROR, fileUriOrPath, 5, 10, "Unexpected token 'MODEL'"));
        // --- END PLACEHOLDER ---

        return new ValidationOutcome(log.toString(), messages);
    }
}
