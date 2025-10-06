package ch.so.agi.lsp.interlis;

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
}
