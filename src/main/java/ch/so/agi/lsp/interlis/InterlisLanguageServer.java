package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main LSP entrypoint. Wires TextDocument/Workspace services and exposes capabilities.
 */
public class InterlisLanguageServer implements LanguageServer, LanguageClientAware {
    private InterlisLanguageClient client;
    
    private final InterlisTextDocumentService textDocumentService;
    private final InterlisWorkspaceService workspaceService;
    
    private final AtomicReference<ClientSettings> clientSettings = new AtomicReference<>(new ClientSettings());

    public static final String CMD_COMPILE = "interlis.compile"; // workspace/executeCommand

    public InterlisLanguageServer() {
        this.textDocumentService = new InterlisTextDocumentService(this);
        this.workspaceService = new InterlisWorkspaceService(this);
    }

    // ---- LanguageServer ----

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        setClientSettings(ClientSettings.from(params.getInitializationOptions()));
        
        ServerCapabilities caps = new ServerCapabilities();

        TextDocumentSyncOptions sync = new TextDocumentSyncOptions();
        sync.setChange(TextDocumentSyncKind.Incremental);
        sync.setOpenClose(true);
        caps.setTextDocumentSync(Either.forRight(sync));

        // request didSave text
        SaveOptions save = new SaveOptions(); save.setIncludeText(false); 
        sync.setSave(save);
        
        ExecuteCommandOptions exec = new ExecuteCommandOptions(Collections.singletonList(CMD_COMPILE));
        caps.setExecuteCommandProvider(exec);

        caps.setPositionEncoding(org.eclipse.lsp4j.PositionEncodingKind.UTF16);
        caps.setDocumentFormattingProvider(true);

        InitializeResult result = new InitializeResult(caps);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        // no-op
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // no-op
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    // ---- LanguageClientAware ----

    @Override
    public void connect(LanguageClient client) {
        this.client = (InterlisLanguageClient) client;
    }

    public LanguageClient getClient() {
        return client;
    }

    public void publishDiagnostics(String uri, List<Diagnostic> diagnostics) {
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
        }
    }
    
    public ClientSettings getClientSettings() {
        return clientSettings.get();
    }

    public void setClientSettings(ClientSettings s) {
        clientSettings.set(s != null ? s : new ClientSettings());
    }
    
    public void clearOutput() {
        if (client != null)
            client.clearLog();
    }
    
    public void logToClient(String text) {
        if (client == null || text == null || text.isBlank()) return;
        // If logs can be huge, chunk them to avoid oversized JSON payloads (optional)
        final int CHUNK = 8000; // chars
        for (int i = 0; i < text.length(); i += CHUNK) {
            client.log(new InterlisLanguageClient.LogParams(text.substring(i, Math.min(text.length(), i + CHUNK))));
        }    
    }
}
