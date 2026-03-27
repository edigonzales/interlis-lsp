package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterlisLiveEditingFeatureTest {

    @Test
    void completionSuppressesNormalSymbolItemsInDirtyTypeRootContext(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("LiveCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL LiveCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE LocalStruct =
                  END LocalStruct;

                  DOMAIN LocalDomain = TEXT;

                  TOPIC T =
                    CLASS C =
                      attr : LocalDomain;
                    END C;
                  END T;
                END LiveCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : LocalDomain;", "attr : Lo");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : Lo") + "attr : Lo".length());
        assertFalse(labels.contains("LocalDomain"));
        assertFalse(labels.contains("LocalStruct"));
        assertFalse(labels.contains("Lo"));
    }

    @Test
    void completionDoesNotOfferVisibleOrLaterDeclaredTypesInAttributeRootContext(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("StrictVisibility.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL StrictVisibility (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS Early =
                      attr : TEXT;
                    END Early;

                    STRUCTURE Gruppe =
                    END Gruppe;

                    CLASS Late =
                      attr : Gruppe;
                    END Late;
                  END T;
                END StrictVisibility.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String earlyDirty = valid.replace("attr : TEXT;", "attr : Gr");
        service.didChange(fullDocumentChange(uri, 2, earlyDirty));
        List<String> earlyLabels = completionLabels(service, uri, earlyDirty,
                earlyDirty.indexOf("attr : Gr") + "attr : Gr".length());
        assertFalse(earlyLabels.contains("Gruppe"));

        String lateDirty = valid.replace("attr : Gruppe;", "attr : Gr");
        service.didChange(fullDocumentChange(uri, 3, lateDirty));
        int lateOffset = lateDirty.lastIndexOf("attr : Gr") + "attr : Gr".length();
        List<String> lateLabels = completionLabels(service, uri, lateDirty, lateOffset);
        assertFalse(lateLabels.contains("Gruppe"));
    }

    @Test
    void completionDoesNotOfferQualifiedSymbolChildrenInAttributeRootContext(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("StrictQualifiedVisibility.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL StrictQualifiedVisibility (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS Early =
                      attr : TEXT;
                    END Early;

                    STRUCTURE Gruppe =
                    END Gruppe;

                    CLASS Late =
                      attr : StrictQualifiedVisibility.T.Gruppe;
                    END Late;
                  END T;
                END StrictQualifiedVisibility.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String earlyDirty = valid.replace("attr : TEXT;", "attr : StrictQualifiedVisibility.T.");
        service.didChange(fullDocumentChange(uri, 2, earlyDirty));
        List<String> earlyLabels = completionLabels(service, uri, earlyDirty,
                earlyDirty.indexOf("StrictQualifiedVisibility.T.") + "StrictQualifiedVisibility.T.".length());
        assertFalse(earlyLabels.contains("Gruppe"));

        String lateDirty = valid.replace("attr : StrictQualifiedVisibility.T.Gruppe;", "attr : StrictQualifiedVisibility.T.");
        service.didChange(fullDocumentChange(uri, 3, lateDirty));
        int lateOffset = lateDirty.lastIndexOf("StrictQualifiedVisibility.T.") + "StrictQualifiedVisibility.T.".length();
        List<String> lateLabels = completionLabels(service, uri, lateDirty, lateOffset);
        assertFalse(lateLabels.contains("Gruppe"));
    }

    @Test
    void completionKeepsAttributeTypeSuggestionsAfterSpace(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("LiveCompletionSpace.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL LiveCompletionSpace (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE LocalStruct =
                  END LocalStruct;

                  DOMAIN LocalDomain = TEXT;

                  TOPIC T =
                    CLASS Existing =
                    END Existing;

                    CLASS C =
                      attr : LocalDomain;
                    END C;
                  END T;
                END LiveCompletionSpace.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : LocalDomain;", "attr : ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : ") + "attr : ".length());
        assertFalse(labels.contains("LocalDomain"));
        assertFalse(labels.contains("LocalStruct"));
        assertFalse(labels.contains("CLASS"), "Root type completion should not suggest CLASS by default");
        assertTrue(labels.contains("MANDATORY"));
        assertTrue(labels.contains("FORMAT"));
        assertTrue(labels.contains("BAG"));
        assertTrue(labels.contains("LIST"));
        assertTrue(labels.contains("INTERLIS.XMLDate"));
        assertTrue(labels.contains("INTERLIS.XMLDateTime"));
        assertTrue(labels.contains("INTERLIS.XMLTime"));
        assertFalse(labels.contains("DATE"));
        assertFalse(labels.contains("TIMEOFDAY"));
        assertFalse(labels.contains("DATETIME"));
    }

    @Test
    void completionOffersMetaTypeKeywordsOnlyWhenPrefixed(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("LiveCompletionMeta.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL LiveCompletionMeta (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END LiveCompletionMeta.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyClass = valid.replace("attr : TEXT;", "attr : Cl");
        service.didChange(fullDocumentChange(uri, 2, dirtyClass));
        List<String> classLabels = completionLabels(service, uri, dirtyClass,
                dirtyClass.indexOf("attr : Cl") + "attr : Cl".length());
        assertTrue(classLabels.contains("CLASS"));

        String dirtyReference = valid.replace("attr : TEXT;", "attr : Re");
        service.didChange(fullDocumentChange(uri, 3, dirtyReference));
        List<String> referenceLabels = completionLabels(service, uri, dirtyReference,
                dirtyReference.indexOf("attr : Re") + "attr : Re".length());
        assertTrue(referenceLabels.contains("REFERENCE"));
    }

    @Test
    void completionRestrictsReferenceTargets(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ReferenceCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL ReferenceCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    ASSOCIATION Link =
                    END Link;

                    CLASS Target =
                    END Target;

                    CLASS C =
                      Ref : REFERENCE TO Target;
                    END C;
                  END T;
                END ReferenceCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("Ref : REFERENCE TO Target;", "Ref : REFERENCE TO ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("Ref : REFERENCE TO ") + "Ref : REFERENCE TO ".length());
        assertTrue(labels.contains("Target"));
        assertTrue(labels.contains("Link"));
        assertTrue(labels.contains("ANYCLASS"));
        assertFalse(labels.contains("TEXT"));
    }

    @Test
    void completionAfterListOffersCollectionContinuations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("CollectionContinuation.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL CollectionContinuation (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END CollectionContinuation.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : LIST");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : LIST") + "attr : LIST".length());
        assertTrue(labels.contains("{"));
        assertTrue(labels.contains("OF"));
    }

    @Test
    void completionAfterCollectionCardinalityOnlyOffersOf(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("CollectionCardinality.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL CollectionCardinality (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END CollectionCardinality.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : LIST {1..*}");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : LIST {1..*}") + "attr : LIST {1..*}".length());
        assertTrue(labels.contains("OF"));
        assertFalse(labels.contains("{"));
    }

    @Test
    void completionAfterListOfRestrictsTargets(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("CollectionTargets.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL CollectionTargets (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE LocalStruct =
                  END LocalStruct;

                  DOMAIN LocalDomain = TEXT;

                  TOPIC T =
                    CLASS LocalClass =
                    END LocalClass;

                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END CollectionTargets.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : LIST OF ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : LIST OF ") + "attr : LIST OF ".length());
        assertTrue(labels.contains("LocalStruct"));
        assertTrue(labels.contains("ANYSTRUCTURE"));
        assertFalse(labels.contains("LocalDomain"));
        assertFalse(labels.contains("LocalClass"));
        assertFalse(labels.contains("TEXT"));
    }

    @Test
    void completionAfterReferenceOffersTo(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ReferencePostKeyword.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL ReferencePostKeyword (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END ReferencePostKeyword.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : REFERENCE");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : REFERENCE") + "attr : REFERENCE".length());
        assertTrue(labels.contains("TO"));
    }

    @Test
    void completionAfterMetaTypeOffersTailKeywords(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("MetaTypeTail.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL MetaTypeTail (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END MetaTypeTail.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : ATTRIBUTE ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : ATTRIBUTE ") + "attr : ATTRIBUTE ".length());
        assertTrue(labels.contains("OF"));
        assertTrue(labels.contains("RESTRICTION"));
    }

    @Test
    void completionProvidesSnippetItemsInAttributeRoot(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("RootSnippets.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL RootSnippets (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END RootSnippets.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<CompletionItem> items = completionItems(service, uri, dirty,
                dirty.indexOf("attr : ") + "attr : ".length());
        CompletionItem snippet = items.stream()
                .filter(item -> "LIST OF ...".equals(item.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected LIST OF snippet"));
        assertEquals(InsertTextFormat.Snippet, snippet.getInsertTextFormat());
        assertNotNull(findItemByLabel(items, "BAG OF ..."));
        assertNotNull(findItemByLabel(items, "BAG {...} OF ..."));
        assertNull(findItemByLabel(items, "ATTRIBUTE OF ..."));
    }

    @Test
    void completionAfterMandatoryKeepsTypeSuggestionsWithoutRepeatingMandatory(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("MandatoryCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL MandatoryCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN LocalDomain = TEXT;

                  TOPIC T =
                    CLASS C =
                      attr : LocalDomain;
                    END C;
                  END T;
                END MandatoryCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : LocalDomain;", "attr : MANDATORY ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : MANDATORY ") + "attr : MANDATORY ".length());
        assertFalse(labels.contains("LocalDomain"));
        assertTrue(labels.contains("FORMAT"));
        assertFalse(labels.contains("MANDATORY"));
    }

    @Test
    void completionOnBlankTopicLineOffersOnlyAllowedTopicStarts(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicBodyCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicBodyCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    
                  END T;
                END TopicBodyCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("  END T;") - 1;
        List<CompletionItem> items = completionItems(service, uri, valid, offset);
        List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

        assertTrue(labels.contains("CLASS"));
        assertTrue(labels.contains("STRUCTURE"));
        assertTrue(labels.contains("ASSOCIATION"));
        assertTrue(labels.contains("VIEW"));
        assertTrue(labels.contains("GRAPHIC"));
        assertTrue(labels.contains("DOMAIN"));
        assertTrue(labels.contains("UNIT"));
        assertTrue(labels.contains("FUNCTION"));
        assertTrue(labels.contains("CONTEXT"));
        assertTrue(labels.contains("CONSTRAINTS"));
        assertTrue(labels.contains("SIGN BASKET"));
        assertTrue(labels.contains("REFSYSTEM BASKET"));
        assertFalse(labels.contains("TOPIC"));
        assertFalse(labels.contains("MODEL"));
        assertFalse(labels.contains("IMPORTS"));
        assertFalse(labels.contains("LINE FORM"));
        assertTrue(indexOfLabel(items, "CLASS") < indexOfLabel(items, "CLASS Name = ... END Name;"));
        assertTrue(indexOfLabel(items, "STRUCTURE") < indexOfLabel(items, "STRUCTURE Name = ... END Name;"));
        assertFalse(labels.stream().anyMatch(label -> label.startsWith("CLASS (")));

        assertEquals(InsertTextFormat.Snippet, findItemByLabel(items, "CLASS Name = ... END Name;").getInsertTextFormat());
        assertEquals(InsertTextFormat.Snippet, findItemByLabel(items, "STRUCTURE Name = ... END Name;").getInsertTextFormat());
        assertEquals(InsertTextFormat.Snippet, findItemByLabel(items, "SIGN BASKET ...").getInsertTextFormat());
        assertNull(findItemByLabel(items, "FUNCTION Name = ..."));
    }

    @Test
    void completionOnBlankModelLineOffersOnlyAllowedModelStarts(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ModelBodyCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL ModelBodyCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  
                END ModelBodyCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("END ModelBodyCompletion.") - 1;
        List<CompletionItem> items = completionItems(service, uri, valid, offset);
        List<String> labels = items.stream().map(CompletionItem::getLabel).toList();

        assertTrue(labels.contains("TOPIC"));
        assertTrue(labels.contains("CLASS"));
        assertTrue(labels.contains("STRUCTURE"));
        assertTrue(labels.contains("DOMAIN"));
        assertTrue(labels.contains("UNIT"));
        assertTrue(labels.contains("FUNCTION"));
        assertTrue(labels.contains("CONTEXT"));
        assertTrue(labels.contains("LINE FORM"));
        assertFalse(labels.contains("ASSOCIATION"));
        assertFalse(labels.contains("VIEW"));
        assertFalse(labels.contains("GRAPHIC"));
        assertFalse(labels.contains("CONSTRAINTS"));
        assertFalse(labels.contains("SIGN BASKET"));
        assertFalse(labels.contains("REFSYSTEM BASKET"));
        assertFalse(labels.contains("MODEL"));
        assertFalse(labels.contains("IMPORTS"));
        assertTrue(indexOfLabel(items, "TOPIC") < indexOfLabel(items, "TOPIC Name = ... END Name;"));
        assertNotNull(findItemByLabel(items, "TOPIC Name = ... END Name;"));
        assertEquals(InsertTextFormat.Snippet, findItemByLabel(items, "TOPIC Name = ... END Name;").getInsertTextFormat());
    }

    @Test
    void completionOnPrefixedModelLineFiltersAllowedStarts(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ModelBodyPrefixCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL ModelBodyPrefixCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TO
                END ModelBodyPrefixCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("  TO") + "  TO".length();
        List<String> labels = completionLabels(service, uri, valid, offset);
        assertTrue(labels.contains("TOPIC"));
        assertFalse(labels.contains("CLASS"));

        String lineDirty = valid.replace("  TO", "  LI");
        service.didChange(fullDocumentChange(uri, 2, lineDirty));
        int lineOffset = lineDirty.indexOf("  LI") + "  LI".length();
        List<String> lineLabels = completionLabels(service, uri, lineDirty, lineOffset);
        assertTrue(lineLabels.contains("LINE FORM"));
        assertFalse(lineLabels.contains("TOPIC"));
    }

    @Test
    void modelBodySnippetUsesModelBodyIndentOnBlankLine(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ModelBodySnippetIndent.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL ModelBodySnippetIndent (en) AT "http://example.org" VERSION "2024-01-01" =
                  
                END ModelBodySnippetIndent.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("END ModelBodySnippetIndent.") - 1;
        List<CompletionItem> items = completionItems(service, uri, valid, offset);
        CompletionItem topicSnippet = findItemByLabel(items, "TOPIC Name = ... END Name;");
        CompletionItem domainSnippet = findItemByLabel(items, "DOMAIN Name = ...;");
        CompletionItem unitSnippet = findItemByLabel(items, "UNIT Name = ...;");

        assertNotNull(topicSnippet);
        assertNotNull(domainSnippet);
        assertNotNull(unitSnippet);
        assertEquals(0, topicSnippet.getTextEdit().getLeft().getRange().getStart().getCharacter());
        assertEquals(InsertTextMode.AsIs, topicSnippet.getInsertTextMode());
        assertEquals("  TOPIC ${1:Name} ${2:}=\n    $0\n  END ${1/^([A-Za-z_][A-Za-z0-9_]*).*$/$1/};", newText(topicSnippet));
        assertEquals("  DOMAIN ${1:Name} ${2:}= $0;", newText(domainSnippet));
        assertEquals("  UNIT ${1:Name} ${2:}= $0;", newText(unitSnippet));
    }

    @Test
    void completionOnPrefixedTopicLineFiltersAllowedStarts(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicBodyPrefixCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicBodyPrefixCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CL
                  END T;
                END TopicBodyPrefixCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("    CL") + "    CL".length();
        List<String> labels = completionLabels(service, uri, valid, offset);
        assertTrue(labels.contains("CLASS"));
        assertFalse(labels.contains("STRUCTURE"));

        String sigDirty = valid.replace("    CL", "    SIG");
        service.didChange(fullDocumentChange(uri, 2, sigDirty));
        int sigOffset = sigDirty.indexOf("    SIG") + "    SIG".length();
        List<String> sigLabels = completionLabels(service, uri, sigDirty, sigOffset);
        assertTrue(sigLabels.contains("SIGN BASKET"));
        assertFalse(sigLabels.contains("CLASS"));
    }

    @Test
    void topicBodySnippetUsesTopicBodyIndentOnBlankLine(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicBodySnippetIndent.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicBodySnippetIndent (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    
                  END T;
                END TopicBodySnippetIndent.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("  END T;") - 1;
        List<CompletionItem> items = completionItems(service, uri, valid, offset);
        CompletionItem classSnippet = findItemByLabel(items, "CLASS Name = ... END Name;");

        assertNotNull(classSnippet);
        assertEquals(0, classSnippet.getTextEdit().getLeft().getRange().getStart().getCharacter());
        assertEquals(InsertTextMode.AsIs, classSnippet.getInsertTextMode());
        assertEquals("    CLASS ${1:Name} ${2:}=\n      $0\n    END ${1/^([A-Za-z_][A-Za-z0-9_]*).*$/$1/};", newText(classSnippet));
        assertEquals("    STRUCTURE ${1:Name} ${2:}=\n      $0\n    END ${1/^([A-Za-z_][A-Za-z0-9_]*).*$/$1/};",
                newText(findItemByLabel(items, "STRUCTURE Name = ... END Name;")));
        assertEquals("    UNIT ${1:Name} ${2:}= $0;",
                newText(findItemByLabel(items, "UNIT Name = ...;")));
    }

    @Test
    void topicBodySnippetIgnoresOverIndentedBlankLine(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicBodySnippetOverIndent.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicBodySnippetOverIndent (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                          
                  END T;
                END TopicBodySnippetOverIndent.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("  END T;") - 1;
        List<CompletionItem> items = completionItems(service, uri, valid, offset);
        assertEquals("    CLASS ${1:Name} ${2:}=\n      $0\n    END ${1/^([A-Za-z_][A-Za-z0-9_]*).*$/$1/};",
                newText(findItemByLabel(items, "CLASS Name = ... END Name;")));
        assertEquals(InsertTextMode.AsIs, findItemByLabel(items, "CLASS Name = ... END Name;").getInsertTextMode());
        assertEquals("    DOMAIN ${1:Name} ${2:}= $0;",
                newText(findItemByLabel(items, "DOMAIN Name = ...;")));
        assertEquals("    UNIT ${1:Name} ${2:}= $0;",
                newText(findItemByLabel(items, "UNIT Name = ...;")));
        assertEquals("    ASSOCIATION ${1:Name} ${2:}=\n      $0\n    END ${1/^([A-Za-z_][A-Za-z0-9_]*).*$/$1/};",
                newText(findItemByLabel(items, "ASSOCIATION Name = ... END Name;")));
        assertEquals("    CONSTRAINTS OF ${1:Class} =\n      $0\n    END;",
                newText(findItemByLabel(items, "CONSTRAINTS OF ... = ... END;")));
        assertEquals(InsertTextMode.AsIs, findItemByLabel(items, "CONSTRAINTS OF ... = ... END;").getInsertTextMode());
        assertNull(findItemByLabel(items, "DOMAIN Name = ...;").getInsertTextMode());
    }

    @Test
    void completionInsideClassBodyDoesNotUseTopicBodySlot(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicBodyIsolation.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicBodyIsolation (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      
                    END C;
                  END T;
                END TopicBodyIsolation.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        int offset = valid.indexOf("    END C;") - 1;
        List<CompletionItem> items = completionItems(service, uri, valid, offset);
        assertTrue(items.isEmpty(), "Expected no topic-body completion inside class body");
    }

    @Test
    void completionAfterBagOffersSnippetsAndContinuations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("BagCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL BagCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE LocalStruct =
                  END LocalStruct;

                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END BagCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyBag = valid.replace("attr : TEXT;", "attr : BAG");
        service.didChange(fullDocumentChange(uri, 2, dirtyBag));
        List<String> bagLabels = completionLabels(service, uri, dirtyBag,
                dirtyBag.indexOf("attr : BAG") + "attr : BAG".length());
        assertTrue(bagLabels.contains("{"));
        assertTrue(bagLabels.contains("OF"));

        String dirtyTarget = valid.replace("attr : TEXT;", "attr : BAG OF ");
        service.didChange(fullDocumentChange(uri, 3, dirtyTarget));
        List<String> targetLabels = completionLabels(service, uri, dirtyTarget,
                dirtyTarget.indexOf("attr : BAG OF ") + "attr : BAG OF ".length());
        assertTrue(targetLabels.contains("LocalStruct"));
        assertTrue(targetLabels.contains("ANYSTRUCTURE"));
    }

    @Test
    void completionAfterHeaderNameOffersStagedContinuations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("HeaderAfterNameCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL HeaderAfterNameCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC BaseTopic =
                  END BaseTopic;

                  TOPIC T =
                    STRUCTURE BaseStruct =
                    END BaseStruct;

                    CLASS BaseClass =
                    END BaseClass;

                    CLASS C =
                    END C;

                    STRUCTURE S =
                    END S;
                  END T;
                END HeaderAfterNameCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyClass = valid.replace("CLASS C =", "CLASS Foo ");
        service.didChange(fullDocumentChange(uri, 2, dirtyClass));
        List<CompletionItem> classItems = completionItems(service, uri, dirtyClass,
                dirtyClass.indexOf("CLASS Foo ") + "CLASS Foo ".length());
        List<String> classLabels = classItems.stream().map(CompletionItem::getLabel).toList();
        assertTrue(classLabels.contains("(ABSTRACT)"));
        assertTrue(classLabels.contains("(EXTENDED)"));
        assertTrue(classLabels.contains("(FINAL)"));
        assertTrue(classLabels.contains("EXTENDS"));
        assertTrue(classLabels.contains("="));
        assertEquals("EXTENDS ", newText(findItemByLabel(classItems, "EXTENDS")));
        assertEquals("=", newText(findItemByLabel(classItems, "=")));

        String dirtyStructure = valid.replace("STRUCTURE S =", "STRUCTURE Bar ");
        service.didChange(fullDocumentChange(uri, 3, dirtyStructure));
        List<String> structureLabels = completionLabels(service, uri, dirtyStructure,
                dirtyStructure.indexOf("STRUCTURE Bar ") + "STRUCTURE Bar ".length());
        assertTrue(structureLabels.contains("(ABSTRACT)"));
        assertTrue(structureLabels.contains("(EXTENDED)"));
        assertTrue(structureLabels.contains("(FINAL)"));
        assertTrue(structureLabels.contains("EXTENDS"));
        assertTrue(structureLabels.contains("="));

        String dirtyTopic = valid.replace("TOPIC T =", "TOPIC Child ");
        service.didChange(fullDocumentChange(uri, 4, dirtyTopic));
        List<String> topicLabels = completionLabels(service, uri, dirtyTopic,
                dirtyTopic.indexOf("TOPIC Child ") + "TOPIC Child ".length());
        assertTrue(topicLabels.contains("(ABSTRACT)"));
        assertFalse(topicLabels.contains("(EXTENDED)"));
        assertTrue(topicLabels.contains("(FINAL)"));
        assertTrue(topicLabels.contains("EXTENDS"));
        assertTrue(topicLabels.contains("="));
    }

    @Test
    void completionBeforeFixedEqualsInBlockHeaderOffersContinuationsWithoutExplicitEquals(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("BlockHeaderBeforeFixedEquals.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL BlockHeaderBeforeFixedEquals (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC BaseTopic =
                  END BaseTopic;

                  TOPIC T =
                    STRUCTURE BaseStruct =
                    END BaseStruct;

                    CLASS BaseClass =
                    END BaseClass;

                    CLASS C =
                    END C;
                  END T;
                END BlockHeaderBeforeFixedEquals.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyAfterName = valid.replace("CLASS C =", "CLASS Foo =");
        service.didChange(fullDocumentChange(uri, 2, dirtyAfterName));
        int afterNameOffset = dirtyAfterName.indexOf("CLASS Foo =") + "CLASS Foo ".length();
        List<String> afterNameLabels = completionLabels(service, uri, dirtyAfterName, afterNameOffset);
        assertTrue(afterNameLabels.contains("(ABSTRACT)"));
        assertTrue(afterNameLabels.contains("(EXTENDED)"));
        assertTrue(afterNameLabels.contains("(FINAL)"));
        assertTrue(afterNameLabels.contains("EXTENDS"));
        assertFalse(afterNameLabels.contains("="));

        String dirtyAfterModifier = valid.replace("CLASS C =", "CLASS Foo (FINAL) =");
        service.didChange(fullDocumentChange(uri, 3, dirtyAfterModifier));
        int afterModifierOffset = dirtyAfterModifier.indexOf("CLASS Foo (FINAL) =") + "CLASS Foo (FINAL) ".length();
        List<CompletionItem> afterModifierItems = completionItems(service, uri, dirtyAfterModifier, afterModifierOffset);
        List<String> afterModifierLabels = afterModifierItems.stream().map(CompletionItem::getLabel).toList();
        assertTrue(afterModifierLabels.contains("EXTENDS"));
        assertFalse(afterModifierLabels.contains("="));
        assertEquals("EXTENDS ", newText(findItemByLabel(afterModifierItems, "EXTENDS")));

        String dirtyExtends = valid.replace("CLASS C =", "CLASS Foo EXTENDS =");
        service.didChange(fullDocumentChange(uri, 4, dirtyExtends));
        int extendsOffset = dirtyExtends.indexOf("CLASS Foo EXTENDS =") + "CLASS Foo EXTENDS ".length();
        List<CompletionItem> extendsItems = completionItems(service, uri, dirtyExtends, extendsOffset);
        assertTrue(extendsItems.stream().map(CompletionItem::getLabel).toList().contains("BaseClass"));
        assertEquals("BaseClass ", newText(findItemByLabel(extendsItems, "BaseClass")));

        String dirtyAfterExtendsTarget = valid.replace("CLASS C =", "CLASS Foo EXTENDS BaseClass =");
        service.didChange(fullDocumentChange(uri, 5, dirtyAfterExtendsTarget));
        int afterExtendsTargetOffset = dirtyAfterExtendsTarget.indexOf("CLASS Foo EXTENDS BaseClass =")
                + "CLASS Foo EXTENDS BaseClass ".length();
        List<String> afterExtendsTargetLabels = completionLabels(service, uri, dirtyAfterExtendsTarget, afterExtendsTargetOffset);
        assertFalse(afterExtendsTargetLabels.contains("="));
    }

    @Test
    void completionBeforeFixedEqualsInTopicBlockHeaderOffersTopicContinuations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicBlockHeaderBeforeFixedEquals.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicBlockHeaderBeforeFixedEquals (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC BaseTopic =
                  END BaseTopic;

                  TOPIC T =
                  END T;
                END TopicBlockHeaderBeforeFixedEquals.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyAfterName = valid.replace("TOPIC T =", "TOPIC Child =");
        service.didChange(fullDocumentChange(uri, 2, dirtyAfterName));
        int afterNameOffset = dirtyAfterName.indexOf("TOPIC Child =") + "TOPIC Child ".length();
        List<String> afterNameLabels = completionLabels(service, uri, dirtyAfterName, afterNameOffset);
        assertTrue(afterNameLabels.contains("(ABSTRACT)"));
        assertFalse(afterNameLabels.contains("(EXTENDED)"));
        assertTrue(afterNameLabels.contains("(FINAL)"));
        assertTrue(afterNameLabels.contains("EXTENDS"));
        assertFalse(afterNameLabels.contains("="));

        String dirtyAfterModifier = valid.replace("TOPIC T =", "TOPIC Child (FINAL) =");
        service.didChange(fullDocumentChange(uri, 3, dirtyAfterModifier));
        int afterModifierOffset = dirtyAfterModifier.indexOf("TOPIC Child (FINAL) =") + "TOPIC Child (FINAL) ".length();
        List<CompletionItem> afterModifierItems = completionItems(service, uri, dirtyAfterModifier, afterModifierOffset);
        List<String> afterModifierLabels = afterModifierItems.stream().map(CompletionItem::getLabel).toList();
        assertTrue(afterModifierLabels.contains("EXTENDS"));
        assertFalse(afterModifierLabels.contains("="));

        String dirtyExtends = valid.replace("TOPIC T =", "TOPIC Child EXTENDS =");
        service.didChange(fullDocumentChange(uri, 4, dirtyExtends));
        int extendsOffset = dirtyExtends.indexOf("TOPIC Child EXTENDS =") + "TOPIC Child EXTENDS ".length();
        List<CompletionItem> extendsItems = completionItems(service, uri, dirtyExtends, extendsOffset);
        assertTrue(extendsItems.stream().map(CompletionItem::getLabel).toList().contains("BaseTopic"));
        assertEquals("BaseTopic ", newText(findItemByLabel(extendsItems, "BaseTopic")));
    }

    @Test
    void completionForDomainHeaderAndSnippetOffersDomainContinuations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("DomainHeaderCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL DomainHeaderCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN BaseDomain = TEXT;
                  DOMAIN SeedDomain = TEXT;
                END DomainHeaderCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyAfterName = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN Child ");
        service.didChange(fullDocumentChange(uri, 2, dirtyAfterName));
        int afterNameOffset = dirtyAfterName.indexOf("DOMAIN Child ") + "DOMAIN Child ".length();
        List<String> afterNameLabels = completionLabels(service, uri, dirtyAfterName, afterNameOffset);
        assertTrue(afterNameLabels.contains("(ABSTRACT)"));
        assertTrue(afterNameLabels.contains("(FINAL)"));
        assertTrue(afterNameLabels.contains("(GENERIC)"));
        assertTrue(afterNameLabels.contains("EXTENDS"));
        assertTrue(afterNameLabels.contains("="));

        String dirtySnippetAfterName = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN Child = ;");
        service.didChange(fullDocumentChange(uri, 3, dirtySnippetAfterName));
        int snippetAfterNameOffset = dirtySnippetAfterName.indexOf("DOMAIN Child = ;") + "DOMAIN Child ".length();
        List<String> snippetAfterNameLabels = completionLabels(service, uri, dirtySnippetAfterName, snippetAfterNameOffset);
        assertTrue(snippetAfterNameLabels.contains("(ABSTRACT)"));
        assertTrue(snippetAfterNameLabels.contains("(FINAL)"));
        assertTrue(snippetAfterNameLabels.contains("(GENERIC)"));
        assertTrue(snippetAfterNameLabels.contains("EXTENDS"));
        assertFalse(snippetAfterNameLabels.contains("="));

        String dirtyAfterModifier = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN Child (FINAL) = ;");
        service.didChange(fullDocumentChange(uri, 4, dirtyAfterModifier));
        int afterModifierOffset = dirtyAfterModifier.indexOf("DOMAIN Child (FINAL) = ;") + "DOMAIN Child (FINAL) ".length();
        List<String> afterModifierLabels = completionLabels(service, uri, dirtyAfterModifier, afterModifierOffset);
        assertTrue(afterModifierLabels.contains("EXTENDS"));
        assertFalse(afterModifierLabels.contains("="));

        String dirtyExtends = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN Child EXTENDS = ;");
        service.didChange(fullDocumentChange(uri, 5, dirtyExtends));
        int extendsOffset = dirtyExtends.indexOf("DOMAIN Child EXTENDS = ;") + "DOMAIN Child EXTENDS ".length();
        List<CompletionItem> extendsItems = completionItems(service, uri, dirtyExtends, extendsOffset);
        assertTrue(extendsItems.stream().map(CompletionItem::getLabel).toList().contains("BaseDomain"));
        assertEquals("BaseDomain ", newText(findItemByLabel(extendsItems, "BaseDomain")));
    }

    @Test
    void completionAfterDomainEqualsOffersOnlyDomainLegalRootAndTails(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("DomainRhsCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL DomainRhsCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN BaseDomain = TEXT;
                  DOMAIN SeedDomain = TEXT;
                END DomainRhsCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyRoot = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN LocalDomain = ");
        service.didChange(fullDocumentChange(uri, 2, dirtyRoot));
        List<CompletionItem> rootItems = completionItems(service, uri, dirtyRoot,
                dirtyRoot.indexOf("DOMAIN LocalDomain = ") + "DOMAIN LocalDomain = ".length());
        List<String> rootLabels = rootItems.stream().map(CompletionItem::getLabel).toList();
        assertTrue(rootLabels.contains("TEXT"));
        assertTrue(rootLabels.contains("MTEXT"));
        assertTrue(rootLabels.contains("BOOLEAN"));
        assertTrue(rootLabels.contains("NUMERIC"));
        assertTrue(rootLabels.contains("FORMAT"));
        assertTrue(rootLabels.contains("CLASS"));
        assertTrue(rootLabels.contains("ATTRIBUTE"));
        assertTrue(rootLabels.contains("ALL OF"));
        assertFalse(rootLabels.contains("REFERENCE"));
        assertFalse(rootLabels.contains("BAG"));
        assertFalse(rootLabels.contains("LIST"));
        assertEquals("TEXT*${1:255}", newText(findItemByLabel(rootItems, "TEXT*<length>")));
        assertEquals("MTEXT*${1:255}", newText(findItemByLabel(rootItems, "MTEXT*<length>")));
        assertEquals("${1:1} .. ${2:10}", newText(findItemByLabel(rootItems, "1 .. 10")));
        assertEquals("(${1:A}, ${2:B}, ${3:C})", newText(findItemByLabel(rootItems, "(A, B, C)")));
        assertEquals("CLASS RESTRICTION (${1:Viewable})", newText(findItemByLabel(rootItems, "CLASS RESTRICTION (...)")));
        assertFalse(rootLabels.contains("\"min\" .. \"max\""));

        String dirtyTextTail = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN LocalDomain = TEXT;");
        service.didChange(fullDocumentChange(uri, 3, dirtyTextTail));
        List<String> textTailLabels = completionLabels(service, uri, dirtyTextTail,
                dirtyTextTail.indexOf("DOMAIN LocalDomain = TEXT;") + "DOMAIN LocalDomain = TEXT".length());
        assertTrue(textTailLabels.contains("*"));
        assertTrue(textTailLabels.contains("* <length>"));

        String dirtyNumericTail = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN LocalDomain = 1;");
        service.didChange(fullDocumentChange(uri, 4, dirtyNumericTail));
        List<String> numericTailLabels = completionLabels(service, uri, dirtyNumericTail,
                dirtyNumericTail.indexOf("DOMAIN LocalDomain = 1;") + "DOMAIN LocalDomain = 1".length());
        assertTrue(numericTailLabels.contains(".."));
        assertTrue(numericTailLabels.contains(".. <upper>"));

        String dirtyMeta = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN LocalDomain = CLASS ");
        service.didChange(fullDocumentChange(uri, 5, dirtyMeta));
        List<String> metaLabels = completionLabels(service, uri, dirtyMeta,
                dirtyMeta.indexOf("DOMAIN LocalDomain = CLASS ") + "DOMAIN LocalDomain = CLASS ".length());
        assertTrue(metaLabels.contains("RESTRICTION"));

        String dirtyAllOf = valid.replace("DOMAIN SeedDomain = TEXT;", "DOMAIN LocalDomain = ALL OF ");
        service.didChange(fullDocumentChange(uri, 6, dirtyAllOf));
        List<CompletionItem> allOfItems = completionItems(service, uri, dirtyAllOf,
                dirtyAllOf.indexOf("DOMAIN LocalDomain = ALL OF ") + "DOMAIN LocalDomain = ALL OF ".length());
        assertTrue(allOfItems.stream().map(CompletionItem::getLabel).toList().contains("BaseDomain"));
    }

    @Test
    void completionForUnitHeaderAndSnippetOffersUnitContinuations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("UnitHeaderCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL UnitHeaderCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  UNIT BaseUnit = ;
                  UNIT SeedUnit = ;
                END UnitHeaderCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyAfterName = valid.replace("UNIT SeedUnit = ;", "UNIT Child ");
        service.didChange(fullDocumentChange(uri, 2, dirtyAfterName));
        int afterNameOffset = dirtyAfterName.indexOf("UNIT Child ") + "UNIT Child ".length();
        List<String> afterNameLabels = completionLabels(service, uri, dirtyAfterName, afterNameOffset);
        assertTrue(afterNameLabels.contains("[Name]"));
        assertTrue(afterNameLabels.contains("(ABSTRACT)"));
        assertTrue(afterNameLabels.contains("EXTENDS"));
        assertTrue(afterNameLabels.contains("="));

        String dirtySnippetAfterName = valid.replace("UNIT SeedUnit = ;", "UNIT Child = ;");
        service.didChange(fullDocumentChange(uri, 3, dirtySnippetAfterName));
        int snippetAfterNameOffset = dirtySnippetAfterName.indexOf("UNIT Child = ;") + "UNIT Child ".length();
        List<String> snippetAfterNameLabels = completionLabels(service, uri, dirtySnippetAfterName, snippetAfterNameOffset);
        assertTrue(snippetAfterNameLabels.contains("[Name]"));
        assertTrue(snippetAfterNameLabels.contains("(ABSTRACT)"));
        assertTrue(snippetAfterNameLabels.contains("EXTENDS"));
        assertFalse(snippetAfterNameLabels.contains("="));

        String dirtyAfterAbbreviation = valid.replace("UNIT SeedUnit = ;", "UNIT Child [abbr] ");
        service.didChange(fullDocumentChange(uri, 4, dirtyAfterAbbreviation));
        int afterAbbreviationOffset = dirtyAfterAbbreviation.indexOf("UNIT Child [abbr] ") + "UNIT Child [abbr] ".length();
        List<String> afterAbbreviationLabels = completionLabels(service, uri, dirtyAfterAbbreviation, afterAbbreviationOffset);
        assertFalse(afterAbbreviationLabels.contains("[Name]"));
        assertTrue(afterAbbreviationLabels.contains("(ABSTRACT)"));
        assertTrue(afterAbbreviationLabels.contains("EXTENDS"));
        assertTrue(afterAbbreviationLabels.contains("="));

        String dirtyAfterModifier = valid.replace("UNIT SeedUnit = ;", "UNIT Child (ABSTRACT) = ;");
        service.didChange(fullDocumentChange(uri, 5, dirtyAfterModifier));
        int afterModifierOffset = dirtyAfterModifier.indexOf("UNIT Child (ABSTRACT) = ;") + "UNIT Child (ABSTRACT) ".length();
        List<String> afterModifierLabels = completionLabels(service, uri, dirtyAfterModifier, afterModifierOffset);
        assertTrue(afterModifierLabels.contains("EXTENDS"));
        assertFalse(afterModifierLabels.contains("="));

        String dirtyExtends = valid.replace("UNIT SeedUnit = ;", "UNIT Child EXTENDS = ;");
        service.didChange(fullDocumentChange(uri, 6, dirtyExtends));
        int extendsOffset = dirtyExtends.indexOf("UNIT Child EXTENDS = ;") + "UNIT Child EXTENDS ".length();
        List<CompletionItem> extendsItems = completionItems(service, uri, dirtyExtends, extendsOffset);
        assertTrue(extendsItems.stream().map(CompletionItem::getLabel).toList().contains("BaseUnit"));
        assertEquals("BaseUnit ", newText(findItemByLabel(extendsItems, "BaseUnit")));
    }

    @Test
    void completionAfterUnitEqualsOffersCuratedUnitRootAndFollowUps(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("UnitRhsCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL UnitRhsCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  UNIT BaseUnit = ;
                  UNIT SeedUnit = ;
                END UnitRhsCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyRoot = valid.replace("UNIT SeedUnit = ;", "UNIT LocalUnit = ");
        service.didChange(fullDocumentChange(uri, 2, dirtyRoot));
        List<CompletionItem> rootItems = completionItems(service, uri, dirtyRoot,
                dirtyRoot.indexOf("UNIT LocalUnit = ") + "UNIT LocalUnit = ".length());
        List<String> rootLabels = rootItems.stream().map(CompletionItem::getLabel).toList();
        assertTrue(rootLabels.contains("[BaseUnit]"));
        assertTrue(rootLabels.contains("1000 [BaseUnit]"));
        assertTrue(rootLabels.contains("(UnitA / UnitB)"));
        assertTrue(rootLabels.contains("(UnitA * UnitB)"));
        assertFalse(rootLabels.contains("TEXT"));
        assertFalse(rootLabels.contains("BOOLEAN"));
        assertFalse(rootLabels.contains("CLASS"));
        assertFalse(rootLabels.contains("REFERENCE"));
        assertEquals("[${1:BaseUnit}]", newText(findItemByLabel(rootItems, "[BaseUnit]")));
        assertEquals("${1:1000} [${2:BaseUnit}]", newText(findItemByLabel(rootItems, "1000 [BaseUnit]")));
        assertEquals("(${1:UnitA} / ${2})", newText(findItemByLabel(rootItems, "(UnitA / UnitB)")));
        assertEquals("(${1:UnitA} * ${2})", newText(findItemByLabel(rootItems, "(UnitA * UnitB)")));

        String dirtyBracket = valid.replace("UNIT SeedUnit = ;", "UNIT LocalUnit = [;");
        service.didChange(fullDocumentChange(uri, 3, dirtyBracket));
        List<CompletionItem> bracketItems = completionItems(service, uri, dirtyBracket,
                dirtyBracket.indexOf("UNIT LocalUnit = [;") + "UNIT LocalUnit = [".length());
        assertTrue(bracketItems.stream().map(CompletionItem::getLabel).toList().contains("BaseUnit"));
        assertEquals("BaseUnit", newText(findItemByLabel(bracketItems, "BaseUnit")));

        String dirtyComposedTarget = valid.replace("UNIT SeedUnit = ;", "UNIT LocalUnit = (;");
        service.didChange(fullDocumentChange(uri, 4, dirtyComposedTarget));
        List<CompletionItem> composedTargetItems = completionItems(service, uri, dirtyComposedTarget,
                dirtyComposedTarget.indexOf("UNIT LocalUnit = (;") + "UNIT LocalUnit = (".length());
        assertTrue(composedTargetItems.stream().map(CompletionItem::getLabel).toList().contains("BaseUnit"));
        assertEquals("BaseUnit ", newText(findItemByLabel(composedTargetItems, "BaseUnit")));

        String dirtyComposedTargetBeforeClosingParen = valid.replace("UNIT SeedUnit = ;", "UNIT LocalUnit = (BaseUnit * );");
        service.didChange(fullDocumentChange(uri, 5, dirtyComposedTargetBeforeClosingParen));
        List<CompletionItem> composedTargetBeforeClosingParenItems = completionItems(service, uri, dirtyComposedTargetBeforeClosingParen,
                dirtyComposedTargetBeforeClosingParen.indexOf("UNIT LocalUnit = (BaseUnit * );")
                        + "UNIT LocalUnit = (BaseUnit * ".length());
        assertTrue(composedTargetBeforeClosingParenItems.stream().map(CompletionItem::getLabel).toList().contains("BaseUnit"));
        assertEquals("BaseUnit ", newText(findItemByLabel(composedTargetBeforeClosingParenItems, "BaseUnit")));

        String dirtyComposedOperator = valid.replace("UNIT SeedUnit = ;", "UNIT LocalUnit = (BaseUnit ;");
        service.didChange(fullDocumentChange(uri, 6, dirtyComposedOperator));
        List<String> composedOperatorLabels = completionLabels(service, uri, dirtyComposedOperator,
                dirtyComposedOperator.indexOf("UNIT LocalUnit = (BaseUnit ;") + "UNIT LocalUnit = (BaseUnit ".length());
        assertTrue(composedOperatorLabels.contains("*"));
        assertTrue(composedOperatorLabels.contains("/"));
        assertTrue(composedOperatorLabels.contains("**"));
        assertTrue(composedOperatorLabels.contains(")"));

        String dirtyComposedOperatorBeforeClosingParen = valid.replace("UNIT SeedUnit = ;", "UNIT LocalUnit = (BaseUnit * SeedUnit);");
        service.didChange(fullDocumentChange(uri, 7, dirtyComposedOperatorBeforeClosingParen));
        List<String> composedOperatorBeforeClosingParenLabels = completionLabels(service, uri, dirtyComposedOperatorBeforeClosingParen,
                dirtyComposedOperatorBeforeClosingParen.indexOf("UNIT LocalUnit = (BaseUnit * SeedUnit);")
                        + "UNIT LocalUnit = (BaseUnit * SeedUnit".length());
        assertTrue(composedOperatorBeforeClosingParenLabels.contains("*"));
        assertTrue(composedOperatorBeforeClosingParenLabels.contains("/"));
        assertTrue(composedOperatorBeforeClosingParenLabels.contains("**"));
        assertTrue(composedOperatorBeforeClosingParenLabels.contains(")"));
    }

    @Test
    void completionDoesNotOfferHeaderFollowUpAtBareNameEnd(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("HeaderBareNameBoundary.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL HeaderBareNameBoundary (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                    END C;
                  END T;
                END HeaderBareNameBoundary.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyClass = valid.replace("CLASS C =", "CLASS ff =");
        service.didChange(fullDocumentChange(uri, 2, dirtyClass));
        List<String> classLabels = completionLabels(service, uri, dirtyClass,
                dirtyClass.indexOf("CLASS ff") + "CLASS ff".length());
        assertFalse(classLabels.contains("(ABSTRACT)"));
        assertFalse(classLabels.contains("(EXTENDED)"));
        assertFalse(classLabels.contains("(FINAL)"));
        assertFalse(classLabels.contains("EXTENDS"));
        assertFalse(classLabels.contains("="));

        String dirtyTopic = valid.replace("TOPIC T =", "TOPIC ff =");
        service.didChange(fullDocumentChange(uri, 3, dirtyTopic));
        List<String> topicLabels = completionLabels(service, uri, dirtyTopic,
                dirtyTopic.indexOf("TOPIC ff") + "TOPIC ff".length());
        assertFalse(topicLabels.contains("(ABSTRACT)"));
        assertFalse(topicLabels.contains("(FINAL)"));
        assertFalse(topicLabels.contains("EXTENDS"));
        assertFalse(topicLabels.contains("="));
    }

    @Test
    void completionInsideHeaderModifierContextOffersOnlyAllowedTransitions(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("HeaderModifierCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL HeaderModifierCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                    END C;
                  END T;
                END HeaderModifierCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyOpen = valid.replace("CLASS C =", "CLASS Foo (");
        service.didChange(fullDocumentChange(uri, 2, dirtyOpen));
        List<String> openLabels = completionLabels(service, uri, dirtyOpen,
                dirtyOpen.indexOf("CLASS Foo (") + "CLASS Foo (".length());
        assertTrue(openLabels.contains("ABSTRACT"));
        assertTrue(openLabels.contains("EXTENDED"));
        assertTrue(openLabels.contains("FINAL"));
        assertFalse(openLabels.contains("EXTENDS"));
        assertFalse(openLabels.contains("="));

        String dirtyClose = valid.replace("CLASS C =", "CLASS Foo (FINAL");
        service.didChange(fullDocumentChange(uri, 3, dirtyClose));
        List<String> closeLabels = completionLabels(service, uri, dirtyClose,
                dirtyClose.indexOf("CLASS Foo (FINAL") + "CLASS Foo (FINAL".length());
        assertEquals(List.of(")"), closeLabels);

        String dirtyAfterModifier = valid.replace("CLASS C =", "CLASS Foo (FINAL) ");
        service.didChange(fullDocumentChange(uri, 4, dirtyAfterModifier));
        List<CompletionItem> afterModifierItems = completionItems(service, uri, dirtyAfterModifier,
                dirtyAfterModifier.indexOf("CLASS Foo (FINAL) ") + "CLASS Foo (FINAL) ".length());
        List<String> afterModifierLabels = afterModifierItems.stream().map(CompletionItem::getLabel).toList();
        assertTrue(afterModifierLabels.contains("EXTENDS"));
        assertTrue(afterModifierLabels.contains("="));
        assertFalse(afterModifierLabels.contains("(FINAL)"));
        assertEquals("EXTENDS ", newText(findItemByLabel(afterModifierItems, "EXTENDS")));
        assertEquals("=", newText(findItemByLabel(afterModifierItems, "=")));
    }

    @Test
    void completionInsideTopicHeaderModifierContextRestrictsToTopicModifiers(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicHeaderModifierCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicHeaderModifierCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                  END T;
                END TopicHeaderModifierCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyOpen = valid.replace("TOPIC T =", "TOPIC Foo (");
        service.didChange(fullDocumentChange(uri, 2, dirtyOpen));
        List<String> openLabels = completionLabels(service, uri, dirtyOpen,
                dirtyOpen.indexOf("TOPIC Foo (") + "TOPIC Foo (".length());
        assertTrue(openLabels.contains("ABSTRACT"));
        assertTrue(openLabels.contains("FINAL"));
        assertFalse(openLabels.contains("EXTENDED"));
    }

    @Test
    void completionAfterTextOrMtextOffersLengthTail(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TextTailCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TextTailCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END TextTailCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyText = valid.replace("attr : TEXT;", "attr : TEXT");
        service.didChange(fullDocumentChange(uri, 2, dirtyText));
        List<CompletionItem> textItems = completionItems(service, uri, dirtyText,
                dirtyText.indexOf("attr : TEXT") + "attr : TEXT".length());
        assertNotNull(findItemByLabel(textItems, "*"));
        assertEquals("*${1:255}", newText(findItemByLabel(textItems, "* <length>")));

        String dirtyMtext = valid.replace("attr : TEXT;", "attr : MANDATORY MTEXT");
        service.didChange(fullDocumentChange(uri, 3, dirtyMtext));
        List<CompletionItem> mtextItems = completionItems(service, uri, dirtyMtext,
                dirtyMtext.indexOf("attr : MANDATORY MTEXT") + "attr : MANDATORY MTEXT".length());
        assertNotNull(findItemByLabel(mtextItems, "*"));
        assertEquals("*${1:255}", newText(findItemByLabel(mtextItems, "* <length>")));

        String dirtyNoSpace = valid.replace("attr : TEXT;", "attr : TEXT*");
        service.didChange(fullDocumentChange(uri, 4, dirtyNoSpace));
        List<CompletionItem> noSpaceItems = completionItems(service, uri, dirtyNoSpace,
                dirtyNoSpace.indexOf("attr : TEXT*") + "attr : TEXT*".length());
        assertEquals("${1:255}", newText(findItemByLabel(noSpaceItems, "<length>")));

        String dirtyWithSpace = valid.replace("attr : TEXT;", "attr : TEXT *");
        service.didChange(fullDocumentChange(uri, 5, dirtyWithSpace));
        List<CompletionItem> withSpaceItems = completionItems(service, uri, dirtyWithSpace,
                dirtyWithSpace.indexOf("attr : TEXT *") + "attr : TEXT *".length());
        assertEquals("${1:255}", newText(findItemByLabel(withSpaceItems, "<length>")));
    }

    @Test
    void completionAfterInlineNumericLiteralOffersRangeTail(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("NumericTailCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL NumericTailCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END NumericTailCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyInt = valid.replace("attr : TEXT;", "attr : 10");
        service.didChange(fullDocumentChange(uri, 2, dirtyInt));
        List<CompletionItem> intItems = completionItems(service, uri, dirtyInt,
                dirtyInt.indexOf("attr : 10") + "attr : 10".length());
        assertNotNull(findItemByLabel(intItems, ".."));
        assertEquals(".. ${1}", newText(findItemByLabel(intItems, ".. <upper>")));

        String dirtyNoSpace = valid.replace("attr : TEXT;", "attr:10");
        service.didChange(fullDocumentChange(uri, 3, dirtyNoSpace));
        List<CompletionItem> noSpaceItems = completionItems(service, uri, dirtyNoSpace,
                dirtyNoSpace.indexOf("attr:10") + "attr:10".length());
        assertNotNull(findItemByLabel(noSpaceItems, ".."));
        assertEquals(".. ${1}", newText(findItemByLabel(noSpaceItems, ".. <upper>")));

        String dirtyDec = valid.replace("attr : TEXT;", "attr : MANDATORY 10.5");
        service.didChange(fullDocumentChange(uri, 4, dirtyDec));
        List<CompletionItem> decItems = completionItems(service, uri, dirtyDec,
                dirtyDec.indexOf("attr : MANDATORY 10.5") + "attr : MANDATORY 10.5".length());
        assertNotNull(findItemByLabel(decItems, ".."));
        assertEquals(".. ${1}", newText(findItemByLabel(decItems, ".. <upper>")));

        String dirtyUpper = valid.replace("attr : TEXT;", "attr : 10..");
        service.didChange(fullDocumentChange(uri, 5, dirtyUpper));
        List<CompletionItem> upperItems = completionItems(service, uri, dirtyUpper,
                dirtyUpper.indexOf("attr : 10..") + "attr : 10..".length());
        assertEquals("${1}", newText(findItemByLabel(upperItems, "<upper>")));

        String dirtyUpperDec = valid.replace("attr : TEXT;", "attr : 10.5 ..");
        service.didChange(fullDocumentChange(uri, 6, dirtyUpperDec));
        List<CompletionItem> upperDecItems = completionItems(service, uri, dirtyUpperDec,
                dirtyUpperDec.indexOf("attr : 10.5 ..") + "attr : 10.5 ..".length());
        assertEquals("${1}", newText(findItemByLabel(upperDecItems, "<upper>")));
    }

    @Test
    void completionUsesVersionAwareDateTimeSuggestions(@TempDir Path tempDir) throws Exception {
        Path file23 = tempDir.resolve("DateTime23.ili");
        String valid23 = """
                INTERLIS 2.3;
                MODEL DateTime23 (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END DateTime23.
                """;
        Files.writeString(file23, valid23);

        InterlisLanguageServer server23 = new InterlisLanguageServer();
        server23.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service23 = server23.getInterlisTextDocumentService();
        String uri23 = file23.toUri().toString();
        service23.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri23, "interlis", 1, valid23)));
        String dirty23 = valid23.replace("attr : TEXT;", "attr : ");
        service23.didChange(fullDocumentChange(uri23, 2, dirty23));
        List<String> labels23 = completionLabels(service23, uri23, dirty23,
                dirty23.indexOf("attr : ") + "attr : ".length());
        assertFalse(labels23.contains("DATE"));
        assertFalse(labels23.contains("TIMEOFDAY"));
        assertFalse(labels23.contains("DATETIME"));
        assertTrue(labels23.contains("INTERLIS.XMLDate"));
        assertTrue(labels23.contains("INTERLIS.XMLDateTime"));
        assertTrue(labels23.contains("INTERLIS.XMLTime"));

        Path file24 = tempDir.resolve("DateTime24.ili");
        String valid24 = valid23.replace("INTERLIS 2.3;", "INTERLIS 2.4;")
                .replace("DateTime23", "DateTime24");
        Files.writeString(file24, valid24);

        InterlisLanguageServer server24 = new InterlisLanguageServer();
        server24.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service24 = server24.getInterlisTextDocumentService();
        String uri24 = file24.toUri().toString();
        service24.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri24, "interlis", 1, valid24)));
        String dirty24 = valid24.replace("attr : TEXT;", "attr : ");
        service24.didChange(fullDocumentChange(uri24, 2, dirty24));
        List<String> labels24 = completionLabels(service24, uri24, dirty24,
                dirty24.indexOf("attr : ") + "attr : ".length());
        assertTrue(labels24.contains("DATE"));
        assertTrue(labels24.contains("TIMEOFDAY"));
        assertTrue(labels24.contains("DATETIME"));
        assertTrue(labels24.contains("INTERLIS.XMLDate"));
    }

    @Test
    void completionAfterInterlisDotOnlyOffersPortableBuiltIns(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("InterlisDotCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL InterlisDotCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Status_Geschaeft = (offen, geschlossen);

                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END InterlisDotCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("attr : TEXT;", "attr : INTERLIS.");
        service.didChange(fullDocumentChange(uri, 2, dirty));
        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : INTERLIS.") + "attr : INTERLIS.".length());
        assertTrue(labels.contains("XMLDate"));
        assertTrue(labels.contains("XMLDateTime"));
        assertTrue(labels.contains("XMLTime"));
        assertFalse(labels.contains("Status_Geschaeft"));
        assertFalse(labels.contains("LIST OF ..."));
        assertFalse(labels.contains("BAG OF ..."));
        assertFalse(labels.contains("REFERENCE"));
    }

    @Test
    void completionAfterFormatOffersOnlyFormattedDomainsAndBoundsSnippets(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("FormatCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL FormatCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN TextDomain = TEXT;
                  DOMAIN Status_Geschaeft = (offen, geschlossen);
                  DOMAIN LocalFormatted = FORMAT INTERLIS.XMLDate "1990-1-1" .. "2100-12-31";

                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END FormatCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyFormat = valid.replace("attr : TEXT;", "attr : FORMAT ");
        service.didChange(fullDocumentChange(uri, 2, dirtyFormat));
        List<CompletionItem> formatItems = completionItems(service, uri, dirtyFormat,
                dirtyFormat.indexOf("attr : FORMAT ") + "attr : FORMAT ".length());
        assertNotNull(findItemByLabel(formatItems, "INTERLIS.XMLDate"));
        assertNotNull(findItemByLabel(formatItems, "INTERLIS.XMLDateTime"));
        assertNotNull(findItemByLabel(formatItems, "INTERLIS.XMLTime"));
        assertNotNull(findItemByLabel(formatItems, "LocalFormatted"));
        assertNull(findItemByLabel(formatItems, "TextDomain"));
        assertNull(findItemByLabel(formatItems, "Status_Geschaeft"));
        assertEquals("BASED ON ${1:Structure} (${2:\"\"})", newText(findItemByLabel(formatItems, "BASED ON ...")));

        String dirtyBounds = valid.replace("attr : TEXT;", "attr : FORMAT INTERLIS.XMLDate");
        service.didChange(fullDocumentChange(uri, 3, dirtyBounds));
        List<CompletionItem> boundItems = completionItems(service, uri, dirtyBounds,
                dirtyBounds.indexOf("attr : FORMAT INTERLIS.XMLDate") + "attr : FORMAT INTERLIS.XMLDate".length());
        assertEquals("\"${1:1990-1-1}\" .. \"${2:2100-12-31}\"", newText(findItemByLabel(boundItems, "\"min\" .. \"max\"")));
    }

    @Test
    void completionAfterQualifiedImportedModelDoesNotFallBackToRootItems(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("ImportedModel.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL ImportedModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedFormatted = FORMAT INTERLIS.XMLDate "1990-1-1" .. "2100-12-31";
                  DOMAIN ImportedText = TEXT;
                END ImportedModel.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS ImportedModel;
                  TOPIC T =
                    CLASS C =
                      attr : TEXT;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));

        String dirty = usingContent.replace("attr : TEXT;", "attr : ImportedModel.");
        service.didChange(fullDocumentChange(uri, 2, dirty));
        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("attr : ImportedModel.") + "attr : ImportedModel.".length());
        assertFalse(labels.contains("ImportedFormatted"));
        assertFalse(labels.contains("ImportedText"));
        assertFalse(labels.contains("LIST OF ..."));
        assertFalse(labels.contains("INTERLIS.XMLDate"));
        assertTrue(labels.isEmpty());
    }

    @Test
    void completionAllowsClassesAndStructuresAfterClassExtends(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ClassExtendsCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL ClassExtendsCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    STRUCTURE BaseStruct =
                    END BaseStruct;

                    CLASS BaseClass =
                    END BaseClass;

                    CLASS Child EXTENDS BaseClass =
                    END Child;
                  END T;
                END ClassExtendsCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("CLASS Child EXTENDS BaseClass =", "CLASS Child EXTENDS ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("CLASS Child EXTENDS ") + "CLASS Child EXTENDS ".length());
        assertTrue(labels.contains("BaseClass"));
        assertTrue(labels.contains("BaseStruct"));
    }

    @Test
    void completionRespectsQualifiedClassExtendsImports(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseModel.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL BaseModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    STRUCTURE BaseStruct =
                    END BaseStruct;

                    CLASS BaseClass =
                    END BaseClass;
                  END T;
                END BaseModel.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseModel;
                  TOPIC T =
                    CLASS Child EXTENDS BaseModel.T.BaseClass =
                    END Child;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, usingContent)));

        String dirty = usingContent.replace("BaseModel.T.BaseClass =", "BaseModel.T.");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("BaseModel.T.") + "BaseModel.T.".length());
        assertTrue(labels.contains("BaseClass"));
        assertTrue(labels.contains("BaseStruct"));
    }

    @Test
    void completionRestrictsStructureExtendsTargets(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("StructureExtendsCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL StructureExtendsCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    STRUCTURE BaseStruct =
                    END BaseStruct;

                    CLASS BaseClass =
                    END BaseClass;

                    STRUCTURE Child EXTENDS BaseStruct =
                    END Child;
                  END T;
                END StructureExtendsCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("STRUCTURE Child EXTENDS BaseStruct =", "STRUCTURE Child EXTENDS ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("STRUCTURE Child EXTENDS ") + "STRUCTURE Child EXTENDS ".length());
        assertTrue(labels.contains("BaseStruct"));
        assertFalse(labels.contains("BaseClass"));
    }

    @Test
    void completionRestrictsAssociationExtendsTargets(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("AssociationExtendsCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL AssociationExtendsCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    STRUCTURE BaseStruct =
                    END BaseStruct;

                    CLASS BaseClass =
                    END BaseClass;

                    ASSOCIATION BaseAssoc =
                    END BaseAssoc;

                    ASSOCIATION Child EXTENDS BaseAssoc =
                    END Child;
                  END T;
                END AssociationExtendsCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirty = valid.replace("ASSOCIATION Child EXTENDS BaseAssoc =", "ASSOCIATION Child EXTENDS ");
        service.didChange(fullDocumentChange(uri, 2, dirty));

        List<String> labels = completionLabels(service, uri, dirty,
                dirty.indexOf("ASSOCIATION Child EXTENDS ") + "ASSOCIATION Child EXTENDS ".length());
        assertTrue(labels.contains("BaseAssoc"));
        assertFalse(labels.contains("BaseClass"));
        assertFalse(labels.contains("BaseStruct"));
    }

    @Test
    void completionRestrictsTopicExtendsTargetsAndOffersEqualsAfterTarget(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("TopicExtendsCompletion.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL TopicExtendsCompletion (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC BaseTopic =
                    CLASS Inner =
                    END Inner;
                  END BaseTopic;

                  TOPIC Child EXTENDS BaseTopic =
                  END Child;
                END TopicExtendsCompletion.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyExtends = valid.replace("TOPIC Child EXTENDS BaseTopic =", "TOPIC Child EXTENDS ");
        service.didChange(fullDocumentChange(uri, 2, dirtyExtends));
        List<String> targetLabels = completionLabels(service, uri, dirtyExtends,
                dirtyExtends.indexOf("TOPIC Child EXTENDS ") + "TOPIC Child EXTENDS ".length());
        assertTrue(targetLabels.contains("BaseTopic"));
        assertFalse(targetLabels.contains("Inner"));

        String dirtyAfterTarget = valid.replace("TOPIC Child EXTENDS BaseTopic =", "TOPIC Child EXTENDS BaseTopic ");
        service.didChange(fullDocumentChange(uri, 3, dirtyAfterTarget));
        List<CompletionItem> afterTargetItems = completionItems(service, uri, dirtyAfterTarget,
                dirtyAfterTarget.indexOf("TOPIC Child EXTENDS BaseTopic ") + "TOPIC Child EXTENDS BaseTopic ".length());
        assertEquals(List.of("="), afterTargetItems.stream().map(CompletionItem::getLabel).toList());
        assertEquals("=", newText(findItemByLabel(afterTargetItems, "=")));
    }

    @Test
    void prepareRenameUsesLiveSymbolRange(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("PrepareRename.ili");
        String content = """
                INTERLIS 2.3;
                MODEL PrepareRename (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE LocalStruct =
                  END LocalStruct;
                END PrepareRename.
                """;
        Files.writeString(file, content);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, content)));

        PrepareRenameParams params = new PrepareRenameParams(new TextDocumentIdentifier(uri),
                DocumentTracker.positionAt(content, content.indexOf("LocalStruct")));
        Either3<org.eclipse.lsp4j.Range, org.eclipse.lsp4j.PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior> result =
                service.prepareRename(params).get();

        assertNotNull(result);
        assertTrue(result.isSecond());
        assertEquals("LocalStruct", result.getSecond().getPlaceholder());
        assertEquals(new Position(2, 12), result.getSecond().getRange().getStart());
    }

    @Test
    void referencesAndRenameFollowImportedLocalFiles(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseModel.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL BaseModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE BaseType =
                  END BaseType;
                END BaseModel.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseModel;
                  TOPIC T =
                    CLASS C =
                      attr : BaseModel.BaseType;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        InterlisLanguageServer server = new InterlisLanguageServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String usingUri = usingFile.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(usingUri, "interlis", 1, usingContent)));

        int refOffset = usingContent.indexOf("BaseModel.BaseType") + "BaseModel.".length();
        Position refPosition = DocumentTracker.positionAt(usingContent, refOffset);

        ReferenceParams referenceParams = new ReferenceParams(new TextDocumentIdentifier(usingUri), refPosition, new ReferenceContext(true));
        List<? extends org.eclipse.lsp4j.Location> references = service.references(referenceParams).get();
        assertEquals(3, references.size(), "Expected declaration, END name, and imported reference");
        assertTrue(references.stream().anyMatch(location -> location.getUri().equals(baseFile.toUri().toString())));
        assertTrue(references.stream().anyMatch(location -> location.getUri().equals(usingUri)));

        RenameParams renameParams = new RenameParams();
        renameParams.setTextDocument(new TextDocumentIdentifier(usingUri));
        renameParams.setPosition(refPosition);
        renameParams.setNewName("BaseTypeRenamed");

        WorkspaceEdit edit = service.rename(renameParams).get();
        assertNotNull(edit);
        Map<String, List<TextEdit>> changes = edit.getChanges();
        assertNotNull(changes);
        assertTrue(changes.containsKey(usingUri));
        assertTrue(changes.containsKey(baseFile.toUri().toString()));
        assertTrue(changes.get(usingUri).stream().anyMatch(change -> "BaseModel.BaseTypeRenamed".equals(change.getNewText())));
        assertTrue(changes.get(baseFile.toUri().toString()).stream().anyMatch(change -> "BaseTypeRenamed".equals(change.getNewText())));
    }

    @Test
    void completionForMetaAttributesOnDefinitionsOffersOnlyRelevantAssignments(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("MetaAttributeDefinitions.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL MetaAttributeDefinitions (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Color = (
                    !!@ enum-meta
                    rot,
                    blau
                  );

                  TOPIC T =
                    !!@ struct-meta
                    STRUCTURE S =
                    END S;

                    !!@ class-meta
                    CLASS C =
                      !!@ constraint-meta
                      MANDATORY CONSTRAINT Name <> "";
                    END C;

                    ASSOCIATION Link =
                      !!@ role-meta
                      rel -- {0..*} C;
                    END Link;
                  END T;
                END MetaAttributeDefinitions.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyStructure = valid.replace("!!@ struct-meta", "!!@ ili2db.");
        service.didChange(fullDocumentChange(uri, 2, dirtyStructure));
        List<String> structureLabels = completionLabels(service, uri, dirtyStructure, offsetAfter(dirtyStructure, "!!@ ili2db."));
        assertTrue(structureLabels.contains("ili2db.mapping=MultiSurface"));
        assertTrue(structureLabels.contains("ili2db.mapping=Localised"));
        assertTrue(structureLabels.contains("ili2db.dispName=\"...\""));
        assertFalse(structureLabels.contains("ili2db.oid=INTERLIS.UUIDOID"));
        assertFalse(structureLabels.contains("ilivalid.keymsg=\"...\""));

        String dirtyClass = valid.replace("!!@ class-meta", "!!@ il");
        service.didChange(fullDocumentChange(uri, 3, dirtyClass));
        List<String> classLabels = completionLabels(service, uri, dirtyClass, offsetAfter(dirtyClass, "!!@ il"));
        assertTrue(classLabels.contains("ili2db.dispName=\"...\""));
        assertTrue(classLabels.contains("ili2db.oid=INTERLIS.UUIDOID"));
        assertTrue(classLabels.contains("ilivalid.keymsg=\"...\""));
        assertTrue(classLabels.contains("ilivalid.keymsg_<lang>=\"...\""));
        assertFalse(classLabels.contains("ili2db.mapping=ARRAY"));
        assertFalse(classLabels.contains("ili2db.mapping=MultiSurface"));

        String dirtyRole = valid.replace("!!@ role-meta", "!!@ iliv");
        service.didChange(fullDocumentChange(uri, 4, dirtyRole));
        List<String> roleLabels = completionLabels(service, uri, dirtyRole, offsetAfter(dirtyRole, "!!@ iliv"));
        assertTrue(roleLabels.contains("ilivalid.target=on"));
        assertTrue(roleLabels.contains("ilivalid.multiplicity=warning"));
        assertTrue(roleLabels.contains("ilivalid.requiredIn=bid1"));
        assertFalse(roleLabels.contains("ili2db.dispName=\"...\""));
        assertFalse(roleLabels.contains("ili2db.mapping=ARRAY"));

        String dirtyConstraintValidation = valid.replace("!!@ constraint-meta", "!!@ ilivalid.");
        service.didChange(fullDocumentChange(uri, 5, dirtyConstraintValidation));
        List<String> constraintValidationLabels = completionLabels(service, uri, dirtyConstraintValidation, offsetAfter(dirtyConstraintValidation, "!!@ ilivalid."));
        assertTrue(constraintValidationLabels.contains("ilivalid.check=on"));
        assertFalse(constraintValidationLabels.contains("ili2db.mapping=ARRAY"));

        String dirtyConstraintMessage = valid.replace("!!@ constraint-meta", "!!@ m");
        service.didChange(fullDocumentChange(uri, 6, dirtyConstraintMessage));
        List<String> constraintMessageLabels = completionLabels(service, uri, dirtyConstraintMessage, offsetAfter(dirtyConstraintMessage, "!!@ m"));
        assertTrue(constraintMessageLabels.contains("message=\"...\""));

        String dirtyConstraintName = valid.replace("!!@ constraint-meta", "!!@ n");
        service.didChange(fullDocumentChange(uri, 7, dirtyConstraintName));
        List<String> constraintNameLabels = completionLabels(service, uri, dirtyConstraintName, offsetAfter(dirtyConstraintName, "!!@ n"));
        assertTrue(constraintNameLabels.contains("name=c1023"));

        String dirtyEnum = valid.replace("!!@ enum-meta", "!!@ ili2db.d");
        service.didChange(fullDocumentChange(uri, 8, dirtyEnum));
        List<String> enumLabels = completionLabels(service, uri, dirtyEnum, offsetAfter(dirtyEnum, "!!@ ili2db.d"));
        assertTrue(enumLabels.contains("ili2db.dispName=\"...\""));
        assertFalse(enumLabels.contains("ilivalid.check=on"));
    }

    @Test
    void completionForMetaAttributesOnAttributesRespectsStructureAndReferenceKinds(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("MetaAttributeAttributes.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL MetaAttributeAttributes (en) AT "http://example.org" VERSION "2024-01-01" =
                  STRUCTURE LocalStruct =
                  END LocalStruct;

                  TOPIC T =
                    CLASS C =
                      !!@ primitive-meta
                      primitiveAttr : TEXT;
                      !!@ struct-meta
                      structAttr : LocalStruct;
                      !!@ list-meta
                      listAttr : BAG OF LocalStruct;
                      !!@ ref-meta
                      refAttr : REFERENCE TO C;
                    END C;
                  END T;
                END MetaAttributeAttributes.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyPrimitive = valid.replace("!!@ primitive-meta", "!!@ il");
        service.didChange(fullDocumentChange(uri, 2, dirtyPrimitive));
        List<String> primitiveLabels = completionLabels(service, uri, dirtyPrimitive, offsetAfter(dirtyPrimitive, "!!@ il"));
        assertTrue(primitiveLabels.contains("ili2db.dispName=\"...\""));
        assertTrue(primitiveLabels.contains("ilivalid.type=on"));
        assertTrue(primitiveLabels.contains("ilivalid.multiplicity=off"));
        assertFalse(primitiveLabels.contains("ili2db.mapping=ARRAY"));
        assertFalse(primitiveLabels.contains("ilivalid.requiredIn=bid1"));

        String dirtyStruct = valid.replace("!!@ struct-meta", "!!@ il");
        service.didChange(fullDocumentChange(uri, 3, dirtyStruct));
        List<String> structLabels = completionLabels(service, uri, dirtyStruct, offsetAfter(dirtyStruct, "!!@ il"));
        assertTrue(structLabels.contains("ili2db.mapping=ARRAY"));
        assertTrue(structLabels.contains("ili2db.mapping=JSON"));
        assertTrue(structLabels.contains("ili2db.mapping=EXPAND"));
        assertTrue(structLabels.contains("ilivalid.requiredIn=bid1"));

        String dirtyList = valid.replace("!!@ list-meta", "!!@ il");
        service.didChange(fullDocumentChange(uri, 4, dirtyList));
        List<String> listLabels = completionLabels(service, uri, dirtyList, offsetAfter(dirtyList, "!!@ il"));
        assertTrue(listLabels.contains("ili2db.mapping=ARRAY"));
        assertTrue(listLabels.contains("ili2db.mapping=JSON"));
        assertTrue(listLabels.contains("ili2db.mapping=EXPAND"));
        assertTrue(listLabels.contains("ilivalid.requiredIn=bid1"));

        String dirtyReference = valid.replace("!!@ ref-meta", "!!@ ilivalid.r");
        service.didChange(fullDocumentChange(uri, 5, dirtyReference));
        List<String> referenceLabels = completionLabels(service, uri, dirtyReference, offsetAfter(dirtyReference, "!!@ ilivalid.r"));
        assertTrue(referenceLabels.contains("ilivalid.requiredIn=bid1"));
        assertFalse(referenceLabels.contains("ili2db.mapping=ARRAY"));
    }

    @Test
    void completionForMetaAttributeValuesAndOrphansRespectsContext(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("MetaAttributeValues.ili");
        String valid = """
                INTERLIS 2.3;
                MODEL MetaAttributeValues (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    !!@ structure-value
                    STRUCTURE S =
                    END S;

                    CLASS C =
                      !!@ attribute-value
                      listAttr : BAG OF S;
                      !!@ severity-value
                      primitiveAttr : TEXT;
                      !!@ quoted-value
                      otherAttr : TEXT;
                    END C;

                    !!@ orphan-value
                  END T;
                END MetaAttributeValues.
                """;
        Files.writeString(file, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        InterlisTextDocumentService service = server.getInterlisTextDocumentService();

        String uri = file.toUri().toString();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        String dirtyStructure = valid.replace("!!@ structure-value", "!!@ ili2db.mapping=");
        service.didChange(fullDocumentChange(uri, 2, dirtyStructure));
        List<String> structureValueLabels = completionLabels(service, uri, dirtyStructure, offsetAfter(dirtyStructure, "!!@ ili2db.mapping="));
        assertTrue(structureValueLabels.contains("MultiSurface"));
        assertTrue(structureValueLabels.contains("Localised"));
        assertFalse(structureValueLabels.contains("ARRAY"));

        String dirtyAttribute = valid.replace("!!@ attribute-value", "!!@ ili2db.mapping=");
        service.didChange(fullDocumentChange(uri, 3, dirtyAttribute));
        List<String> attributeValueLabels = completionLabels(service, uri, dirtyAttribute, offsetAfter(dirtyAttribute, "!!@ ili2db.mapping="));
        assertTrue(attributeValueLabels.contains("ARRAY"));
        assertTrue(attributeValueLabels.contains("JSON"));
        assertTrue(attributeValueLabels.contains("EXPAND"));
        assertFalse(attributeValueLabels.contains("MultiSurface"));

        String dirtySeverity = valid.replace("!!@ severity-value", "!!@ ilivalid.type=");
        service.didChange(fullDocumentChange(uri, 4, dirtySeverity));
        List<String> severityLabels = completionLabels(service, uri, dirtySeverity, offsetAfter(dirtySeverity, "!!@ ilivalid.type="));
        assertEquals(List.of("on", "warning", "off"), severityLabels);

        String dirtyQuoted = valid.replace("!!@ quoted-value", "!!@ ili2db.dispName=");
        service.didChange(fullDocumentChange(uri, 5, dirtyQuoted));
        List<CompletionItem> quotedItems = completionItems(service, uri, dirtyQuoted, offsetAfter(dirtyQuoted, "!!@ ili2db.dispName="));
        assertEquals("\"${1:Text}\"", newText(findItemByLabel(quotedItems, "\"...\"")));

        String dirtyOrphan = valid.replace("!!@ orphan-value", "!!@ ili2db.");
        service.didChange(fullDocumentChange(uri, 6, dirtyOrphan));
        List<String> orphanLabels = completionLabels(service, uri, dirtyOrphan, offsetAfter(dirtyOrphan, "!!@ ili2db."));
        assertTrue(orphanLabels.isEmpty());
    }

    private static DidChangeTextDocumentParams fullDocumentChange(String uri, int version, String text) {
        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, version);
        org.eclipse.lsp4j.TextDocumentContentChangeEvent change = new org.eclipse.lsp4j.TextDocumentContentChangeEvent();
        change.setText(text);
        return new DidChangeTextDocumentParams(identifier, List.of(change));
    }

    private static List<String> completionLabels(InterlisTextDocumentService service,
                                                 String uri,
                                                 String text,
                                                 int offset) throws Exception {
        return completionItems(service, uri, text, offset).stream().map(CompletionItem::getLabel).toList();
    }

    private static List<CompletionItem> completionItems(InterlisTextDocumentService service,
                                                        String uri,
                                                        String text,
                                                        int offset) throws Exception {
        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(DocumentTracker.positionAt(text, offset));

        Either<List<CompletionItem>, org.eclipse.lsp4j.CompletionList> completion = service.completion(params).get();
        assertTrue(completion.isLeft());
        return completion.getLeft();
    }

    private static CompletionItem findItemByLabel(List<CompletionItem> items, String label) {
        return items.stream().filter(item -> label.equals(item.getLabel())).findFirst().orElse(null);
    }

    private static int indexOfLabel(List<CompletionItem> items, String label) {
        for (int i = 0; i < items.size(); i++) {
            if (label.equals(items.get(i).getLabel())) {
                return i;
            }
        }
        return -1;
    }

    private static String newText(CompletionItem item) {
        assertNotNull(item);
        assertNotNull(item.getTextEdit());
        assertTrue(item.getTextEdit().isLeft());
        return item.getTextEdit().getLeft().getNewText();
    }

    private static int offsetAfter(String text, String marker) {
        int index = text.indexOf(marker);
        assertTrue(index >= 0, "Marker not found: " + marker);
        return index + marker.length();
    }
}
