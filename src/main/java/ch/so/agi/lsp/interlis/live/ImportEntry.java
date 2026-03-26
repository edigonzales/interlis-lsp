package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Range;

public record ImportEntry(String name,
                          Range range,
                          boolean unqualified,
                          boolean terminated) {
}
