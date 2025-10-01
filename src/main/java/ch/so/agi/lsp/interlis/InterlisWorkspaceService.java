package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InterlisWorkspaceService implements WorkspaceService {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisWorkspaceService.class);
    
    private final InterlisLanguageServer server;
    private final CommandHandlers handlers;

    public InterlisWorkspaceService(InterlisLanguageServer server) {
        this.server = server;
        this.handlers = new CommandHandlers(server);
    }

    @Override
    public void didChangeConfiguration(org.eclipse.lsp4j.DidChangeConfigurationParams params) {
        // VS Code sends the whole section under params.getSettings()        
        server.setClientSettings(ClientSettings.from(params.getSettings()));
        LOG.info("Updated client settings: {}", server.getClientSettings());
    }

    @Override
    public void didChangeWatchedFiles(org.eclipse.lsp4j.DidChangeWatchedFilesParams params) {
        // Handle watched files if needed
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if (InterlisLanguageServer.CMD_COMPILE.equals(params.getCommand())
                || InterlisLanguageServer.CMD_GENERATE_UML.equals(params.getCommand())) {
            String pathOrUri = extractPath(params.getArguments());
            if (pathOrUri == null) {
                ResponseError err = new ResponseError(ResponseErrorCode.InvalidParams,
                        "Expected the .ili file URI as the first argument", null);
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(new ResponseErrorException(err));
                return failed;
            }

            if (InterlisLanguageServer.CMD_COMPILE.equals(params.getCommand())) {
                LOG.info("validate called with: {}", pathOrUri);
                return handlers.compile(pathOrUri);
            }

            LOG.info("uml generation called with: {}", pathOrUri);
            return handlers.generateUml(pathOrUri);
        }
        return CompletableFuture.completedFuture(null);
    }

    private String extractPath(List<Object> args) {
        String fileUriOrPath = coerceArgToString(args != null && !args.isEmpty() ? args.get(0) : null);
        if (fileUriOrPath == null || fileUriOrPath.isBlank()) {
            return null;
        }

        String pathOrUri = fileUriOrPath;
        if (pathOrUri.startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(pathOrUri));
                pathOrUri = p.toString();
            } catch (Exception e) {
                LOG.warn("Could not convert URI to path: {}", pathOrUri, e);
            }
        }
        return pathOrUri;
    }

    private static String coerceArgToString(Object arg) {
        if (arg == null) return null;
        if (arg instanceof String s) return s;
        if (arg instanceof JsonPrimitive jp && jp.isString()) return jp.getAsString();
        if (arg instanceof java.util.Map<?, ?> map) {
            // Some clients send objects; try common shapes
            Object uri = map.get("uri");
            if (uri instanceof String s) return s;
            Object path = map.get("path");
            if (path instanceof String s) return s;
        }
        // Fallback: last resort
        return String.valueOf(arg);
    }
}
