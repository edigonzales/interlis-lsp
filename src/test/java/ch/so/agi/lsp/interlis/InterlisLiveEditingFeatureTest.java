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

    private static String newText(CompletionItem item) {
        assertNotNull(item);
        assertNotNull(item.getTextEdit());
        assertTrue(item.getTextEdit().isLeft());
        return item.getTextEdit().getLeft().getNewText();
    }
}
