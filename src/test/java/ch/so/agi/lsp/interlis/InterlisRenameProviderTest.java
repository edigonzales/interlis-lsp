package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import ch.so.agi.lsp.interlis.text.InterlisRenameProvider;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterlisRenameProviderTest {

    @Test
    void collectSpellingsIncludesModelScopedVariants() {
        LinkedHashSet<String> spellings = InterlisRenameProvider.collectSpellings(
                "Thema",
                "SO_ARP_SEin_Konfiguration_20250115.Topic.Thema",
                "Thema");

        assertTrue(spellings.contains("Thema"));
        assertTrue(spellings.contains("Topic.Thema"));
        assertTrue(spellings.contains("SO_ARP_SEin_Konfiguration_20250115.Topic.Thema"));
        assertTrue(spellings.contains("SO_ARP_SEin_Konfiguration_20250115.Thema"));
    }

    @Test
    void computeEditsUpdatesQualifiedReferencesWithAllowedPrefixes() {
        String source = String.join("\n",
                "STRUCTURE Thema =" ,
                "END Thema;",
                "",
                "STRUCTURE MyThema EXTENDS SO_ARP_SEin_Konfiguration_20250115.Thema =",
                "END MyThema;",
                "");

        LinkedHashSet<String> spellings = InterlisRenameProvider.collectSpellings(
                "Thema",
                "SO_ARP_SEin_Konfiguration_20250115.Topic.Thema",
                "Thema");

        int primaryStart = DocumentTracker.toOffset(source, new Position(0, 10));
        int primaryEnd = primaryStart + "Thema".length();

        List<TextEdit> edits = InterlisRenameProvider.computeEdits(source, "Thema", "ThemaFoo", spellings, primaryStart, primaryEnd);

        assertEquals(3, edits.size());

        TextEdit definition = edits.get(0);
        assertEquals("ThemaFoo", definition.getNewText());
        assertEquals(new Range(new Position(0, 10), new Position(0, 15)), definition.getRange());

        TextEdit endStatement = edits.get(1);
        assertEquals("ThemaFoo", endStatement.getNewText());
        assertEquals(new Range(new Position(1, 4), new Position(1, 9)), endStatement.getRange());

        TextEdit extendsClause = edits.get(2);
        assertEquals("SO_ARP_SEin_Konfiguration_20250115.ThemaFoo", extendsClause.getNewText());
        assertEquals(new Range(new Position(3, 26), new Position(3, 66)), extendsClause.getRange());
    }

    @Test
    void computeEditsSkipsOtherDefinitionsWithSameSimpleName() {
        String source = String.join("\n",
                "STRUCTURE Thema =",
                "END Thema;",
                "",
                "STRUCTURE Thema EXTENDS MyModel.Thema =",
                "END Thema;",
                "");

        LinkedHashSet<String> spellings = new LinkedHashSet<>();
        spellings.add("Thema");
        spellings.add("MyModel.Thema");

        int primaryStart = DocumentTracker.toOffset(source, new Position(0, 10));
        int primaryEnd = primaryStart + "Thema".length();

        List<TextEdit> edits = InterlisRenameProvider.computeEdits(source, "Thema", "ThemaFoo", spellings, primaryStart, primaryEnd);

        assertEquals(3, edits.size());

        TextEdit definition = edits.get(0);
        assertEquals("ThemaFoo", definition.getNewText());
        assertEquals(new Range(new Position(0, 10), new Position(0, 15)), definition.getRange());

        TextEdit endStatement = edits.get(1);
        assertEquals("ThemaFoo", endStatement.getNewText());
        assertEquals(new Range(new Position(1, 4), new Position(1, 9)), endStatement.getRange());

        TextEdit extendsClause = edits.get(2);
        assertEquals("MyModel.ThemaFoo", extendsClause.getNewText());
        assertEquals(new Range(new Position(3, 24), new Position(3, 37)), extendsClause.getRange());
    }

    @Test
    void renameUpdatesFullyQualifiedReferencesInCompiledModels() throws Exception {
        String source = String.join("\n",
                "INTERLIS 2.3;",
                "",
                "MODEL SO_ARP_SEin_Konfiguration_20250115 (en) AT \"http://example.org\" VERSION \"2024-01-01\" =",
                "",
                "  TOPIC Configuration =",
                "    STRUCTURE Thema =",
                "    END Thema;",
                "  END Configuration;",
                "",
                "  TOPIC AnotherTopic =",
                "    STRUCTURE UsesThema EXTENDS SO_ARP_SEin_Konfiguration_20250115.Configuration.Thema =",
                "    END UsesThema;",
                "  END AnotherTopic;",
                "",
                "END SO_ARP_SEin_Konfiguration_20250115.");

        Path iliFile = Files.createTempFile("rename-qualified", ".ili");
        Files.writeString(iliFile, source, StandardCharsets.UTF_8);

        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisRenameProvider provider = new InterlisRenameProvider(server, null, new CompilationCache(), Ili2cUtil::compile);

        int tokenOffset = source.indexOf("STRUCTURE Thema") + "STRUCTURE ".length();
        Position position = DocumentTracker.positionAt(source, tokenOffset);

        RenameParams params = new RenameParams();
        params.setTextDocument(new TextDocumentIdentifier(iliFile.toUri().toString()));
        params.setPosition(position);
        params.setNewName("ThemaFoo");

        WorkspaceEdit edit = provider.rename(params);
        assertNotNull(edit);
        Map<String, List<TextEdit>> changes = edit.getChanges();
        assertNotNull(changes);
        assertTrue(changes.containsKey(iliFile.toUri().toString()));

        List<TextEdit> edits = changes.get(iliFile.toUri().toString());
        assertNotNull(edits);
        assertTrue(edits.stream().anyMatch(e -> "SO_ARP_SEin_Konfiguration_20250115.Configuration.ThemaFoo".equals(e.getNewText())),
                "Expected EXTENDS clause to update fully qualified reference");
    }

    @Test
    void renameUsesSavedSnapshotWhenDocumentIsDirty() throws Exception {
        String source = String.join("\n",
                "INTERLIS 2.3;",
                "",
                "MODEL DirtyRename (en) AT \"http://example.org\" VERSION \"2024-01-01\" =",
                "  TOPIC Config =",
                "    STRUCTURE Thema =",
                "    END Thema;",
                "  END Config;",
                "END DirtyRename.");

        Path iliFile = Files.createTempFile("rename-dirty", ".ili");
        Files.writeString(iliFile, source, StandardCharsets.UTF_8);

        DocumentTracker tracker = new DocumentTracker();
        String uri = iliFile.toUri().toString();
        tracker.open(new org.eclipse.lsp4j.TextDocumentItem(uri, "interlis", 1, source));

        CompilationCache cache = new CompilationCache();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ch.so.agi.lsp.interlis.server.ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());
        cache.putSavedAttempt(iliFile.toString(), outcome);
        cache.putSuccessful(iliFile.toString(), outcome);

        org.eclipse.lsp4j.VersionedTextDocumentIdentifier identifier =
                new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(uri, 2);
        org.eclipse.lsp4j.TextDocumentContentChangeEvent change = new org.eclipse.lsp4j.TextDocumentContentChangeEvent();
        change.setText(source + "\n! dirty");
        tracker.applyChanges(identifier, List.of(change));

        AtomicInteger compileCount = new AtomicInteger();
        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisRenameProvider provider = new InterlisRenameProvider(
                server,
                tracker,
                cache,
                (cfg, path) -> {
                    compileCount.incrementAndGet();
                    return outcome;
                });

        int tokenOffset = source.indexOf("STRUCTURE Thema") + "STRUCTURE ".length();
        Position position = DocumentTracker.positionAt(source, tokenOffset);

        RenameParams params = new RenameParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(position);
        params.setNewName("ThemaFoo");

        WorkspaceEdit edit = provider.rename(params);
        assertNotNull(edit);
        assertEquals(0, compileCount.get(), "Expected dirty rename to use the saved snapshot without recompiling");
        assertTrue(edit.getChanges().containsKey(uri));
    }
}
