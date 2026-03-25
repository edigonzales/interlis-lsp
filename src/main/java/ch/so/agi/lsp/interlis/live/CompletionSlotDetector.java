package ch.so.agi.lsp.interlis.live;

import ch.so.agi.lsp.interlis.antlr.InterlisLexer;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompletionSlotDetector {
    private static final Set<InterlisSymbolKind> ATTRIBUTE_TYPE_ROOT_KINDS = EnumSet.of(
            InterlisSymbolKind.DOMAIN, InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> COLLECTION_TARGET_KINDS = EnumSet.of(InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> DOMAIN_REFERENCE_KINDS = EnumSet.of(InterlisSymbolKind.DOMAIN);
    private static final Set<InterlisSymbolKind> REFERENCE_TARGET_KINDS = EnumSet.of(
            InterlisSymbolKind.CLASS, InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW);
    private static final Pattern IMPORTS_CONTEXT_PATTERN = Pattern.compile("(?i)^\\s*IMPORTS\\b([^;]*)$");
    private static final Pattern END_CONTEXT_PATTERN = Pattern.compile("(?i)\\bEND\\s+([A-Za-z0-9_]*)\\s*$");
    private static final Pattern EXTENDS_CONTEXT_PATTERN = Pattern.compile(
            "(?i)\\bEXTENDS\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*$");

    public List<CompletionContext> detect(DocumentSnapshot snapshot,
                                          ScopeGraph scopeGraph,
                                          List<LiveToken> tokens) {
        if (snapshot == null || snapshot.text() == null || snapshot.text().isEmpty()) {
            return List.of();
        }

        String text = snapshot.text();
        List<CompletionContext> contexts = new ArrayList<>();
        int lineStart = 0;
        int lineNumber = 0;
        while (lineStart <= text.length()) {
            int lineEnd = lineStart;
            while (lineEnd < text.length()) {
                char ch = text.charAt(lineEnd);
                if (ch == '\n' || ch == '\r') {
                    break;
                }
                lineEnd++;
            }

            List<LiveToken> lineTokens = tokensOnLine(tokens, lineNumber);
            collectImportsContext(text, lineStart, lineEnd, contexts);
            collectEndContext(text, lineStart, lineEnd, scopeGraph, contexts);
            if (collectExtendsContext(text, lineStart, lineEnd, scopeGraph, contexts)) {
                if (lineEnd >= text.length()) {
                    break;
                }
                lineStart = nextLineStart(text, lineEnd);
                lineNumber++;
                continue;
            }
            collectAttributeContext(text, lineStart, lineEnd, scopeGraph, lineTokens, contexts);

            if (lineEnd >= text.length()) {
                break;
            }
            lineStart = nextLineStart(text, lineEnd);
            lineNumber++;
        }
        return List.copyOf(contexts);
    }

    private void collectImportsContext(String text,
                                       int lineStartOffset,
                                       int lineEndOffset,
                                       List<CompletionContext> contexts) {
        String line = text.substring(lineStartOffset, lineEndOffset);
        Matcher matcher = IMPORTS_CONTEXT_PATTERN.matcher(line);
        if (!matcher.find()) {
            return;
        }
        String segment = matcher.group(1) != null ? matcher.group(1) : "";
        int segmentStart = lineStartOffset + matcher.start(1);
        int lastComma = segment.lastIndexOf(',');
        int tokenRelStart = lastComma >= 0 ? lastComma + 1 : 0;
        while (tokenRelStart < segment.length() && Character.isWhitespace(segment.charAt(tokenRelStart))) {
            tokenRelStart++;
        }
        int replaceStart = segmentStart + tokenRelStart;
        Range range = range(text, replaceStart, lineEndOffset);
        String prefix = line.substring(Math.min(replaceStart - lineStartOffset, line.length())).trim();
        contexts.add(new CompletionContext(
                CompletionContext.Kind.IMPORT_MODEL,
                prefix,
                prefix,
                null,
                range,
                null,
                null,
                null));
    }

    private void collectEndContext(String text,
                                   int lineStartOffset,
                                   int lineEndOffset,
                                   ScopeGraph scopeGraph,
                                   List<CompletionContext> contexts) {
        String line = text.substring(lineStartOffset, lineEndOffset);
        Matcher matcher = END_CONTEXT_PATTERN.matcher(line);
        if (!matcher.find()) {
            return;
        }
        LiveSymbol owner = enclosingOwner(text, scopeGraph, lineStartOffset + matcher.start());
        contexts.add(new CompletionContext(
                CompletionContext.Kind.END_NAME,
                groupValue(matcher, 1),
                groupValue(matcher, 1),
                null,
                groupRange(text, matcher, 1, lineStartOffset, lineEndOffset),
                owner != null ? owner.id() : null,
                null,
                owner != null ? owner.kind() : null));
    }

    private boolean collectExtendsContext(String text,
                                          int lineStartOffset,
                                          int lineEndOffset,
                                          ScopeGraph scopeGraph,
                                          List<CompletionContext> contexts) {
        String line = text.substring(lineStartOffset, lineEndOffset);
        Matcher matcher = EXTENDS_CONTEXT_PATTERN.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        LiveSymbol owner = enclosingOwner(text, scopeGraph, lineStartOffset + matcher.start());
        if (owner == null) {
            return false;
        }
        Set<InterlisSymbolKind> allowedKinds = allowedExtendsKinds(owner.kind());
        if (allowedKinds.isEmpty()) {
            return false;
        }
        contexts.add(buildPathAwareContext(
                CompletionContext.Kind.EXTENDS_TARGET,
                text,
                groupValue(matcher, 1),
                groupRange(text, matcher, 1, lineStartOffset, lineEndOffset),
                owner,
                allowedKinds));
        return true;
    }

    private void collectAttributeContext(String text,
                                         int lineStartOffset,
                                         int lineEndOffset,
                                         ScopeGraph scopeGraph,
                                         List<LiveToken> lineTokens,
                                         List<CompletionContext> contexts) {
        LiveToken colon = lastTokenOfType(lineTokens, InterlisLexer.COLON);
        if (colon == null) {
            return;
        }
        LiveSymbol owner = enclosingOwner(text, scopeGraph, colon.startOffset());
        if (owner == null || !supportsAttributeTypeContext(owner.kind())) {
            return;
        }

        List<LiveToken> suffixTokens = tokensAfter(lineTokens, colon.tokenIndex());
        if (suffixTokens.isEmpty()) {
            contexts.add(new CompletionContext(
                    CompletionContext.Kind.ATTRIBUTE_TYPE_ROOT,
                    "",
                    "",
                    null,
                    range(text, lineEndOffset, lineEndOffset),
                    owner.id(),
                    ATTRIBUTE_TYPE_ROOT_KINDS,
                    owner.kind()));
            return;
        }

        List<LiveToken> effectiveTokens = skipMandatoryPrefix(suffixTokens);
        if (effectiveTokens.isEmpty()) {
            contexts.add(new CompletionContext(
                    CompletionContext.Kind.ATTRIBUTE_TYPE_ROOT,
                    "",
                    "MANDATORY",
                    null,
                    range(text, lineEndOffset, lineEndOffset),
                    owner.id(),
                    ATTRIBUTE_TYPE_ROOT_KINDS,
                    owner.kind()));
            return;
        }

        LiveToken first = effectiveTokens.get(0);
        if (first.tokenType() == InterlisLexer.TEXT || first.tokenType() == InterlisLexer.MTEXT) {
            CompletionContext context = detectTextLengthValueContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
            context = detectTextLengthContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
        }
        if (isInlineNumericToken(first.tokenType())) {
            CompletionContext context = detectInlineNumericUpperBoundContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
            context = detectInlineNumericRangeContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
        }
        if (first.tokenType() == InterlisLexer.FORMAT) {
            CompletionContext context = detectFormatContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
        }
        if (first.tokenType() == InterlisLexer.LIST || first.tokenType() == InterlisLexer.BAG) {
            CompletionContext context = detectCollectionContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
        }
        if (first.tokenType() == InterlisLexer.REFERENCE) {
            CompletionContext context = detectReferenceContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
        }
        if (first.tokenType() == InterlisLexer.CLASS
                || first.tokenType() == InterlisLexer.STRUCTURE
                || first.tokenType() == InterlisLexer.ATTRIBUTE) {
            CompletionContext context = detectMetaTailContext(text, lineEndOffset, owner, effectiveTokens);
            if (context != null) {
                contexts.add(context);
                return;
            }
        }

        int replaceStart = effectiveTokens.get(0).startOffset();
        String subject = text.substring(replaceStart, lineEndOffset).trim();
        Range replaceRange = range(text, replaceStart, lineEndOffset);
        contexts.add(buildPathAwareContext(
                CompletionContext.Kind.ATTRIBUTE_TYPE_ROOT,
                text,
                subject,
                replaceRange,
                owner,
                ATTRIBUTE_TYPE_ROOT_KINDS));
    }

    private CompletionContext detectTextLengthContext(String text,
                                                      int lineEndOffset,
                                                      LiveSymbol owner,
                                                      List<LiveToken> suffixTokens) {
        if (suffixTokens.size() != 1) {
            return null;
        }
        LiveToken keyword = suffixTokens.get(0);
        return new CompletionContext(
                CompletionContext.Kind.TEXT_LENGTH_TAIL,
                "",
                keyword.upperText(),
                null,
                range(text, lineEndOffset, lineEndOffset),
                owner.id(),
                null,
                owner.kind());
    }

    private CompletionContext detectTextLengthValueContext(String text,
                                                           int lineEndOffset,
                                                           LiveSymbol owner,
                                                           List<LiveToken> suffixTokens) {
        if (suffixTokens.size() != 2) {
            return null;
        }
        if ((suffixTokens.get(0).tokenType() != InterlisLexer.TEXT && suffixTokens.get(0).tokenType() != InterlisLexer.MTEXT)
                || suffixTokens.get(1).tokenType() != InterlisLexer.MUL) {
            return null;
        }
        return new CompletionContext(
                CompletionContext.Kind.TEXT_LENGTH_VALUE_TAIL,
                "",
                suffixTokens.get(0).upperText(),
                null,
                range(text, lineEndOffset, lineEndOffset),
                owner.id(),
                null,
                owner.kind());
    }

    private CompletionContext detectInlineNumericRangeContext(String text,
                                                              int lineEndOffset,
                                                              LiveSymbol owner,
                                                              List<LiveToken> suffixTokens) {
        if (suffixTokens.isEmpty()) {
            return null;
        }
        if (suffixTokens.size() > 1 && suffixTokens.get(1).tokenType() == InterlisLexer.DOTDOT) {
            return null;
        }
        if (suffixTokens.size() != 1) {
            return null;
        }
        LiveToken literal = suffixTokens.get(0);
        return new CompletionContext(
                CompletionContext.Kind.INLINE_NUMERIC_RANGE_TAIL,
                "",
                literal.text(),
                null,
                range(text, lineEndOffset, lineEndOffset),
                owner.id(),
                null,
                owner.kind());
    }

    private CompletionContext detectInlineNumericUpperBoundContext(String text,
                                                                   int lineEndOffset,
                                                                   LiveSymbol owner,
                                                                   List<LiveToken> suffixTokens) {
        if (suffixTokens.size() != 2) {
            return null;
        }
        if (!isInlineNumericToken(suffixTokens.get(0).tokenType())
                || suffixTokens.get(1).tokenType() != InterlisLexer.DOTDOT) {
            return null;
        }
        LiveToken literal = suffixTokens.get(0);
        return new CompletionContext(
                CompletionContext.Kind.INLINE_NUMERIC_UPPER_BOUND_TAIL,
                "",
                literal.text(),
                null,
                range(text, lineEndOffset, lineEndOffset),
                owner.id(),
                null,
                owner.kind());
    }

    private CompletionContext detectFormatContext(String text,
                                                  int lineEndOffset,
                                                  LiveSymbol owner,
                                                  List<LiveToken> suffixTokens) {
        if (suffixTokens.isEmpty() || suffixTokens.get(0).tokenType() != InterlisLexer.FORMAT) {
            return null;
        }
        if (suffixTokens.size() == 1) {
            return new CompletionContext(
                    CompletionContext.Kind.FORMAT_TYPE_TARGET,
                    "",
                    "",
                    null,
                    range(text, lineEndOffset, lineEndOffset),
                    owner.id(),
                    DOMAIN_REFERENCE_KINDS,
                    owner.kind());
        }

        LiveToken next = suffixTokens.get(1);
        if (next.tokenType() == InterlisLexer.BASED_ON) {
            return null;
        }

        int replaceStart = next.startOffset();
        String subject = text.substring(replaceStart, lineEndOffset).trim();
        String normalizedSubject = normalizeDottedSubject(subject);
        if (isPortableFormatBoundsTarget(normalizedSubject)) {
            return new CompletionContext(
                    CompletionContext.Kind.FORMAT_BOUNDS_TAIL,
                    "",
                    normalizedSubject,
                    null,
                    range(text, lineEndOffset, lineEndOffset),
                    owner.id(),
                    null,
                    owner.kind());
        }
        return buildPathAwareContext(
                CompletionContext.Kind.FORMAT_TYPE_TARGET,
                text,
                subject,
                range(text, replaceStart, lineEndOffset),
                owner,
                DOMAIN_REFERENCE_KINDS);
    }

    private CompletionContext detectCollectionContext(String text,
                                                     int lineEndOffset,
                                                     LiveSymbol owner,
                                                     List<LiveToken> suffixTokens) {
        LiveToken keyword = suffixTokens.get(0);
        int ofIndex = indexOfTokenType(suffixTokens, InterlisLexer.OF);
        if (ofIndex >= 0) {
            int replaceStart = ofIndex + 1 < suffixTokens.size() ? suffixTokens.get(ofIndex + 1).startOffset() : lineEndOffset;
            String subject = text.substring(replaceStart, lineEndOffset).trim();
            Range replaceRange = range(text, replaceStart, lineEndOffset);
            return buildPathAwareContext(
                    CompletionContext.Kind.COLLECTION_OF_TARGET,
                    text,
                    subject,
                    replaceRange,
                    owner,
                    COLLECTION_TARGET_KINDS);
        }

        int replaceStart = lineEndOffset;
        String prefix = "";
        if (!suffixTokens.isEmpty()) {
            LiveToken last = suffixTokens.get(suffixTokens.size() - 1);
            if (last.tokenType() == InterlisLexer.Name) {
                replaceStart = last.startOffset();
                prefix = text.substring(replaceStart, lineEndOffset).trim();
            }
        }
        String clauseTail = text.substring(keyword.endOffset(), lineEndOffset).trim();
            return new CompletionContext(
                CompletionContext.Kind.COLLECTION_POST_KEYWORD,
                prefix,
                clauseTail,
                null,
                range(text, replaceStart, lineEndOffset),
                owner.id(),
                null,
                owner.kind());
    }

    private CompletionContext detectReferenceContext(String text,
                                                    int lineEndOffset,
                                                    LiveSymbol owner,
                                                    List<LiveToken> suffixTokens) {
        int toIndex = indexOfTokenType(suffixTokens, InterlisLexer.TO);
        if (toIndex < 0) {
            int replaceStart = lineEndOffset;
            String prefix = "";
            LiveToken last = suffixTokens.get(suffixTokens.size() - 1);
            if (last.tokenType() == InterlisLexer.Name) {
                replaceStart = last.startOffset();
                prefix = text.substring(replaceStart, lineEndOffset).trim();
            }
            String subject = text.substring(suffixTokens.get(0).endOffset(), lineEndOffset).trim();
            return new CompletionContext(
                    CompletionContext.Kind.REFERENCE_POST_KEYWORD,
                    prefix,
                    subject,
                    null,
                    range(text, replaceStart, lineEndOffset),
                    owner.id(),
                    null,
                    owner.kind());
        }

        int targetTokenIndex = toIndex + 1;
        while (targetTokenIndex < suffixTokens.size() && suffixTokens.get(targetTokenIndex).tokenType() == InterlisLexer.LPAR) {
            targetTokenIndex++;
        }
        int replaceStart = targetTokenIndex < suffixTokens.size() ? suffixTokens.get(targetTokenIndex).startOffset() : lineEndOffset;
        String subject = text.substring(replaceStart, lineEndOffset).trim();
        return buildPathAwareContext(
                CompletionContext.Kind.REFERENCE_TARGET,
                text,
                subject,
                range(text, replaceStart, lineEndOffset),
                owner,
                REFERENCE_TARGET_KINDS);
    }

    private CompletionContext detectMetaTailContext(String text,
                                                    int lineEndOffset,
                                                    LiveSymbol owner,
                                                    List<LiveToken> suffixTokens) {
        LiveToken keyword = suffixTokens.get(0);
        String tail = text.substring(keyword.endOffset(), lineEndOffset);
        int replaceStart = lineEndOffset;
        String prefix = "";
        int wordStart = trailingWordStart(tail);
        if (wordStart >= 0) {
            replaceStart = keyword.endOffset() + wordStart;
            prefix = tail.substring(wordStart).trim();
        }
        return new CompletionContext(
                CompletionContext.Kind.META_TYPE_TAIL,
                prefix,
                keyword.upperText(),
                null,
                range(text, replaceStart, lineEndOffset),
                owner.id(),
                null,
                owner.kind());
    }

    private CompletionContext buildPathAwareContext(CompletionContext.Kind directKind,
                                                    String text,
                                                    String subject,
                                                    Range replaceRange,
                                                    LiveSymbol owner,
                                                    Set<InterlisSymbolKind> allowedKinds) {
        subject = subject != null ? subject : "";
        String prefix = subject;
        String qualifierPath = null;
        Range effectiveRange = replaceRange;

        if (subject.contains(".")) {
            prefix = subject.substring(subject.lastIndexOf('.') + 1);
            qualifierPath = subject.substring(0, subject.lastIndexOf('.'));
            int replaceEnd = DocumentTracker.toOffset(text, replaceRange.getEnd());
            int replaceStart = Math.max(replaceEnd - prefix.length(), 0);
            effectiveRange = range(text, replaceStart, replaceEnd);
        }

        return new CompletionContext(
                directKind,
                prefix,
                subject,
                qualifierPath,
                effectiveRange,
                owner != null ? owner.id() : null,
                allowedKinds,
                owner != null ? owner.kind() : null);
    }

    private static int nextLineStart(String text, int lineEnd) {
        if (lineEnd >= text.length()) {
            return lineEnd;
        }
        if (text.charAt(lineEnd) == '\r' && lineEnd + 1 < text.length() && text.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private static int trailingWordStart(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        int index = text.length() - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        int end = index;
        while (index >= 0 && (Character.isLetterOrDigit(text.charAt(index)) || text.charAt(index) == '_')) {
            index--;
        }
        return end >= 0 ? index + 1 : -1;
    }

    private static List<LiveToken> skipMandatoryPrefix(List<LiveToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.get(0).tokenType() == InterlisLexer.MANDATORY) {
            return tokens.size() == 1 ? List.of() : new ArrayList<>(tokens.subList(1, tokens.size()));
        }
        return tokens;
    }

    private static boolean isInlineNumericToken(int tokenType) {
        return tokenType == InterlisLexer.PosNumber || tokenType == InterlisLexer.Dec || tokenType == InterlisLexer.Number;
    }

    private static String normalizeDottedSubject(String subject) {
        if (subject == null) {
            return "";
        }
        return subject.trim().replaceAll("\\s+", "");
    }

    private static boolean isPortableFormatBoundsTarget(String subject) {
        String upper = subject != null ? subject.toUpperCase(Locale.ROOT) : "";
        return "INTERLIS.XMLDATE".equals(upper)
                || "INTERLIS.XMLDATETIME".equals(upper)
                || "INTERLIS.XMLTIME".equals(upper);
    }

    private static LiveToken lastTokenOfType(List<LiveToken> tokens, int tokenType) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i).tokenType() == tokenType) {
                return tokens.get(i);
            }
        }
        return null;
    }

    private static int indexOfTokenType(List<LiveToken> tokens, int tokenType) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).tokenType() == tokenType) {
                return i;
            }
        }
        return -1;
    }

    private static List<LiveToken> tokensAfter(List<LiveToken> tokens, int tokenIndex) {
        List<LiveToken> result = new ArrayList<>();
        for (LiveToken token : tokens) {
            if (token.tokenIndex() > tokenIndex) {
                result.add(token);
            }
        }
        return result;
    }

    private static int tokenOrEndStart(List<LiveToken> tokens, int lineEndOffset) {
        if (tokens.isEmpty()) {
            return lineEndOffset;
        }
        LiveToken last = tokens.get(tokens.size() - 1);
        if (last.tokenType() == InterlisLexer.Name) {
            return last.startOffset();
        }
        return lineEndOffset;
    }

    private static List<LiveToken> tokensOnLine(List<LiveToken> tokens, int lineNumber) {
        List<LiveToken> result = new ArrayList<>();
        if (tokens == null) {
            return result;
        }
        for (LiveToken token : tokens) {
            if (token.line() == lineNumber) {
                result.add(token);
            }
        }
        return result;
    }

    private static LiveSymbol enclosingOwner(String text, ScopeGraph scopeGraph, int offset) {
        if (scopeGraph == null || text == null) {
            return null;
        }
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        return scopeGraph.findEnclosingContainer(DocumentTracker.positionAt(text, safeOffset));
    }

    private static String groupValue(Matcher matcher, int groupIndex) {
        if (matcher == null || groupIndex > matcher.groupCount()) {
            return "";
        }
        String value = matcher.group(groupIndex);
        return value != null ? value : "";
    }

    private static Range groupRange(String text,
                                    Matcher matcher,
                                    int groupIndex,
                                    int lineStartOffset,
                                    int lineEndOffset) {
        if (matcher == null || groupIndex > matcher.groupCount() || matcher.group(groupIndex) == null) {
            return range(text, lineEndOffset, lineEndOffset);
        }
        return range(text, lineStartOffset + matcher.start(groupIndex), lineStartOffset + matcher.end(groupIndex));
    }

    private static Range range(String text, int startOffset, int endOffset) {
        int safeStart = Math.max(0, Math.min(startOffset, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(endOffset, text.length()));
        return new Range(
                DocumentTracker.positionAt(text, safeStart),
                DocumentTracker.positionAt(text, safeEnd));
    }

    private static Set<InterlisSymbolKind> allowedExtendsKinds(InterlisSymbolKind ownerKind) {
        if (ownerKind == null) {
            return Set.of();
        }
        return switch (ownerKind) {
            case CLASS -> EnumSet.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE);
            case STRUCTURE -> EnumSet.of(InterlisSymbolKind.STRUCTURE);
            case ASSOCIATION -> EnumSet.of(InterlisSymbolKind.ASSOCIATION);
            case VIEW -> EnumSet.of(InterlisSymbolKind.VIEW);
            default -> Set.of();
        };
    }

    private static boolean supportsAttributeTypeContext(InterlisSymbolKind ownerKind) {
        return ownerKind == InterlisSymbolKind.CLASS
                || ownerKind == InterlisSymbolKind.STRUCTURE
                || ownerKind == InterlisSymbolKind.ASSOCIATION;
    }
}
