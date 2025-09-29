package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-inserts boilerplate when '=' is typed after selected INTERLIS headers.
 * Ported from the jEdit plugin implementation, but adapted to return LSP TextEdits.
 */
final class InterlisAutoCloser {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisAutoCloser.class);

    private static final int LOOKBACK_CHARS = 1200;
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final String CARET_SENTINEL = "__INTERLIS_AUTOCLOSE_CARET__";

    private InterlisAutoCloser() {}

    static List<TextEdit> computeEdits(String text,
                                       DocumentOnTypeFormattingParams params) {
        if (text == null || params == null || params.getCh() == null) {
            return Collections.emptyList();
        }

        if (!"=".equals(params.getCh())) {
            return Collections.emptyList();
        }

        int offset = DocumentTracker.toOffset(text, params.getPosition());
        if (offset <= 0 || offset > text.length()) {
            return Collections.emptyList();
        }

        int eqOffset = offset - 1;
        if (eqOffset < 0 || text.charAt(eqOffset) != '=') {
            // If we cannot see the inserted '=' yet, skip instead of risking corruption
            return Collections.emptyList();
        }

        int tailStart = Math.max(0, eqOffset - LOOKBACK_CHARS);
        String tail = text.substring(tailStart, eqOffset);

        List<Token> tokens = Lexer.lex(tail);
        ParseResult res = Parser.parseHeaderEndingAtTail(tokens);
        if (res == null) {
            return Collections.emptyList();
        }

        String indent = leadingIndent(text, tailStart + res.keywordPosInTail);

        switch (res.kind) {
            case MODEL:
                return modelTemplate(text, params, tailStart, res, indent, eqOffset);
            case VIEW_TOPIC:
                return Collections.singletonList(insertAfter(params.getPosition(),
                        "\n" + indent + "DEPENDS ON " + CARET_SENTINEL
                                + "\n" + indent
                                + "\n" + indent + "END " + res.name + ";"));
            case CLASS:
            case STRUCTURE:
            case TOPIC:
                return Collections.singletonList(insertAfter(params.getPosition(),
                        "\n" + indent + CARET_SENTINEL
                                + "\n" + indent + "END " + res.name + ";"));
            default:
                return Collections.emptyList();
        }
    }

    private static List<TextEdit> modelTemplate(String text,
                                                DocumentOnTypeFormattingParams params,
                                                int tailStart,
                                                ParseResult res,
                                                String indent,
                                                int eqOffset) {
        try {
            String today = LocalDate.now(ZURICH).format(ISO_DAY);

            String banner =
                    "/** !!------------------------------------------------------------------------------\n" +
                            " * !! Version    | wer | Änderung\n" +
                            " * !!------------------------------------------------------------------------------\n" +
                            " * !! " + today + " | abr  | Initalversion\n" +
                            " * !!==============================================================================\n" +
                            " */\n" +
                            "!!@ technicalContact=mailto:acme@example.com\n" +
                            "!!@ furtherInformation=https://example.com/path/to/information\n" +
                            "!!@ title=\"a title\"\n" +
                            "!!@ shortDescription=\"a short description\"\n" +
                            "!!@ tags=\"foo,bar,fubar\"\n";

            int headerLine = Math.max(params.getPosition().getLine(), 0);
            int headerLineStart = DocumentTracker.lineStartOffset(text, headerLine);
            Position headerPos = DocumentTracker.positionAt(text, headerLineStart);

            List<TextEdit> edits = new ArrayList<>();
            edits.add(new TextEdit(new Range(headerPos, headerPos), banner));

            int nameAbsStart = tailStart + res.namePosInTail;
            int nameAbsEnd = nameAbsStart + res.nameLen;
            int removeStart = Math.max(nameAbsEnd, 0);
            int removeEnd = Math.min(Math.max(eqOffset + 1, removeStart), text.length());

            String mid = " (de)\n"
                    + indent + "  AT \"https://example.com\"\n"
                    + indent + "  VERSION \"" + today + "\"\n"
                    + indent + "  =\n"
                    + indent + CARET_SENTINEL + "\n"
                    + indent + "END " + res.name + ".";

            Position removeStartPos = DocumentTracker.positionAt(text, removeStart);
            Position removeEndPos = DocumentTracker.positionAt(text, removeEnd);
            edits.add(new TextEdit(new Range(removeStartPos, removeEndPos), mid));

            return edits;
        } catch (Exception ex) {
            LOG.warn("Failed to build MODEL template edits for {}", res.name, ex);
            return Collections.emptyList();
        }
    }

    private static TextEdit insertAfter(Position position, String text) {
        return new TextEdit(new Range(position, position), text);
    }

    private static String leadingIndent(String text, int absPos) {
        int lineStart = 0;
        int cursor = Math.max(absPos, 0);

        for (int i = Math.min(cursor, text.length()) - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                lineStart = i + 1;
                break;
            }
            if (ch == '\r') {
                lineStart = i + 1;
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    lineStart++;
                }
                break;
            }
        }

        int i = lineStart;
        int len = text.length();
        while (i < len) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t') {
                i++;
            } else {
                break;
            }
        }
        return text.substring(lineStart, i);
    }

    private enum Kind { CLASS, STRUCTURE, TOPIC, VIEW_TOPIC, MODEL }

    private enum T { CLASS, STRUCTURE, TOPIC, VIEW, MODEL, EXTENDS, LPAREN, RPAREN, COMMA, DOT, IDENT }

    private static final class Token {
        final T t;
        final String text;
        final int pos;

        Token(T t, String text, int pos) { this.t = t; this.text = text; this.pos = pos; }
        Token(T t, int pos) { this(t, null, pos); }
    }

    private static final class ParseResult {
        final Kind kind;
        final String name;
        final int keywordPosInTail;
        final int namePosInTail;
        final int nameLen;

        ParseResult(Kind kind, String name, int keywordPosInTail, int namePosInTail, int nameLen) {
            this.kind = kind;
            this.name = name;
            this.keywordPosInTail = keywordPosInTail;
            this.namePosInTail = namePosInTail;
            this.nameLen = nameLen;
        }
    }

    private static final class Lexer {
        static List<Token> lex(String s) {
            ArrayList<Token> out = new ArrayList<>();
            if (s == null || s.isEmpty()) {
                return out;
            }
            int i = 0;
            int n = s.length();
            while (i < n) {
                char c = s.charAt(i);

                if (Character.isWhitespace(c)) { i++; continue; }

                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < n && s.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                    if (i + 1 < n) i += 2;
                    continue;
                }

                if (c == '(') { out.add(new Token(T.LPAREN, i)); i++; continue; }
                if (c == ')') { out.add(new Token(T.RPAREN, i)); i++; continue; }
                if (c == ',') { out.add(new Token(T.COMMA, i)); i++; continue; }
                if (c == '.') { out.add(new Token(T.DOT, i)); i++; continue; }

                if (Character.isLetter(c) || c == '_') {
                    int j = i + 1;
                    while (j < n) {
                        char d = s.charAt(j);
                        if (Character.isLetterOrDigit(d) || d == '_') j++; else break;
                    }
                    String w = s.substring(i, j);
                    if      ("CLASS".equals(w))     out.add(new Token(T.CLASS, i));
                    else if ("STRUCTURE".equals(w)) out.add(new Token(T.STRUCTURE, i));
                    else if ("TOPIC".equals(w))     out.add(new Token(T.TOPIC, i));
                    else if ("VIEW".equals(w))      out.add(new Token(T.VIEW, i));
                    else if ("MODEL".equals(w))     out.add(new Token(T.MODEL, i));
                    else if ("EXTENDS".equals(w))   out.add(new Token(T.EXTENDS, i));
                    else                             out.add(new Token(T.IDENT, w, i));
                    i = j;
                    continue;
                }

                i++;
            }
            return out;
        }
    }

    private static final class Parser {
        private static final Set<String> FLAGS_CLASS = set("ABSTRACT", "EXTENDED", "FINAL");
        private static final Set<String> FLAGS_STRUCTURE = FLAGS_CLASS;
        private static final Set<String> FLAGS_TOPIC = set("ABSTRACT", "FINAL");

        static ParseResult parseHeaderEndingAtTail(List<Token> toks) {
            if (toks == null || toks.isEmpty()) return null;

            ParseResult r = tryViewTopicSuffix(toks);
            if (r != null) return r;

            r = tryHeaderSuffix(toks, T.TOPIC, FLAGS_TOPIC, Kind.TOPIC);
            if (r != null) return r;

            r = tryHeaderSuffix(toks, T.CLASS, FLAGS_CLASS, Kind.CLASS);
            if (r != null) return r;

            r = tryHeaderSuffix(toks, T.STRUCTURE, FLAGS_STRUCTURE, Kind.STRUCTURE);
            if (r != null) return r;

            r = tryModelSuffix(toks);
            return r;
        }

        private static ParseResult tryViewTopicSuffix(List<Token> toks) {
            int n = toks.size();
            for (int i = n - 1; i >= 1; i--) {
                Token tView = toks.get(i - 1);
                Token tTopic = toks.get(i);
                if (tView.t != T.VIEW || tTopic.t != T.TOPIC) continue;
                int p = i + 1;
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name = toks.get(p).text;
                int namePos = toks.get(p).pos;
                int nameLen = name.length();
                p++;
                if (p == n) {
                    return new ParseResult(Kind.VIEW_TOPIC, name, tView.pos, namePos, nameLen);
                }
            }
            return null;
        }

        private static ParseResult tryHeaderSuffix(List<Token> toks, T keyword,
                                                   Set<String> allowedFlags, Kind kind) {
            int n = toks.size();
            for (int i = n - 1; i >= 0; i--) {
                Token kw = toks.get(i);
                if (kw.t != keyword) continue;
                int p = i + 1;
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name = toks.get(p).text;
                int namePos = toks.get(p).pos;
                int nameLen = name.length();
                p++;

                int p2 = parseOptionalFlags(toks, p, allowedFlags);
                if (p2 == -1) continue;
                p = p2;

                if (p < n && toks.get(p).t == T.EXTENDS) {
                    p++;
                    int q = parseQualifiedIdent(toks, p);
                    if (q == -1) continue;
                    p = q;
                }

                if (p == n) {
                    return new ParseResult(kind, name, kw.pos, namePos, nameLen);
                }
            }
            return null;
        }

        private static ParseResult tryModelSuffix(List<Token> toks) {
            int n = toks.size();
            for (int i = n - 1; i >= 0; i--) {
                Token kw = toks.get(i);
                if (kw.t != T.MODEL) continue;
                int p = i + 1;
                if (p >= n || toks.get(p).t != T.IDENT) continue;
                String name = toks.get(p).text;
                int namePos = toks.get(p).pos;
                int nameLen = name.length();
                p++;
                if (p == n) {
                    return new ParseResult(Kind.MODEL, name, kw.pos, namePos, nameLen);
                }
            }
            return null;
        }

        private static int parseOptionalFlags(List<Token> toks, int p, Set<String> allowed) {
            int n = toks.size();
            if (p >= n || toks.get(p).t != T.LPAREN) return p;

            int q = p + 1;
            if (q >= n || !isAllowedFlag(toks.get(q), allowed)) return -1;
            q++;
            while (q < n && toks.get(q).t == T.COMMA) {
                q++;
                if (q >= n || !isAllowedFlag(toks.get(q), allowed)) return -1;
                q++;
            }
            if (q >= n || toks.get(q).t != T.RPAREN) return -1;

            return q + 1;
        }

        private static int parseQualifiedIdent(List<Token> toks, int p) {
            int n = toks.size();
            if (p >= n || toks.get(p).t != T.IDENT) return -1;
            p++;
            while (p + 1 < n && toks.get(p).t == T.DOT && toks.get(p + 1).t == T.IDENT) {
                p += 2;
            }
            return p;
        }

        private static boolean isAllowedFlag(Token tk, Set<String> allowed) {
            return tk.t == T.IDENT && allowed.contains(tk.text);
        }

        private static Set<String> set(String... s) {
            Set<String> out = new HashSet<>();
            Collections.addAll(out, s);
            return out;
        }
    }
}

