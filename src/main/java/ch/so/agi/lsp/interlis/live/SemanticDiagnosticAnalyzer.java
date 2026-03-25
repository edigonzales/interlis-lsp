package ch.so.agi.lsp.interlis.live;

import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import ch.so.agi.lsp.interlis.text.InterlisNameResolver;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

public final class SemanticDiagnosticAnalyzer {
    public List<Diagnostic> analyze(DocumentSnapshot snapshot,
                                    ScopeGraph scopeGraph,
                                    List<Diagnostic> syntaxDiagnostics,
                                    TransferDescription authoritativeTd,
                                    Set<String> importedModelNames) {
        if (snapshot == null || scopeGraph == null) {
            return List.of();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(duplicateDeclarationDiagnostics(snapshot, scopeGraph, syntaxDiagnostics));
        diagnostics.addAll(referenceDiagnostics(snapshot, scopeGraph, syntaxDiagnostics, authoritativeTd, importedModelNames));
        return List.copyOf(diagnostics);
    }

    private List<Diagnostic> duplicateDeclarationDiagnostics(DocumentSnapshot snapshot,
                                                             ScopeGraph scopeGraph,
                                                             List<Diagnostic> syntaxDiagnostics) {
        Map<DuplicateKey, List<LiveSymbol>> grouped = new LinkedHashMap<>();
        for (LiveSymbol symbol : scopeGraph.symbols()) {
            if (symbol == null || symbol.name() == null || symbol.nameRange() == null) {
                continue;
            }
            grouped.computeIfAbsent(
                    new DuplicateKey(symbol.parentId(), normalize(symbol.name())),
                    ignored -> new ArrayList<>())
                    .add(symbol);
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (List<LiveSymbol> duplicates : grouped.values()) {
            if (duplicates.size() < 2) {
                continue;
            }
            duplicates.sort(Comparator.comparingInt(symbol -> symbol.id() != null ? symbol.id().startOffset() : Integer.MAX_VALUE));
            LiveSymbol first = duplicates.get(0);
            for (int i = 1; i < duplicates.size(); i++) {
                LiveSymbol duplicate = duplicates.get(i);
                if (overlapsAny(duplicate.nameRange(), syntaxDiagnostics)) {
                    continue;
                }
                Diagnostic diagnostic = new Diagnostic(
                        duplicate.nameRange(),
                        "Duplicate declaration '" + duplicate.name() + "' in this scope",
                        DiagnosticSeverity.Error,
                        "live");
                if (first.nameRange() != null && first.uri() != null) {
                    diagnostic.setRelatedInformation(List.of(new DiagnosticRelatedInformation(
                            new Location(first.uri(), first.nameRange()),
                            "First declaration is here")));
                }
                diagnostics.add(diagnostic);
            }
        }
        return diagnostics;
    }

    private List<Diagnostic> referenceDiagnostics(DocumentSnapshot snapshot,
                                                  ScopeGraph scopeGraph,
                                                  List<Diagnostic> syntaxDiagnostics,
                                                  TransferDescription authoritativeTd,
                                                  Set<String> importedModelNames) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ReferenceHit reference : scopeGraph.references()) {
            if (reference == null || reference.range() == null || reference.rawText() == null || reference.rawText().isBlank()) {
                continue;
            }
            if (overlapsAny(reference.range(), syntaxDiagnostics)) {
                continue;
            }

            int referenceOffset = DocumentTracker.toOffset(snapshot.text(), reference.range().getStart());
            ResolutionState resolution = classifyReference(
                    scopeGraph,
                    reference,
                    referenceOffset,
                    authoritativeTd,
                    importedModelNames);
            if (resolution == ResolutionState.OK || resolution == ResolutionState.AMBIGUOUS) {
                continue;
            }

            String message = switch (resolution) {
                case FORWARD -> "Symbol '" + reference.rawText() + "' is not visible here yet";
                case UNKNOWN -> "Unknown " + describe(reference.allowedKinds()) + " '" + reference.rawText() + "'";
                case OK, AMBIGUOUS -> null;
            };
            if (message != null) {
                diagnostics.add(new Diagnostic(reference.range(), message, DiagnosticSeverity.Error, "live"));
            }
        }
        return diagnostics;
    }

    private ResolutionState classifyReference(ScopeGraph scopeGraph,
                                             ReferenceHit reference,
                                             int referenceOffset,
                                             TransferDescription authoritativeTd,
                                             Set<String> importedModelNames) {
        if (reference.rawText().indexOf('.') >= 0) {
            List<LiveSymbol> visible = scopeGraph.findQualifiedMatchesAt(reference.rawText(), reference.allowedKinds(), referenceOffset);
            if (visible.size() == 1) {
                return ResolutionState.OK;
            }
            if (visible.size() > 1) {
                return ResolutionState.AMBIGUOUS;
            }
            List<LiveSymbol> all = scopeGraph.findQualifiedMatches(reference.rawText(), reference.allowedKinds());
            ResolutionState local = all.isEmpty() ? ResolutionState.UNKNOWN : ResolutionState.FORWARD;
            return local == ResolutionState.UNKNOWN && resolvesImportedAuthoritatively(reference, authoritativeTd, importedModelNames)
                    ? ResolutionState.OK
                    : local;
        }

        List<LiveSymbol> visible = scopeGraph.visibleSymbolsAt(reference.scopeOwnerId(), reference.allowedKinds(), referenceOffset);
        long visibleMatches = visible.stream()
                .filter(symbol -> normalize(symbol.name()).equals(normalize(reference.rawText())))
                .count();
        if (visibleMatches == 1) {
            return ResolutionState.OK;
        }
        if (visibleMatches > 1) {
            return ResolutionState.AMBIGUOUS;
        }

        List<LiveSymbol> potentiallyVisible = scopeGraph.visibleSymbolsAt(reference.scopeOwnerId(), reference.allowedKinds(), Integer.MAX_VALUE);
        boolean existsInScope = potentiallyVisible.stream()
                .anyMatch(symbol -> normalize(symbol.name()).equals(normalize(reference.rawText())));
        ResolutionState local = existsInScope ? ResolutionState.FORWARD : ResolutionState.UNKNOWN;
        return local == ResolutionState.UNKNOWN && resolvesImportedAuthoritatively(reference, authoritativeTd, importedModelNames)
                ? ResolutionState.OK
                : local;
    }

    private boolean resolvesImportedAuthoritatively(ReferenceHit reference,
                                                    TransferDescription authoritativeTd,
                                                    Set<String> importedModelNames) {
        if (authoritativeTd == null || reference == null || reference.rawText() == null || reference.rawText().isBlank()) {
            return false;
        }

        String rawText = reference.rawText().trim();
        if (rawText.isBlank()) {
            return false;
        }

        Set<String> normalizedImports = normalizeAll(importedModelNames);
        boolean predefined = isPredefinedQualifiedReference(rawText);
        if (!predefined && normalizedImports.isEmpty()) {
            return false;
        }

        Element element = resolveImportedElement(
                authoritativeTd,
                importedModelNames,
                normalizedImports,
                rawText,
                reference.allowedKinds());
        if (element == null) {
            return false;
        }

        if (rawText.indexOf('.') >= 0) {
            String firstSegment = rawText.substring(0, rawText.indexOf('.'));
            return predefined || normalizedImports.contains(normalize(firstSegment));
        }

        Model enclosingModel = InterlisNameResolver.findEnclosingModel(element);
        return enclosingModel != null && normalizedImports.contains(normalize(enclosingModel.getName()));
    }

    private Element resolveImportedElement(TransferDescription authoritativeTd,
                                          Set<String> importedModelNames,
                                          Set<String> normalizedImports,
                                          String rawText,
                                          Set<InterlisSymbolKind> allowedKinds) {
        Element direct = rawText.indexOf('.') >= 0
                ? InterlisNameResolver.resolveQualifiedElement(authoritativeTd, rawText)
                : InterlisNameResolver.resolveElement(authoritativeTd, rawText);
        if (direct != null && isAllowed(InterlisMetamodelSupport.toSymbolKind(direct), allowedKinds)) {
            if (rawText.indexOf('.') >= 0) {
                return direct;
            }
            Model enclosingModel = InterlisNameResolver.findEnclosingModel(direct);
            if (enclosingModel != null && normalizedImports.contains(normalize(enclosingModel.getName()))) {
                return direct;
            }
        }

        if (rawText.indexOf('.') >= 0) {
            return null;
        }
        return findImportedElementBySimpleName(authoritativeTd, importedModelNames, rawText, allowedKinds);
    }

    private static boolean isPredefinedQualifiedReference(String rawText) {
        if (rawText == null || rawText.isBlank() || rawText.indexOf('.') < 0) {
            return false;
        }
        String firstSegment = rawText.substring(0, rawText.indexOf('.'));
        return "INTERLIS".equalsIgnoreCase(firstSegment);
    }

    private Element findImportedElementBySimpleName(TransferDescription authoritativeTd,
                                                    Set<String> importedModelNames,
                                                    String simpleName,
                                                    Set<InterlisSymbolKind> allowedKinds) {
        Element match = null;
        for (String importName : importedModelNames) {
            Element importedModel = InterlisNameResolver.resolveElement(authoritativeTd, importName);
            if (!(importedModel instanceof Model model)) {
                continue;
            }
            Element candidate = findElementBySimpleName(model, simpleName, allowedKinds);
            if (candidate == null) {
                continue;
            }
            if (match != null && match != candidate) {
                return null;
            }
            match = candidate;
        }
        return match;
    }

    private Element findElementBySimpleName(Container<?> container,
                                            String simpleName,
                                            Set<InterlisSymbolKind> allowedKinds) {
        if (container == null || simpleName == null || simpleName.isBlank()) {
            return null;
        }

        Element match = null;
        for (Iterator<?> iterator = container.iterator(); iterator.hasNext(); ) {
            Object childObject = iterator.next();
            if (!(childObject instanceof Element child)) {
                continue;
            }
            if (normalize(simpleName).equals(normalize(child.getName()))
                    && isAllowed(InterlisMetamodelSupport.toSymbolKind(child), allowedKinds)) {
                if (match != null && match != child) {
                    return null;
                }
                match = child;
            }
            if (child instanceof Container<?> childContainer) {
                Element nested = findElementBySimpleName(childContainer, simpleName, allowedKinds);
                if (nested != null) {
                    if (match != null && match != nested) {
                        return null;
                    }
                    match = nested;
                }
            }
        }
        return match;
    }

    private static boolean isAllowed(InterlisSymbolKind kind, Set<InterlisSymbolKind> allowedKinds) {
        return kind != null && (allowedKinds == null || allowedKinds.isEmpty() || allowedKinds.contains(kind));
    }

    private static Set<String> normalizeAll(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String name : names) {
            String normalizedName = normalize(name);
            if (!normalizedName.isBlank()) {
                normalized.add(normalizedName);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String describe(Set<InterlisSymbolKind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return "symbol";
        }
        if (kinds.size() == 1) {
            return kinds.iterator().next().name().toLowerCase(Locale.ROOT);
        }
        if (kinds.equals(Set.of(InterlisSymbolKind.DOMAIN, InterlisSymbolKind.STRUCTURE))) {
            return "domain or structure";
        }
        if (kinds.equals(Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW))) {
            return "reference target";
        }
        return "symbol";
    }

    private static boolean overlapsAny(Range candidate, List<Diagnostic> diagnostics) {
        if (candidate == null || diagnostics == null || diagnostics.isEmpty()) {
            return false;
        }
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic == null || diagnostic.getRange() == null) {
                continue;
            }
            if (overlaps(candidate, diagnostic.getRange())) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(Range left, Range right) {
        return compare(left.getStart(), right.getEnd()) < 0 && compare(right.getStart(), left.getEnd()) < 0;
    }

    private static int compare(org.eclipse.lsp4j.Position left, org.eclipse.lsp4j.Position right) {
        if (left.getLine() != right.getLine()) {
            return Integer.compare(left.getLine(), right.getLine());
        }
        return Integer.compare(left.getCharacter(), right.getCharacter());
    }

    private static String normalize(String name) {
        return name != null ? name.trim().toUpperCase(Locale.ROOT) : "";
    }

    private enum ResolutionState {
        OK,
        FORWARD,
        UNKNOWN,
        AMBIGUOUS
    }

    private record DuplicateKey(SymbolId parentId, String normalizedName) {
    }
}
