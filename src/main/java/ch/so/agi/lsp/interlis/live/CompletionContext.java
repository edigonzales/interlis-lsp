package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Range;

import java.util.Set;

public record CompletionContext(Kind kind,
                                String prefix,
                                String subject,
                                String qualifierPath,
                                Range replaceRange,
                                SymbolId scopeOwnerId,
                                Set<InterlisSymbolKind> allowedKinds,
                                InterlisSymbolKind ownerKind) {
    public enum Kind {
        NONE,
        IMPORT_MODEL,
        END_NAME,
        CONTAINER_BODY_ROOT,
        ATTRIBUTE_TYPE_ROOT,
        TEXT_LENGTH_TAIL,
        TEXT_LENGTH_VALUE_TAIL,
        INLINE_NUMERIC_RANGE_TAIL,
        INLINE_NUMERIC_UPPER_BOUND_TAIL,
        FORMAT_TYPE_TARGET,
        FORMAT_BOUNDS_TAIL,
        COLLECTION_POST_KEYWORD,
        COLLECTION_OF_TARGET,
        REFERENCE_POST_KEYWORD,
        REFERENCE_TARGET,
        EXTENDS_TARGET,
        META_TYPE_TAIL,
        QUALIFIED_MEMBER
    }
}
