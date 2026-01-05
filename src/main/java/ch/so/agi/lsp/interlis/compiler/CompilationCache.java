package ch.so.agi.lsp.interlis.compiler;

import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the last successful {@link Ili2cUtil.CompilationOutcome} per source file so expensive
 * compiler invocations can be reused by features such as go-to-definition.
 */
public final class CompilationCache {

    private final Map<String, Ili2cUtil.CompilationOutcome> entries = new ConcurrentHashMap<>();

    public Ili2cUtil.CompilationOutcome get(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key == null) {
            return null;
        }
        return entries.get(key);
    }

    public void put(String pathOrUri, Ili2cUtil.CompilationOutcome outcome) {
        String key = canonicalKey(pathOrUri);
        if (key == null || outcome == null || outcome.getTransferDescription() == null) {
            return;
        }
        entries.put(key, outcome);
    }

    public void invalidate(String pathOrUri) {
        String key = canonicalKey(pathOrUri);
        if (key != null) {
            entries.remove(key);
        }
    }

    public void clear() {
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
