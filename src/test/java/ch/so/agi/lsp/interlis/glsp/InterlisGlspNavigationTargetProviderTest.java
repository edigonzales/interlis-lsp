package ch.so.agi.lsp.interlis.glsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.glsp.server.features.navigation.NavigationTarget;
import org.eclipse.glsp.server.features.navigation.NavigationTargetProvider;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.eclipse.glsp.server.types.EditorContext;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisGlspNavigationTargetProviderTest {
    @AfterEach
    void cleanup() {
        InterlisGlspBridge.clear();
    }

    @Test
    void resolvesLocalDeclarationWithEditorOpenerOptions(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("ProviderModel.ili");
        String source = """
                INTERLIS 2.3;
                CONTRACTED MODEL ProviderModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Colors = (red, blue);
                  STRUCTURE RootStruct =
                  END RootStruct;
                  FUNCTION Describe (value : TEXT) : TEXT;
                  TOPIC Demo =
                    DOMAIN TopicColors = (one, two);
                    STRUCTURE DemoStruct =
                    END DemoStruct;
                    CLASS Example =
                    END Example;
                    VIEW ExampleView
                      PROJECTION OF Example;
                    =
                      ALL OF Example;
                    END ExampleView;
                  END Demo;
                END ProviderModel.
                """.stripIndent();
        Files.writeString(sourceFile, source);
        String sourceUri = sourceFile.toUri().toString();

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisGlspBridge.bindLanguageServer(server);
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(sourceUri, "interlis", 1, source)));

        var compilation = server.getInterlisTextDocumentService().getLastSavedCompilationAttempt(sourceUri);
        assertTrue(compilation != null && compilation.getTransferDescription() != null,
                compilation != null ? compilation.getLogText() : "missing compilation outcome");
        InterlisDiagramModel.DiagramModel diagram = InterlisDiagramModel.render(compilation.getTransferDescription());
        assertTrue(diagram != null && diagram.getNodes().stream().anyMatch(node -> "ProviderModel.Demo.Example".equals(node.getId())));
        assertTrue(diagram != null && diagram.getNodes().size() >= 7);
        for (InterlisDiagramModel.NodeModel node : diagram.getNodes()) {
            assertTrue(server.getInterlisTextDocumentService().findDeclaration(sourceUri, node.getId()).isPresent(),
                    "No declaration for generated diagram node " + node.getId());
        }

        DefaultGModelState modelState = new DefaultGModelState();
        modelState.setProperty(InterlisGlspModelStateKeys.SOURCE_URI, sourceUri);
        modelState.setProperty(InterlisGlspModelStateKeys.MODEL, diagram);
        InterlisGlspModelFactory factory = new InterlisGlspModelFactory();
        factory.modelState = modelState;
        factory.layoutEngine = Optional.empty();
        factory.createGModel();

        InterlisGlspNavigationTargetProvider provider = new InterlisGlspNavigationTargetProvider();
        provider.modelState = modelState;
        EditorContext context = new EditorContext();
        context.setArgs(Map.of("elementId", "ProviderModel.Demo.Example"));

        List<? extends NavigationTarget> targets = provider.getTargets(context);
        assertEquals(1, targets.size());
        NavigationTarget target = targets.get(0);
        assertEquals(sourceUri, target.getUri());
        assertTrue(target.getElementIds().isEmpty(), "Text navigation must not be resolved as an in-diagram element");

        JsonObject options = JsonParser.parseString(
                target.getArgs().get(NavigationTargetProvider.JSON_OPENER_OPTIONS)).getAsJsonObject();
        assertEquals(-2, options.get("viewColumn").getAsInt());
        assertFalse(options.get("preserveFocus").getAsBoolean());
        JsonObject start = options.getAsJsonObject("selection").getAsJsonObject("start");
        org.eclipse.lsp4j.Position expectedPosition = DocumentTracker.positionAt(source, source.indexOf("Example ="));
        assertEquals(expectedPosition.getLine(), start.get("line").getAsInt());
        assertEquals(expectedPosition.getCharacter(), start.get("character").getAsInt());
    }

    @Test
    void returnsNoTargetForUnknownOrDirtyNode(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("DirtyProviderModel.ili");
        String source = """
                INTERLIS 2.3;
                MODEL DirtyProviderModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC Demo =
                    CLASS Example =
                    END Example;
                  END Demo;
                END DirtyProviderModel.
                """.stripIndent();
        Files.writeString(sourceFile, source);
        String sourceUri = sourceFile.toUri().toString();

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisGlspBridge.bindLanguageServer(server);
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(sourceUri, "interlis", 1, source)));

        InterlisDiagramModel.DiagramModel diagram = InterlisDiagramModel.render(
                server.getInterlisTextDocumentService().getLastSuccessfulCompilation(sourceUri).getTransferDescription());
        DefaultGModelState modelState = new DefaultGModelState();
        modelState.setProperty(InterlisGlspModelStateKeys.SOURCE_URI, sourceUri);
        modelState.setProperty(InterlisGlspModelStateKeys.MODEL, diagram);
        InterlisGlspModelFactory factory = new InterlisGlspModelFactory();
        factory.modelState = modelState;
        factory.layoutEngine = Optional.empty();
        factory.createGModel();

        InterlisGlspNavigationTargetProvider provider = new InterlisGlspNavigationTargetProvider();
        provider.modelState = modelState;
        EditorContext unknown = new EditorContext();
        unknown.setArgs(Map.of("elementId", "DirtyProviderModel.Unknown"));
        assertTrue(provider.getTargets(unknown).isEmpty());

        server.getInterlisTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(sourceUri, 2),
                List.of(new TextDocumentContentChangeEvent(null, source + "\n"))));
        EditorContext known = new EditorContext();
        known.setArgs(Map.of("elementId", "DirtyProviderModel.Demo.Example"));
        assertTrue(provider.getTargets(known).isEmpty());
    }

    @Test
    void resolvesExternalInheritanceNodeToImportedLocalFile(@TempDir Path tempDir) throws Exception {
        Path repositoryDir = Files.createDirectories(tempDir.resolve("models"));
        Path baseFile = repositoryDir.resolve("BaseModel.ili");
        String baseSource = """
                INTERLIS 2.3;
                MODEL BaseModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC BaseTopic =
                    CLASS Base =
                    END Base;
                  END BaseTopic;
                END BaseModel.
                """.stripIndent();
        Files.writeString(baseFile, baseSource);

        Path childFile = repositoryDir.resolve("ChildModel.ili");
        String childSource = """
                INTERLIS 2.3;
                MODEL ChildModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseModel;
                  TOPIC Demo =
                    CLASS Child EXTENDS BaseModel.BaseTopic.Base =
                    END Child;
                  END Demo;
                END ChildModel.
                """.stripIndent();
        Files.writeString(childFile, childSource);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toString());
        server.setClientSettings(settings);
        InterlisGlspBridge.bindLanguageServer(server);
        String childUri = childFile.toUri().toString();
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(childUri, "interlis", 1, childSource)));

        var compilation = server.getInterlisTextDocumentService().getLastSuccessfulCompilation(childUri);
        assertTrue(compilation != null && compilation.getTransferDescription() != null,
                compilation != null ? compilation.getLogText() : "missing compilation outcome");
        InterlisDiagramModel.DiagramModel diagram = InterlisDiagramModel.render(compilation.getTransferDescription());
        assertTrue(diagram.getNodes().stream().anyMatch(node -> "BaseModel.BaseTopic.Base".equals(node.getId())));

        var location = server.getInterlisTextDocumentService()
                .findDeclaration(childUri, "BaseModel.BaseTopic.Base")
                .orElseThrow();
        assertEquals(baseFile.toUri().toString(), location.getUri());
        int nameOffset = baseSource.indexOf("Base =");
        assertEquals(DocumentTracker.positionAt(baseSource, nameOffset), location.getRange().getStart());
        assertEquals(DocumentTracker.positionAt(baseSource, nameOffset + "Base".length()), location.getRange().getEnd());
    }
}
