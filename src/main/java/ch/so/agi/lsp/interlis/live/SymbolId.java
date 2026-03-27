package ch.so.agi.lsp.interlis.live;

public record SymbolId(String uri, InterlisSymbolKind kind, String qualifiedName, int startOffset) {
}
