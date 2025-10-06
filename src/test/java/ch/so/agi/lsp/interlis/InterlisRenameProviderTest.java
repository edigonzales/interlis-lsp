package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

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
}
