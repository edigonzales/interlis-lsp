package ch.so.agi.lsp.interlis.text;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the live contents of open text documents so on-type features can
 * operate on the latest edits (even when the file is unsaved).
 */
public final class DocumentTracker {

    private static final class DocumentState {
        String text;
        Integer version;

        DocumentState(String text, Integer version) {
            this.text = text != null ? text : "";
            this.version = version;
        }
    }

    private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();

    public void open(TextDocumentItem item) {
        if (item == null) return;
        documents.put(item.getUri(), new DocumentState(item.getText(), item.getVersion()));
    }

    public void close(String uri) {
        if (uri != null) {
            documents.remove(uri);
        }
    }

    public void applyChanges(VersionedTextDocumentIdentifier identifier, List<TextDocumentContentChangeEvent> changes) {
        if (identifier == null || identifier.getUri() == null || changes == null || changes.isEmpty()) {
            return;
        }

        String uri = identifier.getUri();
        DocumentState state = documents.computeIfAbsent(uri, u -> new DocumentState("", null));
        String text = state.text;

        for (TextDocumentContentChangeEvent change : changes) {
            if (change.getRange() == null) {
                // full document sync
                text = change.getText();
                continue;
            }

            Range range = change.getRange();
            int start = toOffset(text, range.getStart());
            int end = toOffset(text, range.getEnd());
            start = clamp(start, 0, text.length());
            end = clamp(end, start, text.length());

            StringBuilder sb = new StringBuilder(text.length() + change.getText().length());
            sb.append(text, 0, start);
            sb.append(change.getText());
            sb.append(text, end, text.length());
            text = sb.toString();
        }

        state.text = text;
        state.version = identifier.getVersion();
    }

    public String getText(String uri) {
        DocumentState state = uri == null ? null : documents.get(uri);
        return state != null ? state.text : null;
    }

    public Integer getVersion(String uri) {
        DocumentState state = uri == null ? null : documents.get(uri);
        return state != null ? state.version : null;
    }

    public static int toOffset(String text, Position position) {
        if (text == null || text.isEmpty() || position == null) {
            return 0;
        }

        int line = Math.max(position.getLine(), 0);
        int character = Math.max(position.getCharacter(), 0);
        int index = 0;
        int currentLine = 0;
        int length = text.length();

        while (index < length && currentLine < line) {
            char ch = text.charAt(index++);
            if (ch == '\r') {
                if (index < length && text.charAt(index) == '\n') {
                    index++;
                }
                currentLine++;
            } else if (ch == '\n') {
                currentLine++;
            }
        }

        // Clamp to end of document if requested line is beyond current length
        if (currentLine < line) {
            return length;
        }

        int lineStart = index;
        int remaining = character;
        while (index < length && remaining > 0) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '\r') {
                break;
            }
            index++;
            remaining--;
        }

        return index;
    }

    public static Position positionAt(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return new Position(0, 0);
        }

        int length = text.length();
        int safeOffset = clamp(offset, 0, length);
        int line = 0;
        int column = 0;

        for (int i = 0; i < safeOffset; i++) {
            char ch = text.charAt(i);
            if (ch == '\r') {
                if (i + 1 < safeOffset && text.charAt(i + 1) == '\n') {
                    i++;
                }
                line++;
                column = 0;
            } else if (ch == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }

        return new Position(line, column);
    }

    public static int lineStartOffset(String text, int lineNumber) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (lineNumber <= 0) {
            return 0;
        }

        int length = text.length();
        int line = 0;

        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            if (line == lineNumber) {
                return i;
            }
            if (ch == '\r') {
                if (i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                line++;
                if (line == lineNumber) {
                    return Math.min(i + 1, length);
                }
            } else if (ch == '\n') {
                line++;
                if (line == lineNumber) {
                    return Math.min(i + 1, length);
                }
            }
        }
        return length;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
