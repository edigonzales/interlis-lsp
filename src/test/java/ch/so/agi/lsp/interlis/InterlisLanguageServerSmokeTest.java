package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.UnregistrationParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Basic smoke tests that run the language server in-process. */
class InterlisLanguageServerSmokeTest {
    private Path sampleFile;

    @BeforeEach
    void setUp() {
        sampleFile = Path.of("src/test/data/TestSuite_mod-0.ili").toAbsolutePath();
        assertTrue(Files.exists(sampleFile), "Sample INTERLIS model is required for smoke tests");
    }

    @Test
    void compileCommandProducesLogAndDiagnostics() throws Exception {
        RecordingClient client = new RecordingClient();
        InterlisLanguageServer server = new InterlisLanguageServer();
        server.connect(client);

        InitializeParams init = new InitializeParams();
        init.setRootUri(sampleFile.getParent().toUri().toString());
        java.util.Map<String, Object> initOptions = new java.util.HashMap<>();
        initOptions.put("modelRepositories", sampleFile.getParent().toUri().toString());
        initOptions.put("suppressRepositoryLogs", Boolean.TRUE);
        init.setInitializationOptions(initOptions);
        server.initialize(init).get(30, TimeUnit.SECONDS);
        server.initialized(new InitializedParams());
        assertNotNull(server.getGlspServer(), "Smoke test requires the embedded GLSP server");
        assertTrue(server.getGlspServer().startAsync().get(10, TimeUnit.SECONDS),
                "GLSP server should confirm it started successfully");
        assertTrue(server.getGlspServer().isStarted(),
                "GLSP server should be marked as running after startup");

        TextDocumentItem document = new TextDocumentItem();
        document.setLanguageId("interlis");
        document.setText(Files.readString(sampleFile));
        document.setUri(sampleFile.toUri().toString());
        document.setVersion(1);

        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(document));

        // The compile on didOpen should have published diagnostics and logs.
        assertTrue(client.getPublishDiagnosticsCalls() >= 1, "Expected at least one diagnostics publication");
        assertTrue(client.getClearLogCalls() >= 1, "Expected the log channel to be cleared");
        assertFalse(client.getLogMessages().isEmpty(), "Expected compile log text to be sent to the client");
        String latestLog = client.getLogMessages().get(client.getLogMessages().size() - 1);
        assertTrue(latestLog.contains("compiler run done"),
                () -> "Unexpected compile log: " + client.getLogMessages());

        var execParams = new org.eclipse.lsp4j.ExecuteCommandParams();
        execParams.setCommand(InterlisLanguageServer.CMD_COMPILE);
        execParams.setArguments(java.util.List.of(document.getUri()));

        Object result = server.getWorkspaceService().executeCommand(execParams).get(30, TimeUnit.SECONDS);
        assertInstanceOf(String.class, result, "Compile command should return the compiler log text");
        assertTrue(((String) result).contains("compiler run done"), "Compile command response should include ili2c log");

        // The command invocation should have produced additional log notifications.
        assertTrue(client.getLogMessages().size() >= 2,
                () -> "Expected compile command to append log output, but captured " + client.getLogMessages());

        server.shutdown().get(10, TimeUnit.SECONDS);
        server.exit();
    }

    private static final class RecordingClient implements InterlisLanguageClient {
        private final List<String> logMessages = new ArrayList<>();
        private int clearLogCalls = 0;
        private int publishDiagnosticsCalls = 0;

        @Override
        public void clearLog() {
            clearLogCalls++;
        }

        @Override
        public void log(LogParams params) {
            if (params != null && params.text != null) {
                logMessages.add(params.text);
            }
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            publishDiagnosticsCalls++;
        }

        @Override
        public void telemetryEvent(Object object) { }

        @Override
        public void showMessage(MessageParams messageParams) { }

        @Override
        public void logMessage(MessageParams message) { }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
            return CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(true));
        }

        List<String> getLogMessages() {
            return logMessages;
        }

        int getClearLogCalls() {
            return clearLogCalls;
        }

        int getPublishDiagnosticsCalls() {
            return publishDiagnosticsCalls;
        }

    }
}
