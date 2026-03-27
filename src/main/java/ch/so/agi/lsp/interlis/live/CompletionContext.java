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
        TOP_LEVEL_ROOT,
        CONTAINER_BODY_ROOT,
        DECLARATION_HEADER_AFTER_NAME,
        DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_NAME,
        DECLARATION_HEADER_MODIFIER_VALUE,
        DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_VALUE,
        DECLARATION_HEADER_MODIFIER_CLOSE,
        DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_CLOSE,
        DECLARATION_HEADER_AFTER_MODIFIER,
        DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_MODIFIER,
        DECLARATION_HEADER_AFTER_EXTENDS,
        DECLARATION_HEADER_BLOCK_SUFFIX_EXTENDS_TARGET,
        ATTRIBUTE_TYPE_ROOT,
        DOMAIN_TYPE_ROOT,
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
        METAATTRIBUTE_ROOT,
        METAATTRIBUTE_VALUE,
        UNIT_TYPE_ROOT,
        UNIT_BRACKET_TARGET,
        UNIT_COMPOSED_TARGET,
        UNIT_COMPOSED_OPERATOR,
        META_TYPE_TAIL,
        QUALIFIED_MEMBER
    }
}
