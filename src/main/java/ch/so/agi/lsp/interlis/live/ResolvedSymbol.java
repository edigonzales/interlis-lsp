package ch.so.agi.lsp.interlis.live;

import org.eclipse.lsp4j.Range;

import java.util.LinkedHashSet;
import java.util.Set;

public record ResolvedSymbol(LiveSymbol symbol,
                             String authoritativeQualifiedName,
                             Set<String> spellings) {
    public String uri() {
        return symbol.uri();
    }

    public String name() {
        return symbol.name();
    }

    public Range nameRange() {
        return symbol.nameRange();
    }

    public Range endRange() {
        return symbol.endRange();
    }

    public String qualifiedName() {
        if (authoritativeQualifiedName != null && !authoritativeQualifiedName.isBlank()) {
            return authoritativeQualifiedName;
        }
        return symbol.qualifiedName();
    }

    public static Set<String> collectSpellings(String qualifiedName, String simpleName) {
        LinkedHashSet<String> spellings = new LinkedHashSet<>();
        if (simpleName != null && !simpleName.isBlank()) {
            spellings.add(simpleName);
        }
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return spellings;
        }

        spellings.add(qualifiedName);
        String[] segments = qualifiedName.split("\\.");
        for (int i = 1; i < segments.length; i++) {
            String suffix = String.join(".", java.util.Arrays.copyOfRange(segments, i, segments.length));
            if (!suffix.isBlank()) {
                spellings.add(suffix);
            }
        }
        return spellings;
    }
}
