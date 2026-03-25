package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Range;

public record LiveSymbol(SymbolId id,
                         String name,
                         String qualifiedName,
                         String uri,
                         InterlisSymbolKind kind,
                         Range nameRange,
                         Range fullRange,
                         Range endRange,
                         SymbolId parentId) {
}
