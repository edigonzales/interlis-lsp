package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.export.html.InterlisHtmlExporter;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.workspace.CommandHandlers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

class CommandHandlersTest {

    @TempDir
    Path tempDir;

    @Test
    void generateUmlReturnsHtmlFromModel() throws Exception {
        Path iliFile = tempDir.resolve("SimpleModel.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleModel (en)\n" +
                "AT \"http://example.com/SimpleModel.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleModel.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        CompletableFuture<Object> future = handlers.generateUml(iliFile.toString());
        Object htmlObj = future.get(30, TimeUnit.SECONDS);
        String html = assertInstanceOf(String.class, htmlObj);

        assertTrue(html.contains("classDiagram"));
        assertTrue(html.contains("Person"));
    }

    @Test
    void generatePlantUmlReturnsHtmlFromModel() throws Exception {
        Path iliFile = tempDir.resolve("SimpleModelPlant.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleModelPlant (en)\n" +
                "AT \"http://example.com/SimpleModelPlant.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Thing =\n" +
                "    END Thing;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleModelPlant.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        CompletableFuture<Object> future = handlers.generatePlantUml(iliFile.toString());
        Object htmlObj = future.get(30, TimeUnit.SECONDS);
        String html = assertInstanceOf(String.class, htmlObj);

        assertTrue(html.contains("@startuml"));
        assertTrue(html.contains("https://uml.planttext.com/plantuml/png/"));
        assertTrue(html.contains("https://uml.planttext.com/plantuml/svg/"));
    }

    @Test
    void exportGraphmlReturnsDiagramText() throws Exception {
        Path iliFile = tempDir.resolve("SimpleGraph.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleGraph (en)\n" +
                "AT \"http://example.com/SimpleGraph.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS NodeA =\n" +
                "    END NodeA;\n" +
                "    CLASS NodeB =\n" +
                "    END NodeB;\n" +
                "    ASSOCIATION Connects =\n" +
                "      a -- {0..*} NodeA;\n" +
                "      b -- {1} NodeB;\n" +
                "    END Connects;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleGraph.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        CompletableFuture<String> future = handlers.exportGraphml(iliFile.toString());
        String graphml = future.get(30, TimeUnit.SECONDS);

        assertNotNull(graphml);
        assertTrue(graphml.contains("<graphml"));
        assertTrue(graphml.contains("NodeA"));
        assertTrue(graphml.contains("NodeB"));
    }

    @Test
    void exportDiagramModelReturnsContainerizedNodes() throws Exception {
        Path iliFile = tempDir.resolve("SimpleDiagramModel.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleDiagramModel (en)\n" +
                "AT \"http://example.com/SimpleDiagramModel.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "      Name : TEXT*40;\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleDiagramModel.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        InterlisDiagramModel.DiagramModel model = handlers.exportDiagramModel(iliFile.toString())
                .get(30, TimeUnit.SECONDS);

        assertNotNull(model);
        assertEquals("1", model.getSchemaVersion());
        assertTrue(model.getContainers().stream().anyMatch(c ->
                "topic".equals(c.getKind())
                        && c.getQualifiedName() != null
                        && c.getQualifiedName().contains("SimpleTopic")));
        assertTrue(model.getNodes().stream().anyMatch(n ->
                "Person".equals(n.getLabel())
                        && n.getContainerId() != null
                        && !n.getContainerId().isBlank()));
    }

    @Test
    void exportDiagramModelIncludesInlineAndDomainEnumerationValues() throws Exception {
        Path iliFile = tempDir.resolve("EnumDiagramModel.ili");
        Files.writeString(iliFile, """
                INTERLIS 2.3;
                MODEL EnumDiagramModel (en)
                AT "http://example.com/EnumDiagramModel.ili"
                VERSION "2024-01-01" =
                  TOPIC Demo (ABSTRACT) =
                    DOMAIN Farben = (gruen, blau, rot(hell, dunkel));
                    DOMAIN AlleFarben = ALL OF Farben;
                    CLASS MyBase (ABSTRACT) =
                      Aaaaaamyenum : (foo, bar);
                      FarbenAttr : Farben;
                    END MyBase;
                  END Demo;
                END EnumDiagramModel.
                """);

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        InterlisDiagramModel.DiagramModel model = handlers.exportDiagramModel(iliFile.toString())
                .get(30, TimeUnit.SECONDS);

        assertNotNull(model);

        InterlisDiagramModel.NodeModel myBase = model.getNodes().stream()
                .filter(node -> "MyBase".equals(node.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected MyBase diagram node"));
        assertTrue(myBase.getAttributes().contains("Aaaaaamyenum[0..1] : (foo, bar)"));
        assertFalse(myBase.getAttributes().contains("Aaaaaamyenum[0..1] : MyBase"));
        assertTrue(myBase.getAttributes().contains("FarbenAttr[0..1] : Farben"));

        InterlisDiagramModel.NodeModel farben = model.getNodes().stream()
                .filter(node -> "Farben".equals(node.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected Farben diagram node"));
        assertEquals(List.of("gruen", "blau", "rot.hell", "rot.dunkel"), farben.getAttributes());

        InterlisDiagramModel.NodeModel alleFarben = model.getNodes().stream()
                .filter(node -> "AlleFarben".equals(node.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected AlleFarben diagram node"));
        assertEquals(List.of("gruen", "blau", "rot", "rot.hell", "rot.dunkel"), alleFarben.getAttributes());
    }

    @Test
    void exportDiagramModelWritesDebugDumpWhenEnabled() throws Exception {
        Path iliFile = tempDir.resolve("SimpleDiagramDebug.ili");
        Path debugDump = tempDir.resolve("diagram-debug.json");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleDiagramDebug (en)\n" +
                "AT \"http://example.com/SimpleDiagramDebug.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "      Name : TEXT*40;\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleDiagramDebug.\n");

        String previous = System.getProperty("interlis.glsp.debugFile");
        System.setProperty("interlis.glsp.debugFile", debugDump.toString());
        try {
            InterlisLanguageServer server = new InterlisLanguageServer();
            CommandHandlers handlers = new CommandHandlers(server);

            InterlisDiagramModel.DiagramModel model = handlers.exportDiagramModel(iliFile.toString())
                    .get(30, TimeUnit.SECONDS);

            assertNotNull(model);
            assertTrue(Files.exists(debugDump));
            String json = Files.readString(debugDump);
            assertTrue(json.contains("\"diagramModel\""));
            assertTrue(json.contains("\"source\""));
            assertTrue(json.contains("Person"));
        } finally {
            if (previous == null) {
                System.clearProperty("interlis.glsp.debugFile");
            } else {
                System.setProperty("interlis.glsp.debugFile", previous);
            }
        }
    }

    @Test
    void exportDiagramModelUsesLastSuccessfulSnapshotForDirtyDocuments() throws Exception {
        Path iliFile = tempDir.resolve("DirtyDiagram.ili");
        String valid = "INTERLIS 2.3;\n" +
                "MODEL DirtyDiagram (en)\n" +
                "AT \"http://example.com/DirtyDiagram.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END DirtyDiagram.\n";
        Files.writeString(iliFile, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(iliFile.toUri().toString(), "interlis", 1, valid)));

        Files.writeString(iliFile, "MODEL Broken;");
        server.getInterlisTextDocumentService().didChange(fullDocumentChange(iliFile.toUri().toString(), 2, valid + "\n! dirty"));

        CommandHandlers handlers = new CommandHandlers(server);
        InterlisDiagramModel.DiagramModel model = handlers.exportDiagramModel(iliFile.toUri().toString())
                .get(30, TimeUnit.SECONDS);

        assertNotNull(model);
        assertTrue(model.getNodes().stream().anyMatch(n -> "Person".equals(n.getLabel())));
    }

    @Test
    void exportDiagramModelAsksToSaveWhenDirtyDocumentHasNoSuccessfulSnapshot() throws Exception {
        Path iliFile = tempDir.resolve("DirtyDiagramInvalid.ili");
        String invalid = "MODEL DirtyDiagramInvalid;";
        Files.writeString(iliFile, invalid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(iliFile.toUri().toString(), "interlis", 1, invalid)));
        server.getInterlisTextDocumentService().didChange(fullDocumentChange(iliFile.toUri().toString(), 2, invalid + "\nTOPIC Broken ="));

        CommandHandlers handlers = new CommandHandlers(server);
        CompletableFuture<InterlisDiagramModel.DiagramModel> future = handlers.exportDiagramModel(iliFile.toUri().toString());

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, exec.getCause());
        assertTrue(ree.getMessage().contains("Save the file to refresh the diagram"));
    }

    @Test
    void exportDiagramModelKeepsLastSuccessfulSnapshotForSavedInvalidDocuments() throws Exception {
        Path iliFile = tempDir.resolve("SavedInvalidDiagram.ili");
        String valid = "INTERLIS 2.3;\n" +
                "MODEL SavedInvalidDiagram (en)\n" +
                "AT \"http://example.com/SavedInvalidDiagram.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END SavedInvalidDiagram.\n";
        String invalid = valid + "TOPIC Broken =";
        Files.writeString(iliFile, valid);

        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        String uri = iliFile.toUri().toString();
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        Files.writeString(iliFile, invalid);
        server.getInterlisTextDocumentService().didChange(fullDocumentChange(uri, 2, invalid));

        org.eclipse.lsp4j.DidSaveTextDocumentParams saveParams = new org.eclipse.lsp4j.DidSaveTextDocumentParams();
        saveParams.setTextDocument(new org.eclipse.lsp4j.TextDocumentIdentifier(uri));
        server.getInterlisTextDocumentService().didSave(saveParams);

        CommandHandlers handlers = new CommandHandlers(server);
        InterlisDiagramModel.DiagramModel model = handlers.exportDiagramModel(uri).get(30, TimeUnit.SECONDS);

        assertNotNull(model);
        assertTrue(model.getNodes().stream().anyMatch(n -> "Person".equals(n.getLabel())));
    }

    @Test
    void exportDiagramModelReturnsFriendlyFailureForSavedBlankDocuments() throws Exception {
        Path iliFile = tempDir.resolve("SavedBlankDiagram.ili");
        String valid = "INTERLIS 2.3;\n" +
                "MODEL SavedBlankDiagram (en)\n" +
                "AT \"http://example.com/SavedBlankDiagram.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END SavedBlankDiagram.\n";
        Files.writeString(iliFile, valid);

        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());
        String uri = iliFile.toUri().toString();
        server.getInterlisTextDocumentService().didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis", 1, valid)));

        Files.writeString(iliFile, " \n\t");
        server.getInterlisTextDocumentService().didChange(fullDocumentChange(uri, 2, " \n\t"));

        org.eclipse.lsp4j.DidSaveTextDocumentParams saveParams = new org.eclipse.lsp4j.DidSaveTextDocumentParams();
        saveParams.setTextDocument(new org.eclipse.lsp4j.TextDocumentIdentifier(uri));
        server.getInterlisTextDocumentService().didSave(saveParams);

        CommandHandlers handlers = new CommandHandlers(server);
        CompletableFuture<InterlisDiagramModel.DiagramModel> future = handlers.exportDiagramModel(uri);

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, exec.getCause());
        assertTrue(ree.getMessage().contains("Source file is empty"));
        assertFalse(server.getDebugLogText().contains("REAL_COMPILE source=exportDiagramModel-fallback"),
                "Expected saved blank diagram requests to avoid fallback compilation");
    }

    @Test
    void exportDocxFailsWithResponseErrorWhenCompilationFails() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        Path nonexistent = tempDir.resolve("Missing.ili");
        CompletableFuture<String> future = handlers.exportDocx(nonexistent.toUri().toString(), null);

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        Throwable cause = exec.getCause();
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, cause);
        assertEquals(ResponseErrorCode.InternalError.getValue(), ree.getResponseError().getCode());
        assertTrue(ree.getMessage().contains(nonexistent.getFileName().toString()));
    }

    @Test
    void exportGraphmlFailsWithResponseErrorWhenCompilationFails() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        Path nonexistent = tempDir.resolve("MissingGraph.ili");
        CompletableFuture<String> future = handlers.exportGraphml(nonexistent.toUri().toString());

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        Throwable cause = exec.getCause();
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, cause);
        assertEquals(ResponseErrorCode.InternalError.getValue(), ree.getResponseError().getCode());
        assertTrue(ree.getMessage().contains(nonexistent.getFileName().toString()));
    }

    @Test
    void exportHtmlFailsWithResponseErrorWhenCompilationFails() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        Path nonexistent = tempDir.resolve("MissingHtml.ili");
        CompletableFuture<String> future = handlers.exportHtml(nonexistent.toUri().toString(), null);

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        Throwable cause = exec.getCause();
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, cause);
        assertEquals(ResponseErrorCode.InternalError.getValue(), ree.getResponseError().getCode());
        assertTrue(ree.getMessage().contains(nonexistent.getFileName().toString()));
    }

    @Test
    void compileReturnsInfoMessageForBlankFileAndSkipsCompilation() throws Exception {
        Path blank = tempDir.resolve("BlankCompile.ili");
        Files.writeString(blank, " \n\t");

        RecordingServer server = new RecordingServer();
        server.setClientSettings(new ClientSettings());
        CommandHandlers handlers = new CommandHandlers(server);

        Object result = handlers.compile(blank.toUri().toString()).get(30, TimeUnit.SECONDS);

        String message = assertInstanceOf(String.class, result);
        assertTrue(message.contains("Source file is empty"));
        assertEquals(1, server.getCompileFinishedCount(), "Expected blank compile to publish compile completion");
        assertTrue(server.getDiagnostics(blank.toUri().toString()).isEmpty(), "Expected blank compile to clear diagnostics");
        assertTrue(server.getDebugLogText().contains("SKIP_COMPILE source=compile-command"),
                "Expected blank compile command to emit a skip marker");
        assertFalse(server.getDebugLogText().contains("REAL_COMPILE source=compile-command"),
                "Expected blank compile command to avoid real compilation");
    }

    @Test
    void compilePublishesUnusedImportWarningAsLint() throws Exception {
        Path baseFile = tempDir.resolve("BaseTypes.ili");
        Files.writeString(baseFile, """
                INTERLIS 2.3;
                MODEL BaseTypes (en) AT "http://example.org" VERSION "2024-01-01" =
                  DOMAIN ImportedDomain = TEXT*20;
                END BaseTypes.
                """);

        Path usingFile = tempDir.resolve("UsingModel.ili");
        Files.writeString(usingFile, """
                INTERLIS 2.3;
                MODEL UsingModel (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS BaseTypes;
                  TOPIC T =
                  END T;
                END UsingModel.
                """);

        RecordingServer server = new RecordingServer();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(tempDir.toAbsolutePath().toString());
        server.setClientSettings(settings);

        CommandHandlers handlers = new CommandHandlers(server);
        handlers.compile(usingFile.toUri().toString()).get(30, TimeUnit.SECONDS);

        Diagnostic diagnostic = server.getDiagnostics(usingFile.toUri().toString()).stream()
                .filter(item -> item.getMessage() != null && item.getMessage().contains("never used"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected unused-import warning after compile command"));
        assertEquals(DiagnosticSeverity.Warning, diagnostic.getSeverity());
        assertEquals("lint", diagnostic.getSource());
        assertNotNull(diagnostic.getTags());
        assertTrue(diagnostic.getTags().contains(DiagnosticTag.Unnecessary));
    }

    private static DidChangeTextDocumentParams fullDocumentChange(String uri, int version, String text) {
        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, version);
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setText(text);
        return new DidChangeTextDocumentParams(identifier, java.util.List.of(change));
    }

    private static final class RecordingServer extends InterlisLanguageServer {
        private List<Diagnostic> diagnostics = List.of();
        private final StringBuilder debugLogBuffer = new StringBuilder();
        private int compileFinishedCount;

        @Override
        public void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
            this.diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        }

        @Override
        public void debugLogToClient(String text) {
            if (text == null) {
                return;
            }
            debugLogBuffer.append(text);
        }

        @Override
        public void notifyCompileFinished(String uri, boolean success) {
            compileFinishedCount++;
        }

        List<Diagnostic> getDiagnostics(String uri) {
            return diagnostics;
        }

        String getDebugLogText() {
            return debugLogBuffer.toString();
        }

        int getCompileFinishedCount() {
            return compileFinishedCount;
        }
    }
}
