package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ScopeGraph {
    private final Map<SymbolId, LiveSymbol> symbolsById = new LinkedHashMap<>();
    private final Map<SymbolId, List<LiveSymbol>> childrenByParent = new LinkedHashMap<>();
    private final List<ReferenceHit> references = new ArrayList<>();

    public void addSymbol(LiveSymbol symbol) {
        if (symbol == null) {
            return;
        }
        symbolsById.put(symbol.id(), symbol);
        childrenByParent.computeIfAbsent(symbol.parentId(), key -> new ArrayList<>()).add(symbol);
    }

    public void addReference(ReferenceHit reference) {
        if (reference != null) {
            references.add(reference);
        }
    }

    public Collection<LiveSymbol> symbols() {
        return Collections.unmodifiableCollection(symbolsById.values());
    }

    public List<ReferenceHit> references() {
        return Collections.unmodifiableList(references);
    }

    public LiveSymbol symbol(SymbolId id) {
        return id != null ? symbolsById.get(id) : null;
    }

    public List<LiveSymbol> children(SymbolId parentId) {
        return Collections.unmodifiableList(childrenByParent.getOrDefault(parentId, Collections.emptyList()));
    }

    public LiveSymbol findSymbolAt(Position position) {
        if (position == null) {
            return null;
        }
        LiveSymbol best = null;
        for (LiveSymbol symbol : symbolsById.values()) {
            if (symbol.nameRange() == null) {
                continue;
            }
            if (!contains(symbol.nameRange(), position)) {
                continue;
            }
            if (best == null || isNarrower(symbol, best)) {
                best = symbol;
            }
        }
        return best;
    }

    public ReferenceHit findReferenceAt(Position position) {
        if (position == null) {
            return null;
        }
        for (ReferenceHit reference : references) {
            if (reference.range() != null && contains(reference.range(), position)) {
                return reference;
            }
        }
        return null;
    }

    public LiveSymbol findEnclosingContainer(Position position) {
        if (position == null) {
            return null;
        }
        LiveSymbol best = null;
        for (LiveSymbol symbol : symbolsById.values()) {
            if (!symbol.kind().isContainer() || symbol.fullRange() == null) {
                continue;
            }
            if (!contains(symbol.fullRange(), position)) {
                continue;
            }
            if (best == null || isNarrower(symbol, best)) {
                best = symbol;
            }
        }
        return best;
    }

    public List<LiveSymbol> visibleSymbols(SymbolId scopeOwnerId, Set<InterlisSymbolKind> allowedKinds) {
        return visibleSymbolsAt(scopeOwnerId, allowedKinds, Integer.MAX_VALUE);
    }

    public List<LiveSymbol> visibleSymbolsAt(SymbolId scopeOwnerId,
                                             Set<InterlisSymbolKind> allowedKinds,
                                             int referenceOffset) {
        LinkedHashSet<LiveSymbol> visible = new LinkedHashSet<>();
        LiveSymbol owner = symbol(scopeOwnerId);
        if (owner != null && owner.kind().isContainer()) {
            for (LiveSymbol child : children(owner.id())) {
                if (isVisibleCandidate(child, scopeOwnerId, allowedKinds, referenceOffset)) {
                    visible.add(child);
                }
            }
        }
        LiveSymbol cursor = owner;
        while (cursor != null) {
            for (LiveSymbol child : children(cursor.parentId())) {
                if (isVisibleCandidate(child, scopeOwnerId, allowedKinds, referenceOffset)) {
                    visible.add(child);
                }
            }
            cursor = symbol(cursor.parentId());
        }

        for (LiveSymbol root : children(null)) {
            if (isVisibleCandidate(root, scopeOwnerId, allowedKinds, referenceOffset)) {
                visible.add(root);
            }
        }
        return new ArrayList<>(visible);
    }

    public List<LiveSymbol> findQualifiedMatches(String rawText, Set<InterlisSymbolKind> allowedKinds) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }
        String normalized = normalizeQualified(rawText);
        List<LiveSymbol> matches = new ArrayList<>();
        for (LiveSymbol symbol : symbolsById.values()) {
            if (allowedKinds != null && !allowedKinds.isEmpty() && !allowedKinds.contains(symbol.kind())) {
                continue;
            }
            String qualified = normalizeQualified(symbol.qualifiedName());
            if (qualified.endsWith(normalized)) {
                matches.add(symbol);
            }
        }
        return matches;
    }

    public List<LiveSymbol> findQualifiedMatchesAt(String rawText,
                                                   Set<InterlisSymbolKind> allowedKinds,
                                                   int referenceOffset) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }
        String normalized = normalizeQualified(rawText);
        List<LiveSymbol> matches = new ArrayList<>();
        for (LiveSymbol symbol : symbolsById.values()) {
            if (!isAllowed(symbol, allowedKinds) || !isDeclaredBefore(symbol, referenceOffset)) {
                continue;
            }
            String qualified = normalizeQualified(symbol.qualifiedName());
            if (qualified.endsWith(normalized)) {
                matches.add(symbol);
            }
        }
        return matches;
    }

    public LiveSymbol resolveSimple(String name, SymbolId scopeOwnerId, Set<InterlisSymbolKind> allowedKinds) {
        return resolveSimpleAt(name, scopeOwnerId, allowedKinds, Integer.MAX_VALUE);
    }

    public LiveSymbol resolveSimpleAt(String name,
                                      SymbolId scopeOwnerId,
                                      Set<InterlisSymbolKind> allowedKinds,
                                      int referenceOffset) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String target = name.toUpperCase(Locale.ROOT);
        List<LiveSymbol> candidates = visibleSymbolsAt(scopeOwnerId, allowedKinds, referenceOffset);
        LiveSymbol match = null;
        for (LiveSymbol candidate : candidates) {
            if (!candidate.name().toUpperCase(Locale.ROOT).equals(target)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = candidate;
        }
        return match;
    }

    public List<LiveSymbol> findBySimpleName(String name, Set<InterlisSymbolKind> allowedKinds) {
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }
        String target = name.toUpperCase(Locale.ROOT);
        List<LiveSymbol> matches = new ArrayList<>();
        for (LiveSymbol symbol : symbolsById.values()) {
            if (!isAllowed(symbol, allowedKinds)) {
                continue;
            }
            if (symbol.name() != null && symbol.name().toUpperCase(Locale.ROOT).equals(target)) {
                matches.add(symbol);
            }
        }
        return matches;
    }

    private boolean isVisibleCandidate(LiveSymbol symbol,
                                       SymbolId scopeOwnerId,
                                       Set<InterlisSymbolKind> allowedKinds,
                                       int referenceOffset) {
        if (symbol == null) {
            return false;
        }
        if (scopeOwnerId != null && scopeOwnerId.equals(symbol.id())) {
            return false;
        }
        return isAllowed(symbol, allowedKinds) && isDeclaredBefore(symbol, referenceOffset);
    }

    private boolean isAllowed(LiveSymbol symbol, Set<InterlisSymbolKind> allowedKinds) {
        return symbol != null
                && (allowedKinds == null || allowedKinds.isEmpty() || allowedKinds.contains(symbol.kind()));
    }

    private boolean isDeclaredBefore(LiveSymbol symbol, int referenceOffset) {
        if (symbol == null || symbol.id() == null) {
            return false;
        }
        return symbol.id().startOffset() < referenceOffset;
    }

    private static boolean contains(org.eclipse.lsp4j.Range range, Position position) {
        if (range == null || position == null) {
            return false;
        }
        int start = compare(position, range.getStart());
        int end = compare(position, range.getEnd());
        return start >= 0 && end <= 0;
    }

    private static int compare(Position left, Position right) {
        if (left.getLine() != right.getLine()) {
            return Integer.compare(left.getLine(), right.getLine());
        }
        return Integer.compare(left.getCharacter(), right.getCharacter());
    }

    private static boolean isNarrower(LiveSymbol candidate, LiveSymbol incumbent) {
        if (candidate.fullRange() == null || incumbent.fullRange() == null) {
            return false;
        }
        int candidateLines = candidate.fullRange().getEnd().getLine() - candidate.fullRange().getStart().getLine();
        int incumbentLines = incumbent.fullRange().getEnd().getLine() - incumbent.fullRange().getStart().getLine();
        if (candidateLines != incumbentLines) {
            return candidateLines < incumbentLines;
        }
        int candidateChars = candidate.fullRange().getEnd().getCharacter() - candidate.fullRange().getStart().getCharacter();
        int incumbentChars = incumbent.fullRange().getEnd().getCharacter() - incumbent.fullRange().getStart().getCharacter();
        return candidateChars < incumbentChars;
    }

    private static String normalizeQualified(String qualifiedName) {
        return qualifiedName != null ? qualifiedName.replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : "";
    }
}
