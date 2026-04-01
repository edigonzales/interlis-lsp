package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.text.InterlisDocumentSymbolCollector;
import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.DataModel;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterlisTextDocumentServiceTest {

    @Test
    void didOpenCompilesAndStoresFreshSavedAttempt(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelB", ".ili");
        Files.writeString(modelPath, "MODEL ModelB; END ModelB.");

        CompilationCache cache = new CompilationCache();

        Ili2cUtil.CompilationOutcome oldOutcome = new Ili2cUtil.CompilationOutcome(null, "OLD", Collections.emptyList());
        cache.putSavedAttempt(modelPath.toString(), oldOutcome);

        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[0];
            }
        };
        Ili2cUtil.CompilationOutcome freshOutcome = new Ili2cUtil.CompilationOutcome(td, "LOG-1", Collections.emptyList());

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return freshOutcome;
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, "MODEL ModelB; END ModelB.");
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(item);

        service.didOpen(params);

        assertEquals(1, compileCount.get(), "Expected didOpen to compile the saved file authoritatively");
        assertFalse(service.isDocumentDirty(item.getUri()));
        assertEquals("LOG-1", cache.getSavedAttempt(modelPath.toString()).getLogText());
        assertSame(freshOutcome, cache.getSuccessful(modelPath.toString()));
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
    void didOpenBlankFileSkipsCompileAndClearsCachedSnapshots(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "BlankOpen", ".ili");
        Files.writeString(modelPath, " \n\t");

        CompilationCache cache = new CompilationCache();
        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[0];
            }
        };
        cache.putSavedAttempt(modelPath.toString(), new Ili2cUtil.CompilationOutcome(td, "OLD", Collections.emptyList()));
        cache.putSuccessful(modelPath.toString(), new Ili2cUtil.CompilationOutcome(td, "OLD", Collections.emptyList()));

        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return new Ili2cUtil.CompilationOutcome(td, "LOG", Collections.emptyList());
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, " \n\t");
        service.didOpen(new DidOpenTextDocumentParams(item));

        assertEquals(0, compileCount.get(), "Expected blank didOpen to skip compilation");
        assertEquals(1, server.getCompileFinishedCount(), "Expected blank didOpen to still notify compile completion");
        assertFalse(server.wasLastCompileSuccessful(), "Expected blank didOpen to be reported as unsuccessful");
        assertTrue(server.getDiagnostics(item.getUri()).isEmpty(), "Expected blank didOpen to clear diagnostics");
        assertTrue(server.getDebugLogText().contains("SKIP_COMPILE source=didOpen"),
                "Expected blank didOpen to emit a skip marker");
        assertFalse(server.getDebugLogText().contains("REAL_COMPILE source=didOpen"),
                "Expected blank didOpen to avoid real compilation");
        assertNull(cache.getSavedAttempt(modelPath.toString()), "Expected blank didOpen to invalidate saved attempts");
        assertNull(cache.getSuccessful(modelPath.toString()), "Expected blank didOpen to invalidate successful snapshots");
    }

    @Test
    void didChangeReusesLastSuccessfulSnapshotForDocumentSymbols(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelDirtySymbols", ".ili");
        String content = """
                INTERLIS 2.3;
                MODEL ModelDirtySymbols (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC TopicA =
                    CLASS ClassA =
                    END ClassA;
                  END TopicA;
                END ModelDirtySymbols.
                """;
        Files.writeString(modelPath, content);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), modelPath.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return outcome;
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));

        service.didChange(fullDocumentChange(item.getUri(), 2, content + "\n"));

        DocumentSymbolParams symbolParams = new DocumentSymbolParams(new TextDocumentIdentifier(item.getUri()));
        List<Either<SymbolInformation, DocumentSymbol>> symbols = service.documentSymbol(symbolParams).get();

        assertTrue(service.isDocumentDirty(item.getUri()));
        assertEquals(1, compileCount.get(), "Expected dirty document symbols to use the last successful snapshot");
        assertFalse(symbols.isEmpty(), "Expected document symbols to remain available from the saved snapshot");
    }

    @Test
    void didChangeWithoutSuccessfulSnapshotLeavesDocumentSymbolsEmpty(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelDirtyInvalid", ".ili");
        String content = "MODEL ModelDirtyInvalid;";
        Files.writeString(modelPath, content);

        Ili2cUtil.CompilationOutcome failed = new Ili2cUtil.CompilationOutcome(null, "failed", Collections.emptyList());

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return failed;
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));

        service.didChange(fullDocumentChange(item.getUri(), 2, content + "\nTOPIC Broken ="));

        DocumentSymbolParams symbolParams = new DocumentSymbolParams(new TextDocumentIdentifier(item.getUri()));
        List<Either<SymbolInformation, DocumentSymbol>> symbols = service.documentSymbol(symbolParams).get();

        assertTrue(service.isDocumentDirty(item.getUri()));
        assertEquals(1, compileCount.get(), "Expected dirty invalid document to avoid recompilation");
        assertTrue(symbols.isEmpty(), "Expected no document symbols without a successful saved snapshot");
    }

    @Test
    void completionOutsideSupportedSlotDoesNotCompileFallback(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelNoCompletionFallback", ".ili");
        String content = """
                INTERLIS 2.3;
                MODEL ModelNoCompletionFallback (en) AT "http://example.org" VERSION "2024-01-01" =
                END ModelNoCompletionFallback.
                """;
        Files.writeString(modelPath, content);

        CompilationCache cache = new CompilationCache();
        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return new Ili2cUtil.CompilationOutcome(null, "", Collections.emptyList());
                });

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(modelPath.toUri().toString()));
        params.setPosition(new Position(1, 6)); // whitespace after MODEL

        Either<List<CompletionItem>, CompletionList> completion = service.completion(params).get();
        assertTrue(completion.isLeft());
        assertTrue(completion.getLeft().isEmpty());
        assertEquals(0, compileCount.get(), "Expected completion outside supported slots to skip ili2c fallback");
    }

    @Test
    void didChangeUsesSavedSnapshotForImportedLiveDiagnostics(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                    CLASS C =
                      qualifiedAttr : BaseTypes.ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return Ili2cUtil.compile(cfg, path);
                });

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));
        assertEquals(1, compileCount.get(), "Expected didOpen to compile the authoritative snapshot once");

        String dirty = usingContent + "\n";
        service.didChange(fullDocumentChange(uri, 2, dirty));

        waitForDiagnostics(server, uri, 2);

        assertEquals(1, compileCount.get(), "Expected didChange live diagnostics to reuse the saved snapshot");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("ImportedDomain")),
                "Expected imported type to stay valid after whitespace-only dirty edits");
    }

    @Test
    void didChangePublishesUnusedImportWarning(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return Ili2cUtil.compile(cfg, path);
                });

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));
        assertEquals(1, compileCount.get(), "Expected didOpen to compile the authoritative snapshot once");

        String dirty = usingContent + "\n";
        service.didChange(fullDocumentChange(uri, 2, dirty));

        waitForDiagnostics(server, uri, 2);

        assertEquals(1, compileCount.get(), "Expected didChange live diagnostics to reuse the saved snapshot");
        Diagnostic diagnostic = server.getDiagnostics(uri).stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected unused-import warning"));
        assertEquals(DiagnosticSeverity.Warning, diagnostic.getSeverity());
        assertNotNull(diagnostic.getTags());
        assertTrue(diagnostic.getTags().contains(DiagnosticTag.Unnecessary));
    }

    @Test
    void formattingDoesNotRecompileDirtyDocuments(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelDirtyFormat", ".ili");
        String content = """
                INTERLIS 2.4;

                MODEL ModelDirtyFormat (de)
                  AT "https://example.com"
                  VERSION "2025-09-17"
                  =
                END ModelDirtyFormat.
                """;
        Files.writeString(modelPath, content);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), modelPath.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return outcome;
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));

        service.didChange(fullDocumentChange(item.getUri(), 2, content + "\n! unsaved"));

        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        List<? extends TextEdit> edits = service.formatting(params).get();

        assertTrue(service.isDocumentDirty(item.getUri()));
        assertEquals(1, compileCount.get(), "Expected formatting on a dirty document to avoid recompilation");
        assertTrue(edits.isEmpty(), "Expected dirty formatting to no-op instead of applying a stale saved snapshot");
    }

    @Test
    void didChangeUsesSavedSnapshotForPredefinedInterlisLiveDiagnostics(@TempDir Path tempDir) throws Exception {
        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : INTERLIS.XMLDate;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return Ili2cUtil.compile(cfg, path);
                });

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));
        assertEquals(1, compileCount.get(), "Expected didOpen to compile the authoritative snapshot once");

        String dirty = usingContent + "\n";
        service.didChange(fullDocumentChange(uri, 2, dirty));

        waitForDiagnostics(server, uri, 2);

        assertEquals(1, compileCount.get(), "Expected didChange live diagnostics to reuse the saved snapshot");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("INTERLIS.XMLDate")),
                "Expected INTERLIS.XMLDate to stay valid after whitespace-only dirty edits");
    }

    @Test
    void didChangeUsesSavedSnapshotForQualifiedImportedDomainsWithMultipleImports(@TempDir Path tempDir) throws Exception {
        Path geometryFile = tempDir.resolve("GeometryCHLV95_V1.ili");
        Files.writeString(geometryFile, """
                INTERLIS 2.3;
                MODEL GeometryCHLV95_V1 (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN MultiSurface = TEXT*20;
                END GeometryCHLV95_V1.
                """);
        Path textFile = tempDir.resolve("Text.ili");
        Files.writeString(textFile, """
                INTERLIS 2.3;
                MODEL Text (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Label = TEXT*30;
                END Text.
                """);
        Path mathFile = tempDir.resolve("Math.ili");
        Files.writeString(mathFile, """
                INTERLIS 2.3;
                MODEL Math (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Number = 0 .. 10;
                END Math.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS GeometryCHLV95_V1, Text, Math;
                  TOPIC T =
                    CLASS C =
                      attr : GeometryCHLV95_V1.MultiSurface;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return Ili2cUtil.compile(cfg, path);
                });

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));
        assertEquals(1, compileCount.get(), "Expected didOpen to compile the authoritative snapshot once");

        String dirty = usingContent + "\n";
        service.didChange(fullDocumentChange(uri, 2, dirty));

        waitForDiagnostics(server, uri, 2);

        assertEquals(1, compileCount.get(), "Expected didChange live diagnostics to reuse the saved snapshot");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("GeometryCHLV95_V1.MultiSurface")),
                "Expected imported qualified domain to stay valid after whitespace-only dirty edits");
    }

    @Test
    void didChangeKeepsDirectStructureTypesPredefinedUrisAndInlineEnumerationsValid(@TempDir Path tempDir) throws Exception {
        Path modelFile = tempDir.resolve("DirtyTypes.ili");
        String content = """
                INTERLIS 2.4;
                MODEL DirtyTypes (de) AT "https://example.org" VERSION "2026-03-24" =
                  TOPIC Json =
                    STRUCTURE Dokument =
                      Name : TEXT*255;
                    END Dokument;
                    CLASS Example =
                      Inline_Linear : MANDATORY (
                        rot,
                        !!@ ili2db.dispName=grün
                        gruen,
                        gelb
                      );
                      documents : BAG {1..*} OF DirtyTypes.Json.Dokument;
                      document : MANDATORY DirtyTypes.Json.Dokument;
                      url : MANDATORY INTERLIS.URI;
                      uuid : MANDATORY INTERLIS.UUIDOID;
                    END Example;
                  END Json;
                END DirtyTypes.
                """;
        Files.writeString(modelFile, content);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return Ili2cUtil.compile(cfg, path);
                });

        String uri = modelFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, content)));
        assertEquals(1, compileCount.get(), "Expected didOpen to compile the authoritative snapshot once");

        String dirty = content + "\n";
        service.didChange(fullDocumentChange(uri, 2, dirty));

        waitForDiagnostics(server, uri, 2);

        assertEquals(1, compileCount.get(), "Expected didChange live diagnostics to reuse the saved snapshot");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("DirtyTypes.Json.Dokument")),
                "Expected direct qualified structure type to stay valid after whitespace-only dirty edits");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("INTERLIS.URI")),
                "Expected INTERLIS.URI to stay valid after whitespace-only dirty edits");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("INTERLIS.UUIDOID")),
                "Expected INTERLIS.UUIDOID to stay valid after whitespace-only dirty edits");
        assertTrue(server.getDiagnostics(uri).stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Missing ';' after attribute definition")),
                "Expected multiline inline enumerations to stay free of missing-semicolon diagnostics");
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

        assertEquals(1, symbols.size());

        DocumentSymbol mainSymbol = symbols.get(0).getRight();
        assertNotNull(mainSymbol);
        assertEquals("ModelImports", mainSymbol.getName());
        assertEquals(1, mainSymbol.getChildren().size());

        DocumentSymbol importedSymbol = mainSymbol.getChildren().get(0);
        assertEquals("ImportedModel", importedSymbol.getName());
        assertEquals("IMPORT", importedSymbol.getDetail());
        assertEquals(SymbolKind.Module, importedSymbol.getKind());
        assertTrue(importedSymbol.getChildren().isEmpty());
        assertEquals(mainSymbol.getRange().getStart().getLine(), importedSymbol.getRange().getStart().getLine());
        assertEquals(mainSymbol.getSelectionRange().getStart().getLine(), importedSymbol.getSelectionRange().getStart().getLine());
    }

    @Test
    void documentSymbolsOmitTransitiveImportedModels(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelTransitiveImports", ".ili");
        String content = String.join("\n",
                "MODEL ModelTransitiveImports;",
                "END ModelTransitiveImports.",
                "");
        Files.writeString(modelPath, content);

        ModelStub transitive = new ModelStub("TransitiveImport", "TransitiveImport.ili");
        ModelStub direct = new ModelStub("DirectImport", "DirectImport.ili", transitive);
        ModelStub root = new ModelStub("ModelTransitiveImports", modelPath.toString(), 1, direct);

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

        assertEquals(1, symbols.size());

        DocumentSymbol mainSymbol = symbols.get(0).getRight();
        assertNotNull(mainSymbol);
        assertEquals("ModelTransitiveImports", mainSymbol.getName());
        assertEquals(1, mainSymbol.getChildren().size());

        DocumentSymbol importSymbol = mainSymbol.getChildren().get(0);
        assertEquals("DirectImport", importSymbol.getName());
        assertEquals(0, importSymbol.getChildren().size());
    }

    @Test
    void documentSymbolsExpandRangesWhenSourceLineExceedsDocument(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelRange", ".ili");
        String content = String.join("\n",
                "MODEL ModelRange;",
                "END ModelRange.",
                "");
        Files.writeString(modelPath, content);

        ModelStub root = new ModelStub("ModelRange", modelPath.toString(), 200);

        TransferDescription td = new TransferDescription() {
            @Override
            public Model[] getModelsFromLastFile() {
                return new Model[]{root};
            }
        };

        InterlisDocumentSymbolCollector collector = new InterlisDocumentSymbolCollector(content);
        List<DocumentSymbol> symbols = collector.collect(td);

        assertEquals(1, symbols.size());
        DocumentSymbol modelSymbol = symbols.get(0);

        Range range = modelSymbol.getRange();
        Range selection = modelSymbol.getSelectionRange();

        assertNotNull(range);
        assertNotNull(selection);
        assertTrue(rangeContains(range, selection), "Expected selection range to be within the document symbol range");
        assertEquals(0, selection.getStart().getLine(), "Selection should be anchored to the model declaration");
        assertEquals(0, range.getStart().getLine(), "Range should expand to include the selection");
    }

    @Test
    void didSaveForcesRecompileAndPublishesFreshLog(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelSave", ".ili");
        String content = "MODEL ModelSave; END ModelSave.";
        Files.writeString(modelPath, content);

        CompilationCache cache = new CompilationCache();

        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    int count = compileCount.incrementAndGet();
                    TransferDescription td = new TransferDescription() {
                        @Override
                        public Model[] getModelsFromLastFile() {
                            return new Model[0];
                        }
                    };
                    return new Ili2cUtil.CompilationOutcome(td, "LOG-" + count, Collections.emptyList());
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));

        assertEquals(1, compileCount.get(), "Expected initial compile during didOpen");
        assertEquals(1, server.getCompileFinishedCount(), "Expected didOpen to publish compile completion once");
        assertTrue(server.wasLastCompileSuccessful(), "Expected didOpen compile to be marked as successful");
        assertTrue(server.getDebugLogText().contains("REAL_COMPILE source=didOpen"),
                "Expected didOpen to emit a real-compile marker to the debug log");
        assertTrue(server.getLogText().contains("LOG-1"), "Expected didOpen to publish first compile log");

        DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
        saveParams.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        service.didSave(saveParams);

        assertEquals(2, compileCount.get(), "Expected didSave to force recompilation");
        assertEquals(2, server.getCompileFinishedCount(), "Expected didSave to publish compile completion once");
        assertTrue(server.wasLastCompileSuccessful(), "Expected didSave compile to be marked as successful");
        assertTrue(server.getDebugLogText().contains("REAL_COMPILE source=didSave"),
                "Expected didSave to emit a real-compile marker to the debug log");
        assertTrue(server.getLogText().contains("LOG-2"), "Expected didSave to publish fresh compile log");
        assertFalse(service.isDocumentDirty(item.getUri()));

        Ili2cUtil.CompilationOutcome cached = cache.get(modelPath.toString());
        assertNotNull(cached, "Expected compilation outcome to be cached after save");
        assertEquals("LOG-2", cached.getLogText(), "Expected cache to hold the latest compilation outcome");
        assertEquals("LOG-2", cache.getSavedAttempt(modelPath.toString()).getLogText(),
                "Expected saved-attempt cache to track the latest authoritative compile");
    }

    @Test
    void didSaveBlankFileSkipsCompileAndInvalidatesSnapshots(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "ModelBlankSave", ".ili");
        String content = "MODEL ModelBlankSave; END ModelBlankSave.";
        Files.writeString(modelPath, content);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());

        AtomicInteger compileCount = new AtomicInteger();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                (cfg, path) -> {
                    int count = compileCount.incrementAndGet();
                    TransferDescription td = new TransferDescription() {
                        @Override
                        public Model[] getModelsFromLastFile() {
                            return new Model[0];
                        }
                    };
                    return new Ili2cUtil.CompilationOutcome(td, "LOG-" + count, Collections.emptyList());
                });

        TextDocumentItem item = new TextDocumentItem(modelPath.toUri().toString(), "interlis", 1, content);
        service.didOpen(new DidOpenTextDocumentParams(item));
        assertEquals(1, compileCount.get(), "Expected initial compile during didOpen");

        Files.writeString(modelPath, " \n\t");
        service.didChange(fullDocumentChange(item.getUri(), 2, " \n\t"));

        DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
        saveParams.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        service.didSave(saveParams);

        assertEquals(1, compileCount.get(), "Expected blank didSave to skip recompilation");
        assertEquals(2, server.getCompileFinishedCount(), "Expected blank didSave to notify compile completion");
        assertFalse(server.wasLastCompileSuccessful(), "Expected blank didSave to be reported as unsuccessful");
        assertTrue(server.getDiagnostics(item.getUri()).isEmpty(), "Expected blank didSave to clear diagnostics");
        assertTrue(server.getDebugLogText().contains("SKIP_COMPILE source=didSave"),
                "Expected blank didSave to emit a skip marker");
        assertFalse(server.getDebugLogText().contains("REAL_COMPILE source=didSave"),
                "Expected blank didSave to avoid real compilation");
        assertFalse(service.isDocumentDirty(item.getUri()), "Expected blank didSave to mark the document as saved");
        assertNull(cache.getSavedAttempt(modelPath.toString()), "Expected blank didSave to invalidate saved attempts");
        assertNull(cache.getSuccessful(modelPath.toString()), "Expected blank didSave to invalidate successful snapshots");
    }

    @Test
    void didSavePublishesUnusedImportWarningAsLintAfterSuccessfulCompile(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                Ili2cUtil::compile);

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));

        DidSaveTextDocumentParams saveParams = new DidSaveTextDocumentParams();
        saveParams.setTextDocument(new TextDocumentIdentifier(uri));
        service.didSave(saveParams);

        Diagnostic diagnostic = server.getDiagnostics(uri).stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected unused-import warning after didSave"));
        assertEquals(DiagnosticSeverity.Warning, diagnostic.getSeverity());
        assertEquals("lint", diagnostic.getSource());
        assertNotNull(diagnostic.getTags());
        assertTrue(diagnostic.getTags().contains(DiagnosticTag.Unnecessary));
    }

    @Test
    void didSaveDoesNotPublishUnusedImportWarningWhenCompileFails(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModelBroken.ili");
        String brokenContent = """
                INTERLIS 2.3;
                MODEL UsingModelBroken (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                    CLASS C =
                      attr :
                    END C;
                  END T;
                END UsingModelBroken.
                """;
        Files.writeString(usingFile, brokenContent);

        CompilationCache cache = new CompilationCache();
        RecordingServer server = new RecordingServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        InterlisTextDocumentService service = new InterlisTextDocumentService(
                server,
                cache,
                Ili2cUtil::compile);

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, brokenContent)));

        assertTrue(server.getDiagnostics(uri).stream()
                .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("never used")));
    }

    private static DidChangeTextDocumentParams fullDocumentChange(String uri, int version, String text) {
        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, version);
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setText(text);
        return new DidChangeTextDocumentParams(identifier, List.of(change));
    }

    private static void waitForDiagnostics(RecordingServer server, String uri, int expectedPublishCount) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4_000L;
        while (System.currentTimeMillis() < deadline) {
            if (server.getDiagnosticPublishCount(uri) >= expectedPublishCount) {
                return;
            }
            Thread.sleep(25L);
        }
        fail("Timed out waiting for diagnostics for " + uri);
    }

    private static class RecordingServer extends InterlisLanguageServer {
        private final StringBuilder logBuffer = new StringBuilder();
        private final StringBuilder debugLogBuffer = new StringBuilder();
        private final Map<String, List<Diagnostic>> diagnosticsByUri = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> diagnosticPublishCounts = new ConcurrentHashMap<>();
        private int compileFinishedCount;
        private boolean lastCompileSuccessful;

        @Override
        public void clearOutput() {
            logBuffer.setLength(0);
        }

        @Override
        public void logToClient(String text) {
            if (text == null) {
                return;
            }
            logBuffer.append(text);
        }

        @Override
        public void debugLogToClient(String text) {
            if (text == null) {
                return;
            }
            debugLogBuffer.append(text);
        }

        @Override
        public void notifyCompileFinished(String uri, boolean success) {
            compileFinishedCount++;
            lastCompileSuccessful = success;
        }

        @Override
        public void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
            diagnosticsByUri.put(uri, diagnostics != null ? List.copyOf(diagnostics) : List.of());
            diagnosticPublishCounts.computeIfAbsent(uri, ignored -> new AtomicInteger()).incrementAndGet();
        }

        String getLogText() {
            return logBuffer.toString();
        }

        String getDebugLogText() {
            return debugLogBuffer.toString();
        }

        int getCompileFinishedCount() {
            return compileFinishedCount;
        }

        boolean wasLastCompileSuccessful() {
            return lastCompileSuccessful;
        }

        List<Diagnostic> getDiagnostics(String uri) {
            return diagnosticsByUri.getOrDefault(uri, List.of());
        }

        int getDiagnosticPublishCount(String uri) {
            AtomicInteger count = diagnosticPublishCounts.get(uri);
            return count != null ? count.get() : 0;
        }
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

    private static boolean rangeContains(Range outer, Range inner) {
        if (outer == null || inner == null) {
            return false;
        }
        return comparePositions(outer.getStart(), inner.getStart()) <= 0
                && comparePositions(inner.getEnd(), outer.getEnd()) <= 0;
    }

    private static int comparePositions(Position a, Position b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        int lineDiff = Integer.compare(a.getLine(), b.getLine());
        if (lineDiff != 0) {
            return lineDiff;
        }
        return Integer.compare(a.getCharacter(), b.getCharacter());
    }
}
