package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormattingFeatureTest {

    @Test
    void initializeAdvertisesFormattingCapability() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();

        InitializeResult result = server.initialize(new InitializeParams()).get();
        ServerCapabilities capabilities = result.getCapabilities();

        Either<Boolean, ?> provider = capabilities.getDocumentFormattingProvider();
        assertNotNull(provider);
        assertTrue(provider.isLeft());
        assertTrue(provider.getLeft());
    }

    @Test
    void formattingPrettyPrintsEntireDocument() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisTextDocumentService service = (InterlisTextDocumentService) server.getTextDocumentService();

        Path iliFile = Files.createTempFile("ili-format", ".ili");
        String unformatted = "\n   MODEL Foo;   \nEND Foo.   \n";
        Files.writeString(iliFile, unformatted);

        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(iliFile.toUri().toString()));

        List<? extends TextEdit> edits = service.formatting(params).get();
        assertEquals(1, edits.size());

        TextEdit edit = edits.get(0);
        assertEquals("MODEL Foo;\nEND Foo.\n", edit.getNewText());
        assertEquals(0, edit.getRange().getStart().getLine());
        assertEquals(0, edit.getRange().getStart().getCharacter());
    }

    @Test
    void formattingReturnsNoEditWhenAlreadyPretty() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisTextDocumentService service = (InterlisTextDocumentService) server.getTextDocumentService();

        Path iliFile = Files.createTempFile("ili-format", ".ili");
        String formatted = "MODEL Foo;\nEND Foo.\n";
        Files.writeString(iliFile, formatted);

        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(iliFile.toUri().toString()));

        List<? extends TextEdit> edits = service.formatting(params).get();
        assertTrue(edits.isEmpty());
    }
}
