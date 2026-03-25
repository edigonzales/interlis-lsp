package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Range;

import java.util.Set;

public record ReferenceHit(String uri,
                           Range range,
                           String rawText,
                           Set<InterlisSymbolKind> allowedKinds,
                           SymbolId scopeOwnerId) {
}
