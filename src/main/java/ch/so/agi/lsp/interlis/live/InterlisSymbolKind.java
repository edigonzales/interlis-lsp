package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolKind;

public enum InterlisSymbolKind {
    MODEL(SymbolKind.Module, CompletionItemKind.Module),
    TOPIC(SymbolKind.Namespace, CompletionItemKind.Module),
    CLASS(SymbolKind.Class, CompletionItemKind.Class),
    STRUCTURE(SymbolKind.Struct, CompletionItemKind.Struct),
    ASSOCIATION(SymbolKind.Interface, CompletionItemKind.Interface),
    DOMAIN(SymbolKind.TypeParameter, CompletionItemKind.Struct),
    UNIT(SymbolKind.Constant, CompletionItemKind.Unit),
    FUNCTION(SymbolKind.Function, CompletionItemKind.Function),
    VIEW(SymbolKind.Object, CompletionItemKind.Class),
    GRAPHIC(SymbolKind.Object, CompletionItemKind.Class),
    ATTRIBUTE(SymbolKind.Property, CompletionItemKind.Field),
    ROLE(SymbolKind.Property, CompletionItemKind.Property);

    private final SymbolKind symbolKind;
    private final CompletionItemKind completionKind;

    InterlisSymbolKind(SymbolKind symbolKind, CompletionItemKind completionKind) {
        this.symbolKind = symbolKind;
        this.completionKind = completionKind;
    }

    public SymbolKind toSymbolKind() {
        return symbolKind;
    }

    public CompletionItemKind toCompletionKind() {
        return completionKind;
    }

    public boolean isTypeLike() {
        return this == DOMAIN
                || this == STRUCTURE
                || this == CLASS
                || this == ASSOCIATION
                || this == VIEW
                || this == UNIT;
    }

    public boolean isContainer() {
        return this == MODEL
                || this == TOPIC
                || this == CLASS
                || this == STRUCTURE
                || this == ASSOCIATION
                || this == VIEW
                || this == GRAPHIC;
    }
}
