package ch.so.agi.lsp.interlis.live;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record InterlisLanguageLevel(int major, int minor) {
    public static final InterlisLanguageLevel UNKNOWN = new InterlisLanguageLevel(0, 0);

    private static final Pattern HEADER_PATTERN =
            Pattern.compile("(?i)\\bINTERLIS\\s+([0-9]+)(?:\\.([0-9]+))?\\s*;");

    public static InterlisLanguageLevel detect(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        Matcher matcher = HEADER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return UNKNOWN;
        }
        int major = parsePart(matcher.group(1));
        int minor = parsePart(matcher.group(2));
        if (major <= 0) {
            return UNKNOWN;
        }
        return new InterlisLanguageLevel(major, minor);
    }

    public boolean isAtLeast(int expectedMajor, int expectedMinor) {
        if (major != expectedMajor) {
            return major > expectedMajor;
        }
        return minor >= expectedMinor;
    }

    public boolean supportsNativeDateTypes() {
        return isAtLeast(2, 4);
    }

    public boolean supportsCollectionDomains() {
        return isAtLeast(2, 4);
    }

    private static int parsePart(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
