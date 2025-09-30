package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InterlisTextDocumentServiceTest {

    @Test
    void didOpenUsesExistingCacheEntryForSameDocument(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelB", ".ili");

        CompilationCache cache = new CompilationCache();

        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[0];
            }
        };

        Ili2cUtil.CompilationOutcome outcome = new Ili2cUtil.CompilationOutcome(td, "", Collections.emptyList());
        cache.put(modelPath.toString(), outcome);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return outcome;
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, "MODEL ModelB; END ModelB.");
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(item);

        service.didOpen(params);

        assertEquals(0, compileCount.get(), "Expected cached compilation to be reused on didOpen");
    }

    @Test
    void didOpenCompilesImportedModelWhenNotCached(@TempDir Path tempDir) throws Exception {
        Path modelA = Files.createTempFile(tempDir, "ModelA", ".ili");
        Path modelB = Files.createTempFile(tempDir, "ModelB", ".ili");

        CompilationCache cache = new CompilationCache();

        ModelStub imported = new ModelStub("ModelB", modelB.toString());
        ModelStub root = new ModelStub("ModelA", modelA.toString(), imported);

        TransferDescription tdA = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[]{root};
            }
        };

        Ili2cUtil.CompilationOutcome outcomeA = new Ili2cUtil.CompilationOutcome(tdA, "", Collections.emptyList());
        cache.put(modelA.toString(), outcomeA);

        assertNull(cache.get(modelB.toString()), "Imported model should not be cached implicitly");

        TransferDescription tdB = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[]{imported};
            }
        };

        Ili2cUtil.CompilationOutcome outcomeB = new Ili2cUtil.CompilationOutcome(tdB, "", Collections.emptyList());

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    assertEquals(modelB.toString(), path);
                    compileCount.incrementAndGet();
                    return outcomeB;
                });

        TextDocumentItem item = new TextDocumentItem(modelB.toUri().toString(), "interlis", 1, "MODEL ModelB; END ModelB.");
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(item);

        service.didOpen(params);

        assertEquals(1, compileCount.get(), "Expected imported model to trigger compilation when opened");
    }

    private static final class ModelStub extends ch.interlis.ili2c.metamodel.Model {
        private final String name;
        private final String fileName;
        private final ch.interlis.ili2c.metamodel.Model[] imports;

        private ModelStub(String name, String fileName, ch.interlis.ili2c.metamodel.Model... imports) {
            this.name = name;
            this.fileName = fileName;
            this.imports = imports != null ? imports : new ch.interlis.ili2c.metamodel.Model[0];
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
        public ch.interlis.ili2c.metamodel.Model[] getImporting() {
            return imports;
        }
    }
}
