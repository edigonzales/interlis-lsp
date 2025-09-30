package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterlisDefinitionFinderTest {

    @Test
    void resolvesImportedModelToDefinition(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path importedModel = repositoryDir.resolve("BaseModel.ili");
        Files.writeString(importedModel, """
                INTERLIS 2.3;
                MODEL BaseModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC BaseTopic =
                    CLASS Example =
                    END Example;
                  END BaseTopic;
                END BaseModel.
                """.stripIndent());

        Path sourceFile = repositoryDir.resolve("UsingModel.ili");
        String sourceContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS BaseModel;
                  TOPIC UsingTopic =
                  END UsingTopic;
                END UsingModel.
                """.stripIndent();
        Files.writeString(sourceFile, sourceContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem(sourceFile.toUri().toString(), "interlis", 1, sourceContent);
        tracker.open(item);

        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker);

        TextDocumentPositionParams params = new TextDocumentPositionParams();
        params.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        int tokenOffset = sourceContent.indexOf("BaseModel") + 1; // place cursor within the token
        Position cursor = DocumentTracker.positionAt(sourceContent, tokenOffset);
        params.setPosition(cursor);

        Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>> result = finder.findDefinition(params);

        assertTrue(result.isLeft(), "Expected location results");
        List<? extends Location> locations = result.getLeft();
        assertEquals(1, locations.size(), "Expected exactly one definition target");

        Location location = locations.get(0);
        assertEquals(importedModel.toUri().toString(), location.getUri());

        String importedContent = Files.readString(importedModel);
        int startOffset = DocumentTracker.toOffset(importedContent, location.getRange().getStart());
        assertTrue(importedContent.startsWith("BaseModel", startOffset));
    }

    @Test
    void returnsEmptyWhenModelCannotBeResolved(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path sourceFile = repositoryDir.resolve("UsingModel.ili");
        String sourceContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS MissingModel;
                  TOPIC UsingTopic =
                  END UsingTopic;
                END UsingModel.
                """.stripIndent();
        Files.writeString(sourceFile, sourceContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem(sourceFile.toUri().toString(), "interlis", 1, sourceContent);
        tracker.open(item);

        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker);

        TextDocumentPositionParams params = new TextDocumentPositionParams();
        params.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        int tokenOffset = sourceContent.indexOf("MissingModel") + 1;
        Position cursor = DocumentTracker.positionAt(sourceContent, tokenOffset);
        params.setPosition(cursor);

        Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>> result = finder.findDefinition(params);

        assertTrue(result.isLeft(), "Expected location result arm");
        assertTrue(result.getLeft().isEmpty(), "Expected no definition locations");
    }

    @Test
    void resolvesImportedElementWithinModel(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path importedModel = repositoryDir.resolve("ImportedModelA.ili");
        Files.writeString(importedModel, """
                INTERLIS 2.3;
                MODEL ImportedModelA (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC TopicA =
                    STRUCTURE StructureB =
                      attr : TEXT;
                    END StructureB;
                  END TopicA;
                END ImportedModelA.
                """.stripIndent());

        Path sourceFile = repositoryDir.resolve("RootModel.ili");
        String sourceContent = """
                INTERLIS 2.3;
                MODEL RootModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS ImportedModelA;
                  TOPIC RootTopic =
                    CLASS Example =
                      attr1 : ImportedModelA.TopicA.StructureB;
                    END Example;
                  END RootTopic;
                END RootModel.
                """.stripIndent();
        Files.writeString(sourceFile, sourceContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem(sourceFile.toUri().toString(), "interlis", 1, sourceContent);
        tracker.open(item);

        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker);

        TextDocumentPositionParams params = new TextDocumentPositionParams();
        params.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        int tokenOffset = sourceContent.indexOf("StructureB") + 1;
        Position cursor = DocumentTracker.positionAt(sourceContent, tokenOffset);
        params.setPosition(cursor);

        Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>> result = finder.findDefinition(params);
        assertTrue(result.isLeft());
        List<? extends Location> locations = result.getLeft();
        assertEquals(1, locations.size());

        Location location = locations.get(0);
        assertEquals(importedModel.toUri().toString(), location.getUri());

        String importedContent = Files.readString(importedModel);
        int startOffset = DocumentTracker.toOffset(importedContent, location.getRange().getStart());
        assertTrue(importedContent.startsWith("StructureB", startOffset));
    }

    @Test
    void reusesCompilationForRepeatedLookups(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path importedModel = repositoryDir.resolve("BaseModel.ili");
        Files.writeString(importedModel, """
                INTERLIS 2.3;
                MODEL BaseModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC BaseTopic =
                    CLASS Example =
                    END Example;
                  END BaseTopic;
                END BaseModel.
                """.stripIndent());

        Path sourceFile = repositoryDir.resolve("UsingModel.ili");
        String sourceContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS BaseModel;
                  TOPIC UsingTopic =
                  END UsingTopic;
                END UsingModel.
                """.stripIndent();
        Files.writeString(sourceFile, sourceContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem(sourceFile.toUri().toString(), "interlis", 1, sourceContent);
        tracker.open(item);

        CountingCompilationProvider compiler = new CountingCompilationProvider();
        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker, compiler);

        TextDocumentPositionParams params = new TextDocumentPositionParams();
        params.setTextDocument(new TextDocumentIdentifier(item.getUri()));
        int tokenOffset = sourceContent.indexOf("BaseModel") + 1;
        Position cursor = DocumentTracker.positionAt(sourceContent, tokenOffset);
        params.setPosition(cursor);

        finder.findDefinition(params);
        finder.findDefinition(params);

        assertEquals(1, compiler.invocations.get(), "Expected compile to run only once due to caching");
    }

    @Test
    void sharesCachedCompilationWithImportedDocuments(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path importedModel = repositoryDir.resolve("SharedBase.ili");
        String importedContent = """
                INTERLIS 2.3;
                MODEL SharedBase (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC SharedTopic =
                    STRUCTURE SharedExample =
                    END SharedExample;
                  END SharedTopic;
                END SharedBase.
                """.stripIndent();
        Files.writeString(importedModel, importedContent);

        Path rootModel = repositoryDir.resolve("DependentModel.ili");
        String rootContent = """
                INTERLIS 2.3;
                MODEL DependentModel (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS SharedBase;
                  TOPIC DependentTopic =
                    CLASS UsesShared =
                      attr1 : SharedBase.SharedTopic.SharedExample;
                    END UsesShared;
                  END DependentTopic;
                END DependentModel.
                """.stripIndent();
        Files.writeString(rootModel, rootContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem rootItem = new TextDocumentItem(rootModel.toUri().toString(), "interlis", 1, rootContent);
        tracker.open(rootItem);
        TextDocumentItem importedItem = new TextDocumentItem(importedModel.toUri().toString(), "interlis", 1, importedContent);
        tracker.open(importedItem);

        CountingCompilationProvider compiler = new CountingCompilationProvider();
        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker, compiler);

        Ili2cUtil.CompilationOutcome outcome = compiler.compile(settings, rootModel.toAbsolutePath().toString());
        TransferDescription td = outcome.getTransferDescription();
        assertNotNull(td, "Expected compilation to produce a transfer description");
        LinkedHashSet<String> dependencyUris = new LinkedHashSet<>();
        if (td != null) {
            for (Model model : td.getModelsFromLastFile()) {
                if (model == null) {
                    continue;
                }
                collectModelPath(dependencyUris, model.getFileName());

                Model[] imports = model.getImporting();
                if (imports != null) {
                    for (Model imported : imports) {
                        if (imported != null) {
                            collectModelPath(dependencyUris, imported.getFileName());
                        }
                    }
                }
            }
        }

        finder.cacheCompilation(rootItem.getUri(), new ArrayList<>(dependencyUris), outcome);

        String expectedRootKey = Paths.get(InterlisTextDocumentService.toFilesystemPathIfPossible(rootItem.getUri()))
                .toAbsolutePath().normalize().toString();
        String expectedImportedKey = Paths.get(InterlisTextDocumentService.toFilesystemPathIfPossible(importedItem.getUri()))
                .toAbsolutePath().normalize().toString();

        java.lang.reflect.Field cacheField = InterlisDefinitionFinder.class.getDeclaredField("compilationCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ?> cache = (ConcurrentMap<String, ?>) cacheField.get(finder);
        assertTrue(cache.containsKey(expectedRootKey), "Expected root compilation to be cached under normalized path");
        assertTrue(cache.containsKey(expectedImportedKey), "Expected imported compilation to reuse shared cache entry");

        TextDocumentPositionParams rootParams = new TextDocumentPositionParams();
        rootParams.setTextDocument(new TextDocumentIdentifier(rootItem.getUri()));
        int rootOffset = rootContent.indexOf("SharedBase") + 1;
        rootParams.setPosition(DocumentTracker.positionAt(rootContent, rootOffset));
        finder.findDefinition(rootParams);
        assertEquals(1, compiler.invocations.get(), "Expected cached compilation to satisfy root lookup");

        TextDocumentPositionParams importedParams = new TextDocumentPositionParams();
        importedParams.setTextDocument(new TextDocumentIdentifier(importedItem.getUri()));
        int importedOffset = importedContent.indexOf("SharedExample") + 1;
        importedParams.setPosition(DocumentTracker.positionAt(importedContent, importedOffset));
        finder.findDefinition(importedParams);

        assertEquals(1, compiler.invocations.get(), "Expected shared compilation to be reused for dependency lookups");
    }

    @Test
    void invalidatesRootCompilationWhenDependencyChanges(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path dependencyModel = repositoryDir.resolve("Dependency.ili");
        String dependencyContent = """
                INTERLIS 2.3;
                MODEL Dependency (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC DepTopic =
                    CLASS Thing =
                    END Thing;
                  END DepTopic;
                END Dependency.
                """.stripIndent();
        Files.writeString(dependencyModel, dependencyContent);

        Path rootModel = repositoryDir.resolve("Root.ili");
        String rootContent = """
                INTERLIS 2.3;
                MODEL Root (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS Dependency;
                  TOPIC RootTopic =
                    CLASS Usage =
                      attr1 : Dependency.DepTopic.Thing;
                    END Usage;
                  END RootTopic;
                END Root.
                """.stripIndent();
        Files.writeString(rootModel, rootContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem rootItem = new TextDocumentItem(rootModel.toUri().toString(), "interlis", 1, rootContent);
        tracker.open(rootItem);

        TextDocumentItem dependencyItem = new TextDocumentItem(dependencyModel.toUri().toString(), "interlis", 1, dependencyContent);
        tracker.open(dependencyItem);

        CountingCompilationProvider compiler = new CountingCompilationProvider();
        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker, compiler);

        Ili2cUtil.CompilationOutcome outcome = compiler.compile(settings, rootModel.toAbsolutePath().toString());
        LinkedHashSet<String> dependencyUris = new LinkedHashSet<>();
        collectModelPath(dependencyUris, rootModel.toString());
        collectModelPath(dependencyUris, dependencyModel.toString());

        finder.cacheCompilation(rootItem.getUri(), new ArrayList<>(dependencyUris), outcome);

        TextDocumentPositionParams params = new TextDocumentPositionParams();
        params.setTextDocument(new TextDocumentIdentifier(rootItem.getUri()));
        int offset = rootContent.indexOf("Dependency") + 1;
        params.setPosition(DocumentTracker.positionAt(rootContent, offset));

        finder.findDefinition(params);
        assertEquals(1, compiler.invocations.get(), "Expected cached compilation to satisfy initial lookup");

        finder.invalidateDocument(dependencyItem.getUri());

        finder.findDefinition(params);
        assertEquals(2, compiler.invocations.get(), "Expected dependency change to force recompilation of root");
    }

    @Test
    void keepsDependencyCompilationWhenRootCachesSuccessfulOutcome(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path dependencyModel = repositoryDir.resolve("DependentSource.ili");
        String dependencyContent = """
                INTERLIS 2.3;
                MODEL DependentSource (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC DepTopic =
                    CLASS Thing =
                    END Thing;
                  END DepTopic;
                END DependentSource.
                """.stripIndent();
        Files.writeString(dependencyModel, dependencyContent);

        Path rootModel = repositoryDir.resolve("RootConsumer.ili");
        String rootContent = """
                INTERLIS 2.3;
                MODEL RootConsumer (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS DependentSource;
                  TOPIC RootTopic =
                    CLASS Usage =
                      attr1 : DependentSource.DepTopic.Thing;
                    END Usage;
                  END RootTopic;
                END RootConsumer.
                """.stripIndent();
        Files.writeString(rootModel, rootContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem dependencyItem = new TextDocumentItem(dependencyModel.toUri().toString(), "interlis", 2, dependencyContent);
        tracker.open(dependencyItem);
        TextDocumentItem rootItem = new TextDocumentItem(rootModel.toUri().toString(), "interlis", 1, rootContent);
        tracker.open(rootItem);

        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker);

        Ili2cUtil.Message failureMessage = new Ili2cUtil.Message(
                Ili2cUtil.Message.Severity.ERROR,
                dependencyModel.toString(),
                5,
                3,
                "simulated compile failure");
        Ili2cUtil.CompilationOutcome dependencyFailure = new Ili2cUtil.CompilationOutcome(
                null,
                "failure",
                Collections.singletonList(failureMessage));

        finder.cacheCompilation(dependencyItem.getUri(), Collections.emptyList(), dependencyFailure);

        Ili2cUtil.CompilationOutcome rootOutcome = Ili2cUtil.compile(settings, rootModel.toAbsolutePath().toString());
        LinkedHashSet<String> dependencyUris = new LinkedHashSet<>();
        dependencyUris.add(rootModel.toUri().toString());
        dependencyUris.add(dependencyModel.toUri().toString());

        finder.cacheCompilation(rootItem.getUri(), new ArrayList<>(dependencyUris), rootOutcome);

        String expectedDependencyKey = Paths.get(InterlisTextDocumentService.toFilesystemPathIfPossible(dependencyItem.getUri()))
                .toAbsolutePath().normalize().toString();

        java.lang.reflect.Field cacheField = InterlisDefinitionFinder.class.getDeclaredField("compilationCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ?> cache = (ConcurrentMap<String, ?>) cacheField.get(finder);

        Object cachedEntry = cache.get(expectedDependencyKey);
        assertNotNull(cachedEntry, "Expected dependency entry to remain cached");

        java.lang.reflect.Field outcomeField = cachedEntry.getClass().getDeclaredField("outcome");
        outcomeField.setAccessible(true);
        Object cachedOutcome = outcomeField.get(cachedEntry);
        assertSame(dependencyFailure, cachedOutcome, "Expected root caching to preserve dependency compilation outcome");
    }

    @Test
    void skipsProjectingCompilationOntoDependencyWithUnsavedChanges(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));

        Path dependencyModel = repositoryDir.resolve("DependencyWithDraft.ili");
        String dependencyContent = """
                INTERLIS 2.3;
                MODEL DependencyWithDraft (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  TOPIC DraftTopic =
                    CLASS Thing =
                    END Thing;
                  END DraftTopic;
                END DependencyWithDraft.
                """.stripIndent();
        Files.writeString(dependencyModel, dependencyContent);

        Path rootModel = repositoryDir.resolve("DraftConsumer.ili");
        String rootContent = """
                INTERLIS 2.3;
                MODEL DraftConsumer (en) AT \"http://example.org\" VERSION \"2024-01-01\" =
                  IMPORTS DependencyWithDraft;
                  TOPIC DraftTopic =
                    CLASS Usage =
                      attr1 : DependencyWithDraft.DraftTopic.Thing;
                    END Usage;
                  END DraftTopic;
                END DraftConsumer.
                """.stripIndent();
        Files.writeString(rootModel, rootContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem rootItem = new TextDocumentItem(rootModel.toUri().toString(), "interlis", 1, rootContent);
        tracker.open(rootItem);
        TextDocumentItem dependencyItem = new TextDocumentItem(dependencyModel.toUri().toString(), "interlis", 1, dependencyContent);
        tracker.open(dependencyItem);

        InterlisDefinitionFinder finder = new InterlisDefinitionFinder(server, tracker);

        Ili2cUtil.CompilationOutcome rootOutcome = Ili2cUtil.compile(settings, rootModel.toAbsolutePath().toString());
        ArrayList<String> dependencyUris = new ArrayList<>();
        dependencyUris.add(rootItem.getUri());
        dependencyUris.add(dependencyItem.getUri());

        finder.cacheCompilation(rootItem.getUri(), dependencyUris, rootOutcome);

        String dependencyKey = Paths.get(InterlisTextDocumentService.toFilesystemPathIfPossible(dependencyItem.getUri()))
                .toAbsolutePath().normalize().toString();
        String rootKey = Paths.get(InterlisTextDocumentService.toFilesystemPathIfPossible(rootItem.getUri()))
                .toAbsolutePath().normalize().toString();

        java.lang.reflect.Field cacheField = InterlisDefinitionFinder.class.getDeclaredField("compilationCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ?> cache = (ConcurrentMap<String, ?>) cacheField.get(finder);
        assertTrue(cache.containsKey(dependencyKey), "Expected dependency projection to be cached before edits");

        VersionedTextDocumentIdentifier dependencyIdentifier = new VersionedTextDocumentIdentifier();
        dependencyIdentifier.setUri(dependencyItem.getUri());
        dependencyIdentifier.setVersion(2);
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setRange(null);
        change.setText(dependencyContent + "\n!! draft edit");
        tracker.applyChanges(dependencyIdentifier, Collections.singletonList(change));
        finder.invalidateDocument(dependencyItem.getUri());

        finder.cacheCompilation(rootItem.getUri(), dependencyUris, rootOutcome);

        cache = (ConcurrentMap<String, ?>) cacheField.get(finder);
        assertTrue(cache.containsKey(rootKey), "Expected root compilation to remain cached");
        assertFalse(cache.containsKey(dependencyKey), "Expected dependency with unsaved edits to skip projection");

        finder.invalidateDocument(dependencyItem.getUri());
        cache = (ConcurrentMap<String, ?>) cacheField.get(finder);
        assertFalse(cache.containsKey(rootKey), "Dependency invalidation should evict root entry even without projection");
    }

    private void collectModelPath(LinkedHashSet<String> uris, String fileName) {
        if (fileName == null) {
            return;
        }
        String normalizedPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileName);
        Path modelPath = Paths.get(normalizedPath).toAbsolutePath().normalize();
        uris.add(modelPath.toUri().toString());
    }

    private static final class CountingCompilationProvider implements InterlisDefinitionFinder.CompilationProvider {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Ili2cUtil.CompilationOutcome compile(ClientSettings settings, String fileUriOrPath) {
            invocations.incrementAndGet();
            return Ili2cUtil.compile(settings, fileUriOrPath);
        }
    }
}
