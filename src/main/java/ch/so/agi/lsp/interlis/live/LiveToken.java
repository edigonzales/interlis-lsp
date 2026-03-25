package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Range;

import java.util.Locale;

public record LiveToken(int tokenIndex,
                        int tokenType,
                        String text,
                        Range range,
                        int startOffset,
                        int endOffset) {
    public String upperText() {
        return text != null ? text.toUpperCase(Locale.ROOT) : "";
    }

    public int line() {
        return range != null && range.getStart() != null ? range.getStart().getLine() : 0;
    }
}
