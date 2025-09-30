package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertEquals(1, location.getRange().getStart().getLine());
        assertTrue(location.getRange().getEnd().getCharacter() > location.getRange().getStart().getCharacter());
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
}
