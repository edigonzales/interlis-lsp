package ch.so.agi.lsp.interlis.live;

import ch.so.agi.lsp.interlis.antlr.InterlisLexer;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SyntaxDiagnosticMapper {
    public List<Diagnostic> map(DocumentSnapshot snapshot,
                                ScopeGraph scopeGraph,
                                List<LiveToken> tokens,
                                List<RawSyntaxError> rawErrors) {
        if (snapshot == null) {
            return List.of();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        if (rawErrors != null) {
            for (RawSyntaxError error : rawErrors) {
                if (error == null) {
                    continue;
                }
                diagnostics.add(mapRawError(snapshot, tokens, error));
            }
        }
        diagnostics.addAll(missingSemicolonDiagnostics(snapshot, tokens, diagnostics));
        diagnostics.addAll(missingAttributeTypeDiagnostics(snapshot, scopeGraph, tokens, diagnostics));
        diagnostics.addAll(missingAttributeHeadDiagnostics(snapshot, scopeGraph, tokens, diagnostics));
        diagnostics.addAll(endNameDiagnostics(snapshot, scopeGraph));
        return List.copyOf(diagnostics);
    }

    private Diagnostic mapRawError(DocumentSnapshot snapshot, List<LiveToken> tokens, RawSyntaxError error) {
        SpecializedDiagnostic specialized = findSpecializedDiagnostic(snapshot, tokens, error);
        Range range = specialized != null ? specialized.range() : fallbackRange(snapshot, tokens, error);
        String message = specialized != null && specialized.message() != null
                ? specialized.message()
                : error.message();
        return new Diagnostic(range, message, DiagnosticSeverity.Error, "antlr");
    }

    private SpecializedDiagnostic findSpecializedDiagnostic(DocumentSnapshot snapshot, List<LiveToken> tokens, RawSyntaxError error) {
        int beforeIndex = indexBeforeError(tokens, error);
        if (beforeIndex < 0) {
            return null;
        }

        Range collectionRange = unfinishedCollectionRange(tokens, beforeIndex);
        if (collectionRange != null) {
            return new SpecializedDiagnostic(collectionRange, null);
        }

        Range referenceRange = danglingReferenceRange(tokens, beforeIndex);
        if (referenceRange != null) {
            return new SpecializedDiagnostic(referenceRange, null);
        }

        Range extendsRange = danglingExtendsRange(tokens, beforeIndex);
        if (extendsRange != null) {
            return new SpecializedDiagnostic(extendsRange, null);
        }

        SpecializedDiagnostic semicolonDiagnostic = missingSemicolonDiagnostic(snapshot, tokens, error, beforeIndex);
        if (semicolonDiagnostic != null) {
            return semicolonDiagnostic;
        }

        return null;
    }

    private List<Diagnostic> endNameDiagnostics(DocumentSnapshot snapshot, ScopeGraph scopeGraph) {
        if (scopeGraph == null || snapshot.text() == null) {
            return List.of();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (LiveSymbol symbol : scopeGraph.symbols()) {
            if (symbol == null || !symbol.kind().isContainer() || symbol.endRange() == null || symbol.nameRange() == null) {
                continue;
            }
            String actual = text(snapshot.text(), symbol.endRange());
            if (actual == null || actual.isBlank() || symbol.name() == null) {
                continue;
            }
            if (actual.equals(symbol.name())) {
                continue;
            }

            Range range = expandToEndClause(snapshot.text(), symbol.endRange());
            Diagnostic diagnostic = new Diagnostic(
                    range,
                    "END name mismatch: expected '" + symbol.name() + "' but found '" + actual + "'",
                    DiagnosticSeverity.Error,
                    "live");
            DiagnosticRelatedInformation related = new DiagnosticRelatedInformation(
                    new Location(snapshot.uri(), symbol.nameRange()),
                    "Container starts here");
            diagnostic.setRelatedInformation(List.of(related));
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    private static Range unfinishedCollectionRange(List<LiveToken> tokens, int beforeIndex) {
        ClauseWindow clause = clauseWindow(tokens, beforeIndex);
        if (clause == null) {
            return null;
        }
        LiveToken anchor = null;
        boolean hasOf = false;
        for (LiveToken token : clause.tokens()) {
            if (token.tokenType() == InterlisLexer.OF) {
                hasOf = true;
            }
            if (token.tokenType() == InterlisLexer.LIST || token.tokenType() == InterlisLexer.BAG) {
                anchor = token;
                hasOf = false;
            }
        }
        if (anchor == null) {
            return null;
        }
        if (!hasOf) {
            return lineClauseRange(clause.tokens(), anchor);
        }

        int ofIndex = lastIndexOf(clause.tokens(), InterlisLexer.OF);
        if (ofIndex < 0 || hasMeaningfulTarget(clause.tokens(), ofIndex + 1)) {
            return null;
        }
        return lineClauseRange(clause.tokens(), anchor);
    }

    private static Range danglingReferenceRange(List<LiveToken> tokens, int beforeIndex) {
        ClauseWindow clause = clauseWindow(tokens, beforeIndex);
        if (clause == null) {
            return null;
        }
        LiveToken reference = null;
        boolean hasTo = false;
        for (LiveToken token : clause.tokens()) {
            if (token.tokenType() == InterlisLexer.REFERENCE) {
                reference = token;
                hasTo = false;
                continue;
            }
            if (reference != null && token.tokenType() == InterlisLexer.TO) {
                hasTo = true;
            }
        }
        if (reference == null) {
            return null;
        }
        if (!hasTo) {
            return lineClauseRange(clause.tokens(), reference);
        }

        int toIndex = lastIndexOf(clause.tokens(), InterlisLexer.TO);
        if (toIndex < 0 || hasMeaningfulTarget(clause.tokens(), toIndex + 1)) {
            return null;
        }
        return lineClauseRange(clause.tokens(), reference);
    }

    private static Range danglingExtendsRange(List<LiveToken> tokens, int beforeIndex) {
        ClauseWindow clause = clauseWindow(tokens, beforeIndex);
        if (clause == null) {
            return null;
        }
        int extendsIndex = lastIndexOf(clause.tokens(), InterlisLexer.EXTENDS);
        if (extendsIndex < 0) {
            return null;
        }
        if (hasMeaningfulTarget(clause.tokens(), extendsIndex + 1)) {
            return null;
        }
        return lineClauseRange(clause.tokens(), clause.tokens().get(extendsIndex));
    }

    private static SpecializedDiagnostic missingSemicolonDiagnostic(DocumentSnapshot snapshot,
                                                                   List<LiveToken> tokens,
                                                                   RawSyntaxError error,
                                                                   int beforeIndex) {
        ClauseWindow clause = clauseWindow(tokens, beforeIndex);
        if (clause == null) {
            return null;
        }
        Range range = typeClauseRange(clause.tokens());
        if (range == null) {
            return null;
        }
        if (!expectsSemicolon(error) && !looksLikeMissingSemicolon(clause.tokens(), error)) {
            return null;
        }
        return new SpecializedDiagnostic(range, "Missing ';' after attribute definition");
    }

    private static List<Diagnostic> missingSemicolonDiagnostics(DocumentSnapshot snapshot,
                                                                List<LiveToken> tokens,
                                                                List<Diagnostic> existingDiagnostics) {
        if (snapshot == null || tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int lineStart = 0; lineStart < tokens.size(); ) {
            int lineEnd = lineStart;
            int line = tokens.get(lineStart).line();
            while (lineEnd + 1 < tokens.size() && tokens.get(lineEnd + 1).line() == line) {
                lineEnd++;
            }
            List<LiveToken> lineTokens = tokens.subList(lineStart, lineEnd + 1);
            Range range = likelyMissingSemicolonRange(lineTokens, tokens, lineEnd + 1);
            if (range != null && !overlapsAny(range, existingDiagnostics) && !overlapsAny(range, diagnostics)) {
                diagnostics.add(new Diagnostic(range, "Missing ';' after attribute definition", DiagnosticSeverity.Error, "live"));
            }
            lineStart = lineEnd + 1;
        }
        return diagnostics;
    }

    private static List<Diagnostic> missingAttributeHeadDiagnostics(DocumentSnapshot snapshot,
                                                                    ScopeGraph scopeGraph,
                                                                    List<LiveToken> tokens,
                                                                    List<Diagnostic> existingDiagnostics) {
        if (snapshot == null || scopeGraph == null || tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int lineStart = 0; lineStart < tokens.size(); ) {
            int lineEnd = lineStart;
            int line = tokens.get(lineStart).line();
            while (lineEnd + 1 < tokens.size() && tokens.get(lineEnd + 1).line() == line) {
                lineEnd++;
            }
            List<LiveToken> lineTokens = tokens.subList(lineStart, lineEnd + 1);
            Range range = likelyMissingAttributeHeadRange(scopeGraph, lineTokens, tokens, lineEnd + 1);
            if (range != null && !overlapsAny(range, existingDiagnostics) && !overlapsAny(range, diagnostics)) {
                diagnostics.add(new Diagnostic(
                        range,
                        "Missing ':' and type after attribute name",
                        DiagnosticSeverity.Error,
                        "live"));
            }
            lineStart = lineEnd + 1;
        }
        return diagnostics;
    }

    private static List<Diagnostic> missingAttributeTypeDiagnostics(DocumentSnapshot snapshot,
                                                                    ScopeGraph scopeGraph,
                                                                    List<LiveToken> tokens,
                                                                    List<Diagnostic> existingDiagnostics) {
        if (snapshot == null || scopeGraph == null || tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int lineStart = 0; lineStart < tokens.size(); ) {
            int lineEnd = lineStart;
            int line = tokens.get(lineStart).line();
            while (lineEnd + 1 < tokens.size() && tokens.get(lineEnd + 1).line() == line) {
                lineEnd++;
            }
            List<LiveToken> lineTokens = tokens.subList(lineStart, lineEnd + 1);
            Range range = likelyMissingAttributeTypeRange(scopeGraph, lineTokens, tokens, lineEnd + 1);
            if (range != null && !overlapsAny(range, existingDiagnostics) && !overlapsAny(range, diagnostics)) {
                diagnostics.add(new Diagnostic(
                        range,
                        "Missing type after ':' in attribute definition",
                        DiagnosticSeverity.Error,
                        "live"));
            }
            lineStart = lineEnd + 1;
        }
        return diagnostics;
    }

    private static ClauseWindow clauseWindow(List<LiveToken> tokens, int beforeIndex) {
        if (tokens == null || tokens.isEmpty() || beforeIndex < 0) {
            return null;
        }
        List<LiveToken> window = new ArrayList<>();
        int maxLookback = Math.max(0, beforeIndex - 24);
        for (int i = beforeIndex; i >= maxLookback; i--) {
            LiveToken token = tokens.get(i);
            if (isHardBoundary(token)) {
                break;
            }
            window.add(token);
        }
        Collections.reverse(window);
        return window.isEmpty() ? null : new ClauseWindow(window);
    }

    private static boolean isHardBoundary(LiveToken token) {
        if (token == null) {
            return true;
        }
        return isHardBoundaryTokenType(token.tokenType());
    }

    private static boolean isHardBoundaryTokenType(int tokenType) {
        return switch (tokenType) {
            case InterlisLexer.SEMI, InterlisLexer.EQ, InterlisLexer.MODEL, InterlisLexer.TOPIC,
                 InterlisLexer.CLASS, InterlisLexer.STRUCTURE, InterlisLexer.ASSOCIATION,
                 InterlisLexer.DOMAIN, InterlisLexer.UNIT, InterlisLexer.FUNCTION, InterlisLexer.GRAPHIC,
                 InterlisLexer.VIEW, InterlisLexer.END, InterlisLexer.EOF -> true;
            default -> false;
        };
    }

    private static boolean expectsSemicolon(RawSyntaxError error) {
        if (error == null || error.message() == null) {
            return false;
        }
        String message = error.message().toLowerCase(Locale.ROOT);
        return message.contains("missing ';'")
                || (message.contains("';'") && message.contains("expecting"));
    }

    private static boolean looksLikeMissingSemicolon(List<LiveToken> tokens, RawSyntaxError error) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        int colonIndex = lastIndexOf(tokens, InterlisLexer.COLON);
        if (colonIndex < 0 || colonIndex + 1 >= tokens.size()) {
            return false;
        }
        if (error != null && isHardBoundaryTokenType(error.offendingTokenType())) {
            return true;
        }
        LiveToken lastOnLine = null;
        for (int i = colonIndex + 1; i < tokens.size(); i++) {
            LiveToken token = tokens.get(i);
            if (token == null) {
                continue;
            }
            if (lastOnLine != null && token.line() != lastOnLine.line()) {
                break;
            }
            lastOnLine = token;
        }
        return lastOnLine != null
                && error != null
                && error.position() != null
                && compare(error.position(), lastOnLine.range().getEnd()) >= 0;
    }

    private static boolean hasMeaningfulTarget(List<LiveToken> tokens, int startIndex) {
        for (int i = Math.max(startIndex, 0); i < tokens.size(); i++) {
            LiveToken token = tokens.get(i);
            if (token == null) {
                continue;
            }
            if (token.tokenType() == InterlisLexer.Name
                    || token.tokenType() == InterlisLexer.ANYSTRUCTURE
                    || token.tokenType() == InterlisLexer.ANYCLASS
                    || token.tokenType() == InterlisLexer.STRUCTURE
                    || token.tokenType() == InterlisLexer.CLASS
                    || token.tokenType() == InterlisLexer.VIEW
                    || token.tokenType() == InterlisLexer.ASSOCIATION) {
                return true;
            }
            if (token.tokenType() == InterlisLexer.DOT) {
                continue;
            }
        }
        return false;
    }

    private static Range likelyMissingSemicolonRange(List<LiveToken> lineTokens,
                                                     List<LiveToken> allTokens,
                                                     int nextIndex) {
        if (lineTokens == null || lineTokens.isEmpty()) {
            return null;
        }
        int colonIndex = lastIndexOf(lineTokens, InterlisLexer.COLON);
        if (colonIndex < 1 || lastIndexOf(lineTokens, InterlisLexer.SEMI) > colonIndex) {
            return null;
        }
        if (!looksLikeTypeDeclaration(lineTokens, colonIndex) || !startsNewClause(allTokens, nextIndex)) {
            return null;
        }
        return typeClauseRange(lineTokens);
    }

    private static Range likelyMissingAttributeHeadRange(ScopeGraph scopeGraph,
                                                         List<LiveToken> lineTokens,
                                                         List<LiveToken> allTokens,
                                                         int nextIndex) {
        if (scopeGraph == null || lineTokens == null || lineTokens.isEmpty()) {
            return null;
        }
        LiveToken first = lineTokens.get(0);
        if (first == null || first.tokenType() != InterlisLexer.Name) {
            return null;
        }
        if (lastIndexOf(lineTokens, InterlisLexer.COLON) >= 0 || lastIndexOf(lineTokens, InterlisLexer.EQ) >= 0) {
            return null;
        }
        if (!looksLikeStandaloneAttributeHead(lineTokens, allTokens, nextIndex)) {
            return null;
        }
        LiveSymbol owner = scopeGraph.findEnclosingContainer(first.range().getStart());
        if (owner == null || !supportsAttributeHeadContext(owner.kind())) {
            return null;
        }
        return first.range();
    }

    private static Range likelyMissingAttributeTypeRange(ScopeGraph scopeGraph,
                                                         List<LiveToken> lineTokens,
                                                         List<LiveToken> allTokens,
                                                         int nextIndex) {
        if (scopeGraph == null || lineTokens == null || lineTokens.isEmpty()) {
            return null;
        }
        LiveToken first = lineTokens.get(0);
        if (first == null || first.tokenType() != InterlisLexer.Name) {
            return null;
        }
        int colonIndex = lastIndexOf(lineTokens, InterlisLexer.COLON);
        if (colonIndex < 1 || colonIndex != lineTokens.size() - 1) {
            return null;
        }
        if (lastIndexOf(lineTokens, InterlisLexer.EQ) >= 0 || lastIndexOf(lineTokens, InterlisLexer.SEMI) > colonIndex) {
            return null;
        }
        if (!looksLikeMissingAttributeType(lineTokens, allTokens, nextIndex)) {
            return null;
        }
        LiveSymbol owner = scopeGraph.findEnclosingContainer(first.range().getStart());
        if (owner == null || !supportsAttributeHeadContext(owner.kind())) {
            return null;
        }
        LiveToken colon = lineTokens.get(colonIndex);
        return new Range(first.range().getStart(), colon.range().getEnd());
    }

    private static boolean looksLikeTypeDeclaration(List<LiveToken> lineTokens, int colonIndex) {
        boolean hasLeadingName = false;
        for (int i = 0; i < colonIndex; i++) {
            LiveToken token = lineTokens.get(i);
            if (token == null) {
                continue;
            }
            if (token.tokenType() == InterlisLexer.EQ) {
                return false;
            }
            if (token.tokenType() == InterlisLexer.Name) {
                hasLeadingName = true;
            }
        }
        if (!hasLeadingName) {
            return false;
        }
        return hasTypeClauseToken(lineTokens, colonIndex + 1);
    }

    private static boolean looksLikeStandaloneAttributeHead(List<LiveToken> lineTokens,
                                                            List<LiveToken> allTokens,
                                                            int nextIndex) {
        if (lineTokens == null || lineTokens.isEmpty()) {
            return false;
        }
        if (lineTokens.size() == 1) {
            return startsNewClause(allTokens, nextIndex);
        }
        return lineTokens.size() == 2 && lineTokens.get(1).tokenType() == InterlisLexer.SEMI;
    }

    private static boolean looksLikeMissingAttributeType(List<LiveToken> lineTokens,
                                                         List<LiveToken> allTokens,
                                                         int nextIndex) {
        if (lineTokens == null || lineTokens.size() < 2) {
            return false;
        }
        if (lineTokens.get(0).tokenType() != InterlisLexer.Name) {
            return false;
        }
        return lineTokens.size() == 2
                && lineTokens.get(1).tokenType() == InterlisLexer.COLON
                && startsNewClause(allTokens, nextIndex);
    }

    private static boolean supportsAttributeHeadContext(InterlisSymbolKind ownerKind) {
        return ownerKind == InterlisSymbolKind.CLASS
                || ownerKind == InterlisSymbolKind.STRUCTURE
                || ownerKind == InterlisSymbolKind.ASSOCIATION;
    }

    private static boolean hasTypeClauseToken(List<LiveToken> lineTokens, int startIndex) {
        for (int i = Math.max(startIndex, 0); i < lineTokens.size(); i++) {
            LiveToken token = lineTokens.get(i);
            if (token == null) {
                continue;
            }
            switch (token.tokenType()) {
                case InterlisLexer.Name, InterlisLexer.MANDATORY, InterlisLexer.FORMAT,
                     InterlisLexer.TEXT, InterlisLexer.MTEXT, InterlisLexer.NAME, InterlisLexer.URI,
                     InterlisLexer.BOOLEAN, InterlisLexer.NUMERIC, InterlisLexer.DATE,
                     InterlisLexer.TIMEOFDAY, InterlisLexer.DATETIME, InterlisLexer.UUIDOID,
                     InterlisLexer.COORD, InterlisLexer.MULTICOORD, InterlisLexer.POLYLINE,
                     InterlisLexer.MULTIPOLYLINE, InterlisLexer.AREA, InterlisLexer.MULTIAREA,
                     InterlisLexer.SURFACE, InterlisLexer.MULTISURFACE, InterlisLexer.REFERENCE,
                     InterlisLexer.BAG, InterlisLexer.LIST, InterlisLexer.CLASS,
                     InterlisLexer.STRUCTURE, InterlisLexer.ATTRIBUTE, InterlisLexer.ANYSTRUCTURE,
                     InterlisLexer.ANYCLASS, InterlisLexer.INTERLIS -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static boolean startsNewClause(List<LiveToken> allTokens, int nextIndex) {
        if (allTokens == null || nextIndex >= allTokens.size()) {
            return true;
        }
        LiveToken next = allTokens.get(nextIndex);
        return next != null && (isHardBoundary(next) || next.tokenType() == InterlisLexer.Name);
    }

    private static int lastIndexOf(List<LiveToken> tokens, int tokenType) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i).tokenType() == tokenType) {
                return i;
            }
        }
        return -1;
    }

    private static Range lineClauseRange(List<LiveToken> tokens, LiveToken anchor) {
        if (anchor == null) {
            return null;
        }
        LiveToken lastOnLine = anchor;
        for (LiveToken token : tokens) {
            if (token.line() != anchor.line()) {
                break;
            }
            if (compare(token.range().getStart(), anchor.range().getStart()) >= 0) {
                lastOnLine = token;
            }
        }
        return new Range(anchor.range().getStart(), lastOnLine.range().getEnd());
    }

    private static Range typeClauseRange(List<LiveToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        int colonIndex = lastIndexOf(tokens, InterlisLexer.COLON);
        if (colonIndex < 0 || colonIndex + 1 >= tokens.size()) {
            return null;
        }

        LiveToken first = null;
        for (int i = colonIndex + 1; i < tokens.size(); i++) {
            LiveToken token = tokens.get(i);
            if (token == null) {
                continue;
            }
            first = token;
            break;
        }
        if (first == null) {
            return null;
        }

        LiveToken lastOnLine = first;
        for (int i = colonIndex + 1; i < tokens.size(); i++) {
            LiveToken token = tokens.get(i);
            if (token == null || token.line() != first.line()) {
                break;
            }
            lastOnLine = token;
        }
        return new Range(first.range().getStart(), lastOnLine.range().getEnd());
    }

    private static int indexBeforeError(List<LiveToken> tokens, RawSyntaxError error) {
        if (tokens == null || tokens.isEmpty()) {
            return -1;
        }
        if (error.offendingTokenIndex() != null) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).tokenIndex() >= error.offendingTokenIndex()) {
                    return Math.max(i - 1, 0);
                }
            }
            return tokens.size() - 1;
        }

        Position position = error.position();
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Range range = tokens.get(i).range();
            if (range != null && compare(range.getStart(), position) <= 0) {
                return i;
            }
        }
        return -1;
    }

    private static Range fallbackRange(DocumentSnapshot snapshot, List<LiveToken> tokens, RawSyntaxError error) {
        if (tokens != null && error.offendingTokenIndex() != null) {
            for (LiveToken token : tokens) {
                if (token.tokenIndex() == error.offendingTokenIndex()) {
                    return token.range();
                }
            }
        }

        Position position = error.position() != null ? error.position() : new Position(0, 0);
        int startOffset = DocumentTracker.toOffset(snapshot.text(), position);
        int lineEnd = lineEndOffset(snapshot.text(), startOffset);
        int endOffset = Math.max(startOffset + 1, lineEnd);
        return new Range(DocumentTracker.positionAt(snapshot.text(), startOffset),
                DocumentTracker.positionAt(snapshot.text(), endOffset));
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

    private static Range expandToEndClause(String text, Range endNameRange) {
        if (text == null || endNameRange == null) {
            return endNameRange;
        }
        int startOffset = DocumentTracker.toOffset(text, endNameRange.getStart());
        int lineStart = lineStartOffset(text, startOffset);
        String line = text.substring(lineStart, Math.min(lineEndOffset(text, startOffset), text.length()));
        int relativeEnd = endNameRange.getStart().getCharacter();
        String prefix = line.substring(0, Math.min(relativeEnd, line.length()));
        int keyword = prefix.toUpperCase(Locale.ROOT).lastIndexOf("END");
        int rangeStart = keyword >= 0 ? lineStart + keyword : startOffset;
        return new Range(DocumentTracker.positionAt(text, rangeStart), endNameRange.getEnd());
    }

    private static String text(String text, Range range) {
        if (text == null || range == null) {
            return "";
        }
        int start = DocumentTracker.toOffset(text, range.getStart());
        int end = DocumentTracker.toOffset(text, range.getEnd());
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        return text.substring(safeStart, safeEnd);
    }

    private static int lineStartOffset(String text, int offset) {
        int index = Math.min(Math.max(offset, 0), text.length());
        while (index > 0) {
            char ch = text.charAt(index - 1);
            if (ch == '\n' || ch == '\r') {
                break;
            }
            index--;
        }
        return index;
    }

    private static int lineEndOffset(String text, int offset) {
        int index = Math.min(Math.max(offset, 0), text.length());
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '\r') {
                break;
            }
            index++;
        }
        return index;
    }

    private static int compare(Position left, Position right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left.getLine() != right.getLine()) {
            return Integer.compare(left.getLine(), right.getLine());
        }
        return Integer.compare(left.getCharacter(), right.getCharacter());
    }

    private record ClauseWindow(List<LiveToken> tokens) {
    }

    private record SpecializedDiagnostic(Range range, String message) {
    }
}
