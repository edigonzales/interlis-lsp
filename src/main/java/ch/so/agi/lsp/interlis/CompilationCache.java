package ch.so.agi.lsp.interlis;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the last successful {@link Ili2cUtil.CompilationOutcome} per source file so expensive
 * compiler invocations can be reused by features such as go-to-definition.
 */
final class CompilationCache {

    private static final class Entry {
        final Ili2cUtil.CompilationOutcome outcome;

        Entry(Ili2cUtil.CompilationOutcome outcome) {
            this.outcome = outcome;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    Ili2cUtil.CompilationOutcome get(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key == null) {
            return null;
        }
        Entry entry = entries.get(key);
        return entry != null ? entry.outcome : null;
    }

    void put(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        String key = canonicalKey(pathOrUri);
        if (key == null || outcome == null || outcome.getTransferDescription() == null) {
            return;
        }
        entries.put(key, new Entry(outcome));
    }

    void invalidate(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key != null) {
            entries.remove(key);
        }
    }

    void clear() {
        entries.clear();
    }

    private static String canonicalKey(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }

        String filesystemPath = InterlisTextDocumentService.toFilesystemPathIfPossible(pathOrUri);
        try {
            Path path = Paths.get(filesystemPath);
            return path.toAbsolutePath().normalize().toString();
        } catch (Exception ex) {
            return filesystemPath;
        }
    }
}

