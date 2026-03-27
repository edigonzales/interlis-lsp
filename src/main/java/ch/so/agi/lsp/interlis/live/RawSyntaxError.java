package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Position;

public record RawSyntaxError(Position position,
                             Integer offendingTokenIndex,
                             int offendingTokenType,
                             String offendingText,
                             String message) {
}
