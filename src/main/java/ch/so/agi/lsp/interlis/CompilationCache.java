package ch.so.agi.lsp.interlis;

import java.nio.file.Path;
import java.nio.file.Paths;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        Entry entry = new Entry(outcome);
        entries.put(key, entry);
        indexRelatedFiles(outcome.getTransferDescription(), entry);
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

    private void indexRelatedFiles(TransferDescription td, Entry entry) {
        if (td == null) {
            return;
        }

        Set<String> visited = new HashSet<>();
        for (Model model : td.getModelsFromLastFile()) {
            if (model != null) {
                indexModel(model, entry, visited);
            }
        }
    }

    private void indexModel(Model model, Entry entry, Set<String> visited) {
        if (model == null) {
            return;
        }

        String modelFile = model.getFileName();
        String modelKey = canonicalKey(modelFile);
        if (modelKey != null && visited.add(modelKey)) {
            entries.put(modelKey, entry);
        }

        Model[] imports = model.getImporting();
        if (imports != null) {
            for (Model imported : imports) {
                if (imported != null) {
                    indexModel(imported, entry, visited);
                }
            }
        }
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

