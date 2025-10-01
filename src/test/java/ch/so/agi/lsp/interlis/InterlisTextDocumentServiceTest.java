package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.DataModel;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void documentSymbolsReflectModelStructure(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelOutline", ".ili");
        String content = String.join("\n",
                "MODEL ModelOutline;",
                "TOPIC TopicA =",
                "  CLASS ClassA =",
                "    attr1 : TEXT;",
                "  END ClassA;",
                "END TopicA;",
                "END ModelOutline.",
                "");
        Files.writeString(modelPath, content);

        DataModel model = new DataModel();
        model.setName("ModelOutline");
        model.setFileName(modelPath.toString());
        model.setSourceLine(1);

        Topic topic = new Topic();
        topic.setName("TopicA");
        topic.setSourceLine(2);
        topic.setContainer(model);
        model.add(topic);

        TestTable table = new TestTable("ClassA", 3);
        table.setContainer(topic);
        topic.add(table);

        AttributeDef attribute = new AttributeDef() {};
        attribute.setName("attr1");
        attribute.setSourceLine(4);
        table.addAttribute(attribute);

        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[]{model};
            }
        };

        Ili2cUtil.CompilationOutcome outcome = new Ili2cUtil.CompilationOutcome(td, "", Collections.emptyList());

        CompilationCache cache = new CompilationCache();
        cache.put(modelPath.toString(), outcome);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());

        InterlisTextDocumentService service = new InterlisTextDocumentService(server, cache, (cfg, path) -> outcome);

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));

        DocumentSymbolParams symbolParams = new DocumentSymbolParams(new TextDocumentIdentifier(item.getUri()));
        List<Either<SymbolInformation, DocumentSymbol>> symbols = service.documentSymbol(symbolParams).get();

        assertEquals(1, symbols.size());
        Either<SymbolInformation, DocumentSymbol> modelEntry = symbols.get(0);
        assertTrue(modelEntry.isRight());
        DocumentSymbol modelSymbol = modelEntry.getRight();
        assertEquals("ModelOutline", modelSymbol.getName());
        assertEquals(SymbolKind.Module, modelSymbol.getKind());
        assertEquals(1, modelSymbol.getChildren().size());

        DocumentSymbol topicSymbol = modelSymbol.getChildren().get(0);
        assertEquals("TopicA", topicSymbol.getName());
        assertEquals(SymbolKind.Namespace, topicSymbol.getKind());
        assertEquals(1, topicSymbol.getChildren().size());

        DocumentSymbol classSymbol = topicSymbol.getChildren().get(0);
        assertEquals("ClassA", classSymbol.getName());
        assertEquals(SymbolKind.Class, classSymbol.getKind());
        assertEquals(1, classSymbol.getChildren().size());

        DocumentSymbol attributeSymbol = classSymbol.getChildren().get(0);
        assertEquals("attr1", attributeSymbol.getName());
        assertEquals(SymbolKind.Property, attributeSymbol.getKind());
        assertEquals(3, attributeSymbol.getSelectionRange().getStart().getLine());
    }

    @Test
    void documentSymbolsIncludeImportedModels(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelImports", ".ili");
        String content = String.join("\n",
                "MODEL ModelImports;",
                "END ModelImports.",
                "");
        Files.writeString(modelPath, content);

        ModelStub imported = new ModelStub("ImportedModel", "ImportedModel.ili");
        ModelStub root = new ModelStub("ModelImports", modelPath.toString(), 1, imported);

        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[]{root};
            }
        };

        Ili2cUtil.CompilationOutcome outcome = new Ili2cUtil.CompilationOutcome(td, "", Collections.emptyList());

        CompilationCache cache = new CompilationCache();
        cache.put(modelPath.toString(), outcome);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());

        InterlisTextDocumentService service = new InterlisTextDocumentService(server, cache, (cfg, path) -> outcome);

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));

        DocumentSymbolParams symbolParams = new DocumentSymbolParams(new TextDocumentIdentifier(item.getUri()));
        List<Either<SymbolInformation, DocumentSymbol>> symbols = service.documentSymbol(symbolParams).get();

        assertEquals(2, symbols.size());

        DocumentSymbol mainSymbol = symbols.get(0).getRight();
        assertNotNull(mainSymbol);
        assertEquals("ModelImports", mainSymbol.getName());

        DocumentSymbol importedSymbol = symbols.get(1).getRight();
        assertNotNull(importedSymbol);
        assertEquals("ImportedModel", importedSymbol.getName());
        assertEquals(SymbolKind.Module, importedSymbol.getKind());
        assertEquals(mainSymbol.getRange().getStart().getLine(), importedSymbol.getRange().getStart().getLine());
        assertEquals(mainSymbol.getSelectionRange().getStart().getLine(), importedSymbol.getSelectionRange().getStart().getLine());
    }

    private static final class TestTable extends Table {
        private final List<ViewableTransferElement> attributes = new ArrayList<>();

        private TestTable(String name, int sourceLine) throws Exception {
            setName(name);
            setSourceLine(sourceLine);
            setIdentifiable(true);
        }

        void addAttribute(AttributeDef attribute) {
            attribute.setContainer(this);
            attributes.add(new ViewableTransferElement(attribute));
        }

        @Override
        public Iterator<ViewableTransferElement> getAttributesAndRoles2() {
            return attributes.iterator();
        }
    }

    private static final class ModelStub extends ch.interlis.ili2c.metamodel.Model {
        private final String name;
        private final String fileName;
        private final ch.interlis.ili2c.metamodel.Model[] imports;
        private final int sourceLine;

        private ModelStub(String name, String fileName, ch.interlis.ili2c.metamodel.Model... imports) {
            this(name, fileName, 1, imports);
        }

        private ModelStub(String name, String fileName, int sourceLine, ch.interlis.ili2c.metamodel.Model... imports) {
            this.name = name;
            this.fileName = fileName;
            this.sourceLine = sourceLine;
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
        public int getSourceLine() {
            return sourceLine;
        }

        @Override
        public ch.interlis.ili2c.metamodel.Model[] getImporting() {
            return imports;
        }
    }
}
