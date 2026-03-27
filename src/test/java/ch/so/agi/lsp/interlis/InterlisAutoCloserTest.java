package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.text.InterlisAutoCloser;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterlisAutoCloserTest {

    @Test
    void returnsEmptyWhenTriggerIsNotEquals() {
        DocumentOnTypeFormattingParams params = params(0, 0, ";");
        List<TextEdit> edits = InterlisAutoCloser.computeEdits("CLASS Foo =", params);
        assertTrue(edits.isEmpty());
    }

    @Test
    void classHeaderGetsBlankLineAndEnd() {
        String text = "  CLASS Foo =";
        DocumentOnTypeFormattingParams params = params(0, text.length(), "=");

        List<TextEdit> edits = InterlisAutoCloser.computeEdits(text, params);
        assertEquals(1, edits.size());

        TextEdit edit = edits.get(0);
        assertEquals(new Range(new Position(0, text.length()), new Position(0, text.length())), edit.getRange());
        assertEquals("\n  " + InterlisAutoCloser.CARET_SENTINEL + "\n  END Foo;", edit.getNewText());
    }

    @Test
    void viewTopicHeaderGetsDependsOnBlock() {
        String text = "VIEW TOPIC TopicA =";
        DocumentOnTypeFormattingParams params = params(0, text.length(), "=");

        List<TextEdit> edits = InterlisAutoCloser.computeEdits(text, params);
        assertEquals(1, edits.size());
        assertEquals("\nDEPENDS ON " + InterlisAutoCloser.CARET_SENTINEL + "\n\nEND TopicA;", edits.get(0).getNewText());
    }

    @Test
    void modelHeaderNoLongerAutoCloses() {
        String text = "INTERLIS 2.4;\n\nMODEL ModelA =";
        DocumentOnTypeFormattingParams params = params(2, "MODEL ModelA =".length(), "=");

        List<TextEdit> edits = InterlisAutoCloser.computeEdits(text, params);
        assertTrue(edits.isEmpty());
    }

    private static DocumentOnTypeFormattingParams params(int line, int character, String ch) {
        DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams();
        params.setCh(ch);
        params.setPosition(new Position(line, character));
        params.setTextDocument(new TextDocumentIdentifier("file:///doc.ili"));
        return params;
    }
}
