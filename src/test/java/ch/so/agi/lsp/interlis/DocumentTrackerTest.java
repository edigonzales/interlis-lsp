package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTrackerTest { 

    @Test
    void openStoresInitialContents() {
        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem("file:///doc.ili", "INTERLIS", 1, "CLASS Foo =");
        tracker.open(item);

        assertEquals("CLASS Foo =", tracker.getText("file:///doc.ili"));
        assertEquals(1, tracker.getVersion("file:///doc.ili"));
    }

    @Test
    void applyIncrementalChangesUpdatesText() {
        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem("file:///doc.ili", "INTERLIS", 1, "Hello\nWorld");
        tracker.open(item);

        VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier("file:///doc.ili", 2);
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setRange(new Range(new Position(1, 0), new Position(1, 5)));
        change.setText("Interlis");

        tracker.applyChanges(id, List.of(change));

        assertEquals("Hello\nInterlis", tracker.getText("file:///doc.ili"));
        assertEquals(2, tracker.getVersion("file:///doc.ili"));
    }

    @Test
    void applyFullDocumentSyncReplacesContent() {
        DocumentTracker tracker = new DocumentTracker();
        TextDocumentItem item = new TextDocumentItem("file:///doc.ili", "INTERLIS", 1, "old");
        tracker.open(item);

        VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier("file:///doc.ili", 3);
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setText("new text");

        tracker.applyChanges(id, List.of(change));

        assertEquals("new text", tracker.getText("file:///doc.ili"));
        assertEquals(3, tracker.getVersion("file:///doc.ili"));
    }

    @Test
    void toOffsetAndPositionAtRoundTrip() {
        String text = "line1\r\nline2\nlast";
        int offset = DocumentTracker.toOffset(text, new Position(1, 3));
        Position pos = DocumentTracker.positionAt(text, offset);
        assertEquals(1, pos.getLine());
        assertEquals(3, pos.getCharacter());
    }

    @Test
    void lineStartOffsetFindsRequestedLine() {
        String text = "a\n\nbcd\n";
        assertEquals(3, DocumentTracker.lineStartOffset(text, 2));
        assertEquals(text.length(), DocumentTracker.lineStartOffset(text, 10));
    }

    @Test
    void retrievesContentByCanonicalPath(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Doc.ili");
        Files.writeString(file, "MODEL Doc END Doc.");

        String uri = file.toUri().toString();
        DocumentTracker tracker = new DocumentTracker();
        tracker.open(new TextDocumentItem(uri, "INTERLIS", 1, "MODEL Doc END Doc."));

        String canonicalPath = file.toAbsolutePath().normalize().toString();
        assertEquals("MODEL Doc END Doc.", tracker.getText(canonicalPath));
        assertEquals(1, tracker.getVersion(canonicalPath));
    }
}
