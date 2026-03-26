package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Diagnostic;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public record LiveParseResult(DocumentSnapshot snapshot,
                              ScopeGraph scopeGraph,
                              InterlisLanguageLevel languageLevel,
                              List<LiveToken> tokens,
                              List<RawSyntaxError> rawSyntaxErrors,
                              List<CompletionContext> completionContexts,
                              Set<SymbolId> formattedDomainIds,
                              List<ImportEntry> importEntries,
                              Set<String> importedModelNames,
                              boolean authoritativeFallbackEnabled,
                              List<Diagnostic> diagnostics) {
    public LiveParseResult {
        languageLevel = languageLevel != null ? languageLevel : InterlisLanguageLevel.UNKNOWN;
        tokens = tokens != null ? List.copyOf(tokens) : List.of();
        rawSyntaxErrors = rawSyntaxErrors != null ? List.copyOf(rawSyntaxErrors) : List.of();
        completionContexts = completionContexts != null ? List.copyOf(completionContexts) : List.of();
        formattedDomainIds = formattedDomainIds != null ? Set.copyOf(formattedDomainIds) : Set.of();
        importEntries = importEntries != null ? List.copyOf(importEntries) : List.of();
        importedModelNames = importedModelNames != null ? Set.copyOf(importedModelNames) : Set.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public List<LiveToken> tokens() {
        return Collections.unmodifiableList(tokens);
    }

    public List<RawSyntaxError> rawSyntaxErrors() {
        return Collections.unmodifiableList(rawSyntaxErrors);
    }

    public List<CompletionContext> completionContexts() {
        return Collections.unmodifiableList(completionContexts);
    }

    public Set<SymbolId> formattedDomainIds() {
        return Collections.unmodifiableSet(formattedDomainIds);
    }

    public List<ImportEntry> importEntries() {
        return Collections.unmodifiableList(importEntries);
    }

    public Set<String> importedModelNames() {
        return Collections.unmodifiableSet(importedModelNames);
    }

    public List<Diagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}
