package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    void modelHeaderProducesTemplate() {
        String text = "INTERLIS 2.4;\n\nMODEL ModelA =";
        DocumentOnTypeFormattingParams params = params(2, "MODEL ModelA =".length(), "=");

        List<TextEdit> edits = InterlisAutoCloser.computeEdits(text, params);
        assertEquals(2, edits.size());

        TextEdit banner = edits.get(0);
        assertEquals(new Position(2, 0), banner.getRange().getStart());
        assertEquals(banner.getRange().getStart(), banner.getRange().getEnd());

        String today = LocalDate.now(ZoneId.of("Europe/Zurich")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String expectedBanner = "/** !!------------------------------------------------------------------------------\n" +
                " * !! Version    | wer | Änderung\n" +
                " * !!------------------------------------------------------------------------------\n" +
                " * !! " + today + " | abr  | Initalversion\n" +
                " * !!==============================================================================\n" +
                " */\n" +
                "!!@ technicalContact=mailto:acme@example.com\n" +
                "!!@ furtherInformation=https://example.com/path/to/information\n" +
                "!!@ title=\"a title\"\n" +
                "!!@ shortDescription=\"a short description\"\n" +
                "!!@ tags=\"foo,bar,fubar\"\n";
        assertEquals(expectedBanner, banner.getNewText());

        TextEdit mid = edits.get(1);
        assertEquals(" (de)\n" +
                "  AT \"https://example.com\"\n" +
                "  VERSION \"" + today + "\"\n" +
                "  =\n" +
                InterlisAutoCloser.CARET_SENTINEL + "\n" +
                "END ModelA.", mid.getNewText());
    }

    private static DocumentOnTypeFormattingParams params(int line, int character, String ch) {
        DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams();
        params.setCh(ch);
        params.setPosition(new Position(line, character));
        params.setTextDocument(new TextDocumentIdentifier("file:///doc.ili"));
        return params;
    }
}
