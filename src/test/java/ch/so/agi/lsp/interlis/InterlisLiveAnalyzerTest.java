package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.live.DocumentSnapshot;
import ch.so.agi.lsp.interlis.live.InterlisLiveAnalyzer;
import ch.so.agi.lsp.interlis.live.LiveParseResult;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterlisLiveAnalyzerTest {

    @Test
    void incompleteListMarksCollectionKeywordInsteadOfNextEnd() {
        String text = """
                INTERLIS 2.3;
                MODEL DiagnosticList (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr555: LIST
                    END C;
                  END T;
                END DiagnosticList.
                """;

        LiveParseResult result = analyze("file:///DiagnosticList.ili", text);

        Range expected = range(text, "LIST");
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> sameRange(expected, diagnostic.getRange())),
                "Expected diagnostic to point at LIST instead of END");
    }

    @Test
    void incompleteReferenceMarksReferenceKeyword() {
        String text = """
                INTERLIS 2.3;
                MODEL DiagnosticReference (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      ref: REFERENCE
                    END C;
                  END T;
                END DiagnosticReference.
                """;

        LiveParseResult result = analyze("file:///DiagnosticReference.ili", text);

        Range expected = range(text, "REFERENCE");
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> sameRange(expected, diagnostic.getRange())),
                "Expected diagnostic to point at REFERENCE");
    }

    @Test
    void danglingExtendsMarksExtendsClause() {
        String text = """
                INTERLIS 2.3;
                MODEL DiagnosticExtends (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS Child EXTENDS
                    END Child;
                  END T;
                END DiagnosticExtends.
                """;

        LiveParseResult result = analyze("file:///DiagnosticExtends.ili", text);

        Range expected = range(text, "EXTENDS");
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> sameRange(expected, diagnostic.getRange())),
                "Expected diagnostic to point at EXTENDS");
    }

    @Test
    void endNameMismatchMarksWholeEndClauseAndAddsRelatedInformation() {
        String text = """
                INTERLIS 2.3;
                MODEL DiagnosticEnd (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS MyClass =
                    END MyClassC;
                  END T;
                END DiagnosticEnd.
                """;

        LiveParseResult result = analyze("file:///DiagnosticEnd.ili", text);

        Diagnostic mismatch = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getMessage() != null && diagnostic.getMessage().contains("END name mismatch"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected END mismatch diagnostic"));

        Range expected = range(text, "END MyClassC");
        assertTrue(sameRange(expected, mismatch.getRange()));
        assertNotNull(mismatch.getRelatedInformation());
        assertEquals(1, mismatch.getRelatedInformation().size());
        assertEquals("Container starts here", mismatch.getRelatedInformation().get(0).getMessage());
    }

    @Test
    void unknownTypeProducesLiveSemanticDiagnostic() {
        String text = """
                INTERLIS 2.3;
                MODEL UnknownType (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : xxxxxxx;
                    END C;
                  END T;
                END UnknownType.
                """;

        LiveParseResult result = analyze("file:///UnknownType.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Unknown"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected unknown-type diagnostic"));
        assertTrue(sameRange(range(text, "xxxxxxx"), diagnostic.getRange()));
    }

    @Test
    void forwardReferenceProducesLiveSemanticDiagnostic() {
        String text = """
                INTERLIS 2.3;
                MODEL ForwardType (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : Gruppe;
                    END C;

                    DOMAIN Gruppe = TEXT;
                  END T;
                END ForwardType.
                """;

        LiveParseResult result = analyze("file:///ForwardType.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("not visible here yet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected forward-reference diagnostic"));
        int referenceStart = text.indexOf("attr : Gruppe;") + "attr : ".length();
        Range expected = new Range(
                DocumentTracker.positionAt(text, referenceStart),
                DocumentTracker.positionAt(text, referenceStart + "Gruppe".length()));
        assertTrue(sameRange(expected, diagnostic.getRange()));
    }

    @Test
    void duplicateDeclarationProducesLiveSemanticDiagnostic() {
        String text = """
                INTERLIS 2.3;
                MODEL DuplicateType (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Gruppe = TEXT;

                  STRUCTURE Gruppe =
                  END Gruppe;
                END DuplicateType.
                """;

        LiveParseResult result = analyze("file:///DuplicateType.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Duplicate declaration"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected duplicate declaration diagnostic"));

        int second = text.indexOf("STRUCTURE Gruppe =") + "STRUCTURE ".length();
        Range expected = new Range(
                DocumentTracker.positionAt(text, second),
                DocumentTracker.positionAt(text, second + "Gruppe".length()));
        assertTrue(sameRange(expected, diagnostic.getRange()));
    }

    @Test
    void missingSemicolonMarksSimpleTypeClause() {
        String text = """
                INTERLIS 2.3;
                MODEL MissingSemicolonSimple (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : BOOLEAN
                    END C;
                  END T;
                END MissingSemicolonSimple.
                """;

        LiveParseResult result = analyze("file:///MissingSemicolonSimple.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing ';'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected missing-semicolon diagnostic"));
        assertTrue(sameRange(range(text, "BOOLEAN"), diagnostic.getRange()));
    }

    @Test
    void missingSemicolonMarksQualifiedTypeClause() {
        String text = """
                INTERLIS 2.3;
                MODEL MissingSemicolonQualified (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : INTERLIS.XMLDate
                    END C;
                  END T;
                END MissingSemicolonQualified.
                """;

        LiveParseResult result = analyze("file:///MissingSemicolonQualified.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing ';'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected missing-semicolon diagnostic"));
        assertTrue(sameRange(range(text, "INTERLIS.XMLDate"), diagnostic.getRange()));
    }

    @Test
    void missingAttributeHeadMarksAttributeNameWhenSemicolonIsPresent() {
        String text = """
                INTERLIS 2.3;
                MODEL MissingAttributeHeadWithSemicolon (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr;
                    END C;
                  END T;
                END MissingAttributeHeadWithSemicolon.
                """;

        LiveParseResult result = analyze("file:///MissingAttributeHeadWithSemicolon.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing ':' and type"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected missing-attribute-head diagnostic"));
        assertTrue(sameRange(range(text, "attr"), diagnostic.getRange()));
    }

    @Test
    void missingAttributeHeadMarksAttributeNameBeforeEnd() {
        String text = """
                INTERLIS 2.3;
                MODEL MissingAttributeHeadBeforeEnd (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr
                    END C;
                  END T;
                END MissingAttributeHeadBeforeEnd.
                """;

        LiveParseResult result = analyze("file:///MissingAttributeHeadBeforeEnd.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing ':' and type"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected missing-attribute-head diagnostic"));
        assertTrue(sameRange(range(text, "attr"), diagnostic.getRange()));
    }

    @Test
    void missingAttributeTypeMarksAttributeHeadWithColon() {
        String text = """
                INTERLIS 2.3;
                MODEL MissingAttributeTypeWithColon (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr:
                    END C;
                  END T;
                END MissingAttributeTypeWithColon.
                """;

        LiveParseResult result = analyze("file:///MissingAttributeTypeWithColon.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing type after ':'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected missing-attribute-type diagnostic"));
        assertTrue(sameRange(range(text, "attr:"), diagnostic.getRange()));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("END name mismatch")),
                "Expected missing-attribute-type diagnostic without END mismatch cascade but got: " + result.diagnostics());
    }

    @Test
    void missingAttributeTypeMarksAttributeHeadWithColonBeforeEnd() {
        String text = """
                INTERLIS 2.3;
                MODEL MissingAttributeTypeBeforeEnd (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr:   
                    END C;
                  END T;
                END MissingAttributeTypeBeforeEnd.
                """;

        LiveParseResult result = analyze("file:///MissingAttributeTypeBeforeEnd.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing type after ':'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected missing-attribute-type diagnostic"));
        assertTrue(sameRange(range(text, "attr:"), diagnostic.getRange()));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("END name mismatch")),
                "Expected missing-attribute-type diagnostic without END mismatch cascade but got: " + result.diagnostics());
    }

    @Test
    void factorOnlyAttributeValueMarksNumericLiteral() {
        String text = """
                INTERLIS 2.3;
                MODEL FactorOnlyNumeric (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr:0;
                    END C;
                  END T;
                END FactorOnlyNumeric.
                """;

        LiveParseResult result = analyze("file:///FactorOnlyNumeric.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing type before value after ':'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected factor-only attribute diagnostic"));
        int start = text.indexOf("attr:0;") + "attr:".length();
        Range expected = new Range(
                DocumentTracker.positionAt(text, start),
                DocumentTracker.positionAt(text, start + "0".length()));
        assertTrue(sameRange(expected, diagnostic.getRange()));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null
                                && item.getMessage().contains("Missing ';' after attribute definition")),
                "Expected factor-only attribute diagnostic without missing-semicolon fallback but got: " + result.diagnostics());
    }

    @Test
    void factorOnlyAttributeValueBeforeEndMarksNumericLiteral() {
        String text = """
                INTERLIS 2.3;
                MODEL FactorOnlyNumericBeforeEnd (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr:0
                    END C;
                  END T;
                END FactorOnlyNumericBeforeEnd.
                """;

        LiveParseResult result = analyze("file:///FactorOnlyNumericBeforeEnd.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing type before value after ':'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected factor-only attribute diagnostic"));
        int start = text.indexOf("attr:0") + "attr:".length();
        Range expected = new Range(
                DocumentTracker.positionAt(text, start),
                DocumentTracker.positionAt(text, start + "0".length()));
        assertTrue(sameRange(expected, diagnostic.getRange()));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null
                                && item.getMessage().contains("Missing ';' after attribute definition")),
                "Expected factor-only attribute diagnostic without missing-semicolon fallback but got: " + result.diagnostics());
    }

    @Test
    void factorOnlyAttributeValueMarksStringLiteral() {
        String text = """
                INTERLIS 2.3;
                MODEL FactorOnlyString (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr:"x";
                    END C;
                  END T;
                END FactorOnlyString.
                """;

        LiveParseResult result = analyze("file:///FactorOnlyString.ili", text);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("Missing type before value after ':'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected factor-only attribute diagnostic"));
        assertTrue(sameRange(range(text, "\"x\""), diagnostic.getRange()));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null
                                && item.getMessage().contains("Missing ';' after attribute definition")),
                "Expected factor-only attribute diagnostic without missing-semicolon fallback but got: " + result.diagnostics());
    }

    @Test
    void inlineNumericRangeRemainsValidAndDoesNotProduceFactorOnlyDiagnostic() {
        String text = """
                INTERLIS 2.3;
                MODEL InlineNumericRange (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr:0..10;
                    END C;
                  END T;
                END InlineNumericRange.
                """;

        LiveParseResult result = analyze("file:///InlineNumericRange.ili", text);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null
                                && item.getMessage().contains("Missing type before value after ':'")),
                "Expected inline numeric range to stay free of factor-only diagnostics but got: " + result.diagnostics());
    }

    @Test
    void importedTypesStayValidInDirtyDocumentWithAuthoritativeFallback(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS UNQUALIFIED BaseTypes;
                  TOPIC T =
                    CLASS C =
                      qualifiedAttr : BaseTypes.ImportedDomain;
                      unqualifiedAttr : ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("qualifiedAttr : BaseTypes.ImportedDomain;",
                "qualifiedAttr  : BaseTypes.ImportedDomain;");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.importedModelNames().contains("BaseTypes"));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("ImportedDomain")),
                "Expected imported names to stay valid in dirty mode but got: " + result.diagnostics());
    }

    @Test
    void unusedImportProducesWarningDiagnosticWithUnnecessaryTag(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent + "\n";

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        Diagnostic diagnostic = result.diagnostics().stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected unused-import warning"));
        assertEquals(DiagnosticSeverity.Warning, diagnostic.getSeverity());
        assertNotNull(diagnostic.getTags());
        assertTrue(diagnostic.getTags().contains(DiagnosticTag.Unnecessary));
        assertTrue(sameRange(range(usingContent, "BaseTypes"), diagnostic.getRange()));
    }

    @Test
    void qualifiedImportedUseSuppressesUnusedImportWarning(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    DOMAIN ImportedDomain = TEXT*20;
                  END T;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                    CLASS C =
                      attr : BaseTypes.T.ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("attr : BaseTypes.T.ImportedDomain;", "attr  : BaseTypes.T.ImportedDomain;");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("never used")),
                "Expected qualified imported usage to suppress unused-import warnings but got: " + result.diagnostics());
    }

    @Test
    void unqualifiedImportedUseSuppressesUnusedImportWarning(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS UNQUALIFIED BaseTypes;
                  TOPIC T =
                    CLASS C =
                      attr : ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("attr : ImportedDomain;", "attr  : ImportedDomain;");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("never used")),
                "Expected unqualified imported usage to suppress unused-import warnings but got: " + result.diagnostics());
    }

    @Test
    void halfFinishedQualifiedImportedUseSuppressesUnusedImportWarning(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                    CLASS C =
                      attr : BaseTypes.ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("BaseTypes.ImportedDomain;", "BaseTypes.");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("never used")),
                "Expected partial qualified usage to suppress unused-import warnings but got: " + result.diagnostics());
    }

    @Test
    void brokenImportClauseDoesNotProduceUnusedImportWarning() {
        String text = """
                INTERLIS 2.3;
                MODEL BrokenImport (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes
                  TOPIC T =
                  END T;
                END BrokenImport.
                """;

        LiveParseResult result = analyze("file:///BrokenImport.ili", text);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(item -> item.getMessage() != null && item.getMessage().contains("never used")),
                "Expected broken IMPORTS clause to suppress unused-import warnings but got: " + result.diagnostics());
    }

    @Test
    void qualifiedImportedTopicTypesStayValidInDirtyDocument(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    DOMAIN ImportedDomain = TEXT*20;
                  END T;
                END BaseTypes.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                    CLASS C =
                      qualifiedAttr : BaseTypes.T.ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("qualifiedAttr : BaseTypes.T.ImportedDomain;",
                "qualifiedAttr  : BaseTypes.T.ImportedDomain;");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.importedModelNames().contains("BaseTypes"));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("BaseTypes.T.ImportedDomain")),
                "Expected qualified imported topic type to stay valid in dirty mode but got: " + result.diagnostics());
    }

    @Test
    void removingImportMakesImportedTypesUnknownAgain(@TempDir Path tempDir) throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        String baseContent = """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """;
        Files.writeString(baseFile, baseContent);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS UNQUALIFIED BaseTypes;
                  TOPIC T =
                    CLASS C =
                      qualifiedAttr : BaseTypes.ImportedDomain;
                      unqualifiedAttr : ImportedDomain;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("  IMPORTS UNQUALIFIED BaseTypes;\n", "");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.importedModelNames().isEmpty());
        assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("BaseTypes.ImportedDomain")),
                "Expected qualified imported type to become unknown when IMPORTS is removed");
        assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("ImportedDomain")),
                "Expected unqualified imported type to become unknown when IMPORTS is removed");
    }

    @Test
    void predefinedInterlisDomainsStayValidInDirtyDocument(@TempDir Path tempDir) throws Exception {
        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  TOPIC T =
                    CLASS C =
                      attr : INTERLIS.XMLDate;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("attr : INTERLIS.XMLDate;", "attr  : INTERLIS.XMLDate;");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("INTERLIS.XMLDate")),
                "Expected INTERLIS.XMLDate to stay valid in dirty mode but got: " + result.diagnostics());
    }

    @Test
    void qualifiedImportedDomainsStayValidWithMultipleImports(@TempDir Path tempDir) throws Exception {
        Path geometryFile = tempDir.resolve("GeometryCHLV95_V1.ili");
        Files.writeString(geometryFile, """
                INTERLIS 2.3;
                MODEL GeometryCHLV95_V1 (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN MultiSurface = TEXT*20;
                END GeometryCHLV95_V1.
                """);
        Path textFile = tempDir.resolve("Text.ili");
        Files.writeString(textFile, """
                INTERLIS 2.3;
                MODEL Text (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Label = TEXT*30;
                END Text.
                """);
        Path mathFile = tempDir.resolve("Math.ili");
        Files.writeString(mathFile, """
                INTERLIS 2.3;
                MODEL Math (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN Number = 0 .. 10;
                END Math.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        String usingContent = """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS GeometryCHLV95_V1, Text, Math;
                  TOPIC T =
                    CLASS C =
                      attr : GeometryCHLV95_V1.MultiSurface;
                    END C;
                  END T;
                END UsingModel.
                """;
        Files.writeString(usingFile, usingContent);

        TransferDescription authoritativeTd = compile(tempDir, usingFile);
        String dirty = usingContent.replace("attr : GeometryCHLV95_V1.MultiSurface;",
                "attr  : GeometryCHLV95_V1.MultiSurface;");

        LiveParseResult result = analyze(usingFile.toUri().toString(), usingFile.toString(), dirty, authoritativeTd);

        assertTrue(result.importedModelNames().contains("GeometryCHLV95_V1"));
        assertTrue(result.importedModelNames().contains("Text"));
        assertTrue(result.importedModelNames().contains("Math"));
        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("GeometryCHLV95_V1.MultiSurface")),
                "Expected qualified imported domain to stay valid with multiple IMPORTS but got: " + result.diagnostics());
    }

    @Test
    void directAttributeTypesAllowInterlisUriUuidOidAndQualifiedStructures(@TempDir Path tempDir) throws Exception {
        Path modelFile = tempDir.resolve("DirtyTypes.ili");
        String content = """
                INTERLIS 2.4;
                MODEL DirtyTypes (de) AT "https://example.org" VERSION "2026-03-24" =
                  TOPIC Json =
                    STRUCTURE Dokument =
                      Name : TEXT*255;
                    END Dokument;
                    CLASS Example =
                      documents : BAG {1..*} OF DirtyTypes.Json.Dokument;
                      document : MANDATORY DirtyTypes.Json.Dokument;
                      url : MANDATORY INTERLIS.URI;
                      uuid : MANDATORY INTERLIS.UUIDOID;
                    END Example;
                  END Json;
                END DirtyTypes.
                """;
        Files.writeString(modelFile, content);

        TransferDescription authoritativeTd = compile(tempDir, modelFile);
        String dirty = content.replace("document : MANDATORY DirtyTypes.Json.Dokument;",
                "document  : MANDATORY DirtyTypes.Json.Dokument;");

        LiveParseResult result = analyze(modelFile.toUri().toString(), modelFile.toString(), dirty, authoritativeTd);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("DirtyTypes.Json.Dokument")),
                "Expected qualified structure types to stay valid in dirty mode but got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("INTERLIS.URI")),
                "Expected INTERLIS.URI to stay valid in dirty mode but got: " + result.diagnostics());
        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("INTERLIS.UUIDOID")),
                "Expected INTERLIS.UUIDOID to stay valid in dirty mode but got: " + result.diagnostics());
    }

    @Test
    void collectionTypesStillRejectDomains() {
        String text = """
                INTERLIS 2.4;
                MODEL InvalidCollectionType (de) AT "https://example.org" VERSION "2026-03-24" =
                  DOMAIN Label = TEXT*255;
                  TOPIC T =
                    CLASS Example =
                      labels : BAG {1..*} OF Label;
                    END Example;
                  END T;
                END InvalidCollectionType.
                """;

        LiveParseResult result = analyze("file:///InvalidCollectionType.ili", text);

        assertTrue(result.diagnostics().stream()
                        .anyMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Unknown")
                                && diagnostic.getMessage().contains("Label")),
                "Expected BAG OF domain to stay rejected but got: " + result.diagnostics());
    }

    @Test
    void multilineInlineEnumerationsWithMetaAttributesDoNotTriggerMissingSemicolon() {
        String text = """
                INTERLIS 2.4;
                MODEL InlineEnumMeta (de) AT "https://example.org" VERSION "2026-03-24" =
                  TOPIC T =
                    CLASS Example =
                      Inline_Linear : MANDATORY (
                        rot,
                        !!@ ili2db.dispName=grün
                        gruen,
                        gelb
                      );
                      Inline_Baumstruktur : MANDATORY (
                        rot (
                          hellrot,
                          dunkelrot
                        ),
                        !!@ ili2db.dispName=grün
                        gruen,
                        gelb
                      );
                    END Example;
                  END T;
                END InlineEnumMeta.
                """;

        LiveParseResult result = analyze("file:///InlineEnumMeta.ili", text);

        assertTrue(result.diagnostics().stream()
                        .noneMatch(diagnostic -> diagnostic.getMessage() != null
                                && diagnostic.getMessage().contains("Missing ';' after attribute definition")),
                "Expected multiline inline enumerations to stay free of missing-semicolon diagnostics but got: "
                        + result.diagnostics());
    }

    private static LiveParseResult analyze(String uri, String text) {
        InterlisLiveAnalyzer analyzer = new InterlisLiveAnalyzer();
        return analyzer.analyze(new DocumentSnapshot(uri, null, text, 1));
    }

    private static LiveParseResult analyze(String uri, String path, String text, TransferDescription authoritativeTd) {
        InterlisLiveAnalyzer analyzer = new InterlisLiveAnalyzer();
        return analyzer.analyze(new DocumentSnapshot(uri, path, text, 2), authoritativeTd);
    }

    private static TransferDescription compile(Path repositoryDir, Path modelFile) {
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repositoryDir.toAbsolutePath().toString());
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(settings, modelFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());
        return outcome.getTransferDescription();
    }

    private static Range range(String text, String needle) {
        int start = text.indexOf(needle);
        assertTrue(start >= 0, "Needle not found: " + needle);
        return new Range(
                DocumentTracker.positionAt(text, start),
                DocumentTracker.positionAt(text, start + needle.length()));
    }

    private static boolean sameRange(Range left, Range right) {
        return left != null
                && right != null
                && samePosition(left.getStart(), right.getStart())
                && samePosition(left.getEnd(), right.getEnd());
    }

    private static boolean samePosition(Position left, Position right) {
        return left != null
                && right != null
                && left.getLine() == right.getLine()
                && left.getCharacter() == right.getCharacter();
    }
}
