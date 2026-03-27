package ch.so.agi.lsp.interlis.live;

import ch.so.agi.lsp.interlis.text.DocumentTracker;
import org.eclipse.lsp4j.Position;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LiveSymbolResolver {
    private LiveSymbolResolver() {
    }

    public static ResolvedSymbol resolveAtPosition(LiveParseResult result, Position position) {
        if (result == null || position == null) {
            return null;
        }
        ScopeGraph graph = result.scopeGraph();
        LiveSymbol symbol = graph.findSymbolAt(position);
        if (symbol != null) {
            return new ResolvedSymbol(symbol, null, ResolvedSymbol.collectSpellings(symbol.qualifiedName(), symbol.name()));
        }

        ReferenceHit reference = graph.findReferenceAt(position);
        if (reference == null) {
            return null;
        }

        LiveSymbol resolved = resolveReferenceLocally(result, reference);
        if (resolved == null) {
            return null;
        }
        return new ResolvedSymbol(resolved, null, ResolvedSymbol.collectSpellings(resolved.qualifiedName(), resolved.name()));
    }

    public static LiveSymbol resolveReferenceLocally(LiveParseResult result, ReferenceHit reference) {
        if (result == null || reference == null) {
            return null;
        }
        String rawText = normalize(reference.rawText());
        if (rawText.isBlank()) {
            return null;
        }
        ScopeGraph graph = result.scopeGraph();
        int referenceOffset = referenceOffset(result, reference);
        if (rawText.indexOf('.') >= 0) {
            List<LiveSymbol> matches = graph.findQualifiedMatchesAt(rawText, reference.allowedKinds(), referenceOffset);
            return matches.size() == 1 ? matches.get(0) : null;
        }
        return graph.resolveSimpleAt(rawText, reference.scopeOwnerId(), reference.allowedKinds(), referenceOffset);
    }

    public static boolean isReferenceTo(LiveParseResult result, ReferenceHit reference, ResolvedSymbol target) {
        if (result == null || reference == null || target == null || target.symbol() == null) {
            return false;
        }
        LiveSymbol locallyResolved = resolveReferenceLocally(result, reference);
        if (locallyResolved != null) {
            return sameSymbol(locallyResolved, target.symbol())
                    || sameQualifiedName(locallyResolved.qualifiedName(), target.qualifiedName());
        }
        String rawText = normalize(reference.rawText());
        if (rawText.isBlank()) {
            return false;
        }
        if (rawText.indexOf('.') >= 0) {
            if (!result.scopeGraph().findQualifiedMatches(rawText, reference.allowedKinds()).isEmpty()) {
                return false;
            }
            for (String spelling : target.spellings()) {
                if (normalize(spelling).endsWith(rawText.toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
        if (!rawText.equals(normalize(target.symbol().name()))) {
            return false;
        }
        int localMatches = 0;
        int referenceOffset = referenceOffset(result, reference);
        for (LiveSymbol visible : result.scopeGraph().visibleSymbolsAt(reference.scopeOwnerId(), reference.allowedKinds(), referenceOffset)) {
            if (normalize(visible.name()).equals(rawText)) {
                localMatches++;
            }
        }
        if (localMatches != 0) {
            return false;
        }
        for (LiveSymbol symbol : result.scopeGraph().visibleSymbolsAt(reference.scopeOwnerId(), reference.allowedKinds(), Integer.MAX_VALUE)) {
            if (normalize(symbol.name()).equals(rawText)) {
                return false;
            }
        }
        return true;
    }

    public static boolean sameSymbol(LiveSymbol left, LiveSymbol right) {
        if (left == null || right == null || left.id() == null || right.id() == null) {
            return false;
        }
        return left.id().equals(right.id());
    }

    public static boolean sameQualifiedName(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    public static Set<InterlisSymbolKind> typeKinds() {
        return Set.of(
                InterlisSymbolKind.DOMAIN,
                InterlisSymbolKind.STRUCTURE,
                InterlisSymbolKind.CLASS,
                InterlisSymbolKind.ASSOCIATION,
                InterlisSymbolKind.VIEW,
                InterlisSymbolKind.UNIT);
    }

    private static String normalize(String text) {
        return text != null ? text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : "";
    }

    private static int referenceOffset(LiveParseResult result, ReferenceHit reference) {
        if (result == null || result.snapshot() == null || result.snapshot().text() == null
                || reference == null || reference.range() == null || reference.range().getStart() == null) {
            return Integer.MAX_VALUE;
        }
        return DocumentTracker.toOffset(result.snapshot().text(), reference.range().getStart());
    }
}
