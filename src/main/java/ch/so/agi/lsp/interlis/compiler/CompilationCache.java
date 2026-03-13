package ch.so.agi.lsp.interlis.compiler;

import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the last successful {@link Ili2cUtil.CompilationOutcome} per source file, plus the last
 * authoritative saved compile attempt (including failures), so interactive features can avoid
 * recompiling dirty documents against stale on-disk content.
 */
public final class CompilationCache {

    private final Map<String, Ili2cUtil.CompilationOutcome> successfulEntries = new ConcurrentHashMap<>();
    private final Map<String, Ili2cUtil.CompilationOutcome> savedAttempts = new ConcurrentHashMap<>();

    public Ili2cUtil.CompilationOutcome get(String pathOrUri) {
        return getSuccessful(pathOrUri);
    }

    public Ili2cUtil.CompilationOutcome getSuccessful(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key == null) {
            return null;
        }
        return successfulEntries.get(key);
    }

    public void put(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        putSuccessful(pathOrUri, outcome);
    }

    public void putSuccessful(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        String key = canonicalKey(pathOrUri);
        if (key == null || outcome == null || outcome.getTransferDescription() == null) {
            return;
        }
        successfulEntries.put(key, outcome);
    }

    public Ili2cUtil.CompilationOutcome getSavedAttempt(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key == null) {
            return null;
        }
        return savedAttempts.get(key);
    }

    public void putSavedAttempt(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        String key = canonicalKey(pathOrUri);
        if (key == null || outcome == null) {
            return;
        }
        savedAttempts.put(key, outcome);
    }

    public void invalidate(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key != null) {
            successfulEntries.remove(key);
            savedAttempts.remove(key);
        }
    }

    public void clear() {
        successfulEntries.clear();
        savedAttempts.clear();
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
