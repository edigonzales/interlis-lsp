package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CompilationCacheTest {

    @Test
    void storesAndRetrievesByCanonicalPath() throws Exception {
        CompilationCache cache = new CompilationCache();
        Path temp = Files.createTempFile("sample", ".ili");

        Ili2cUtil.CompilationOutcome outcome = new Ili2cUtil.CompilationOutcome(
                new TransferDescription() {
                    @Override
                    public ch.interlis.ili2c.metamodel.Model[] getModelsFromLastFile() {
                        return new ch.interlis.ili2c.metamodel.Model[0];
                    }
                },
                "",
                Collections.emptyList());

        cache.put(temp.toString(), outcome);

        // Access via a different representation of the same path
        Ili2cUtil.CompilationOutcome cached = cache.get(temp.toAbsolutePath().normalize().toString());
        assertNotNull(cached);
        assertSame(outcome, cached);
    }

    @Test
    void ignoresEntriesWithoutTransferDescription() throws Exception {
        CompilationCache cache = new CompilationCache();
        Path temp = Files.createTempFile("sample", ".ili");

        Ili2cUtil.CompilationOutcome failed = new Ili2cUtil.CompilationOutcome(null, "", Collections.emptyList());
        cache.put(temp.toString(), failed);

        assertNull(cache.get(temp.toString()));
    }

    @Test
    void definitionFinderUsesCacheWhenAvailable() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        DocumentTracker tracker = new DocumentTracker();
        CompilationCache cache = new CompilationCache();

        Path tempFile = Files.createTempFile("Example", ".ili");
        String uri = tempFile.toUri().toString();
        String source = "MODEL Example;";
        tracker.open(new org.eclipse.lsp4j.TextDocumentItem(uri, "interlis", 1, source));

        AtomicInteger compileCount = new AtomicInteger();

        Ili2cUtil.CompilationOutcome stubOutcome = new Ili2cUtil.CompilationOutcome(
                new TransferDescription() {
                    @Override
                    public ch.interlis.ili2c.metamodel.Model[] getModelsFromLastFile() {
                        return new ch.interlis.ili2c.metamodel.Model[0];
                    }
                },
                "",
                Collections.emptyList());

        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(
                server,
                tracker,
                cache,
                (settings, path) -> {
                    compileCount.incrementAndGet();
                    return stubOutcome;
                });

        org.eclipse.lsp4j.TextDocumentPositionParams params = new org.eclipse.lsp4j.TextDocumentPositionParams();
        params.setTextDocument(new org.eclipse.lsp4j.TextDocumentIdentifier(uri));
        params.setPosition(new org.eclipse.lsp4j.Position(0, 2));

        finder.findDefinition(params);
        finder.findDefinition(params);

        assertEquals(1, compileCount.get(), "Expected compile to run only once due to caching");
    }

    @Test
    void indexesImportedModelFilesForReuse() throws Exception {
        CompilationCache cache = new CompilationCache();

        Path modelA = Files.createTempFile("ModelA", ".ili");
        Path modelB = Files.createTempFile("ModelB", ".ili");

        Model imported = new StubModel("ModelB", modelB.toString());
        Model root = new StubModel("ModelA", modelA.toString(), imported);

        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[]{root};
            }
        };

        Ili2cUtil.CompilationOutcome outcome = new Ili2cUtil.CompilationOutcome(td, "", Collections.emptyList());

        cache.put(modelA.toString(), outcome);

        assertSame(outcome, cache.get(modelA.toString()));
        assertSame(outcome, cache.get(modelB.toString()), "Expected imported model file to be indexed");
    }

    private static final class StubModel extends Model {
        private final String name;
        private final String fileName;
        private final Model[] imports;

        private StubModel(String name, String fileName, Model... imports) {
            this.name = name;
            this.fileName = fileName;
            this.imports = imports != null ? imports : new Model[0];
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public Model[] getImporting() {
            return imports;
        }
    }
}

