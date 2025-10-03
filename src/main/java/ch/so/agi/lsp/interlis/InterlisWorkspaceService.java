package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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

    @JsonRequest(InterlisLanguageServer.REQ_EXPORT_DOCX)
    public CompletableFuture<String> exportDocx(Object rawParams) {
        ExportDocxParams params = coerceExportParams(rawParams);
        if (params == null) {
            return invalidParams("Expected parameters with uri or path");
        }

        String candidate = firstNonBlank(params.getPath(), params.getUri());
        String normalized = normalizePath(candidate);
        if (normalized == null) {
            return invalidParams("Expected uri or path to be provided");
        }

        LOG.info("docx export called with: {}", normalized);
        return handlers.exportDocx(normalized, params.getTitle());
    }

    private ExportDocxParams coerceExportParams(Object rawParams) {
        if (rawParams == null) {
            return null;
        }

        if (rawParams instanceof ExportDocxParams typed) {
            return typed;
        }

        ExportDocxParams params = new ExportDocxParams();

        if (rawParams instanceof java.util.Map<?, ?> map) {
            params.setUri(coerceArgToString(map.get("uri")));
            params.setPath(coerceArgToString(map.get("path")));
            params.setTitle(coerceArgToString(map.get("title")));
            if (params.getUri() != null || params.getPath() != null || params.getTitle() != null) {
                return params;
            }
        }

        String fallback = coerceArgToString(rawParams);
        if (fallback != null && !fallback.isBlank()) {
            params.setPath(fallback);
            return params;
        }

        return null;
    }

    private String extractPath(List<Object> args) {
        String fileUriOrPath = coerceArgToString(args != null && !args.isEmpty() ? args.get(0) : null);
        if (fileUriOrPath == null || fileUriOrPath.isBlank()) {
            return null;
        }

        return normalizePath(fileUriOrPath);
    }

    private static final int MAX_COERCE_DEPTH = 8;

    private static String coerceArgToString(Object arg) {
        return coerceArgToString(arg, 0);
    }

    @SuppressWarnings("unchecked")
    private static String coerceArgToString(Object arg, int depth) {
        if (arg == null) return null;
        if (arg instanceof String s) return s;
        if (arg instanceof JsonPrimitive jp && jp.isString()) return jp.getAsString();
        if (depth >= MAX_COERCE_DEPTH) {
            return String.valueOf(arg);
        }
        if (arg instanceof List<?> list) {
            for (Object element : list) {
                String candidate = coerceArgToString(element, depth + 1);
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return null;
        }
        if (arg instanceof Map<?, ?> map) {
            String direct = coerceFromMapEntries((Map<Object, Object>) map, depth,
                    "uri", "fsPath", "path", "href");
            if (direct != null) {
                return direct;
            }

            String nested = coerceFromMapEntries((Map<Object, Object>) map, depth,
                    "textDocument", "documentUri", "source");
            if (nested != null) {
                return nested;
            }

            Object scheme = map.get("scheme");
            Object authority = map.get("authority");
            Object path = map.get("path");
            if (scheme instanceof String schemeStr) {
                String pathStr = coerceArgToString(path, depth + 1);
                if (pathStr != null && !pathStr.isBlank()) {
                    String authorityStr = coerceArgToString(authority, depth + 1);
                    try {
                        URI uri = authorityStr != null && !authorityStr.isBlank()
                                ? new URI(schemeStr, authorityStr, pathStr, null, null)
                                : new URI(schemeStr, null, pathStr, null);
                        return uri.toString();
                    } catch (Exception ignore) {
                        // Fall through to final fallback
                    }
                }
            }

            return String.valueOf(arg);
        }
        // Fallback: last resort
        return String.valueOf(arg);
    }

    private static String coerceFromMapEntries(Map<Object, Object> map, int depth, String... keys) {
        for (String key : keys) {
            if (!map.containsKey(key)) {
                continue;
            }
            Object value = map.get(key);
            if (value == map) {
                continue;
            }
            String candidate = coerceArgToString(value, depth + 1);
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizePath(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }

        String normalized = pathOrUri;
        if (normalized.startsWith("file:")) {
            try {
                Path p = Paths.get(URI.create(normalized));
                normalized = p.toString();
            } catch (Exception e) {
                LOG.warn("Could not convert URI to path: {}", normalized, e);
            }
        }
        return normalized;
    }

    private CompletableFuture<String> invalidParams(String message) {
        ResponseError err = new ResponseError(ResponseErrorCode.InvalidParams, message, null);
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ResponseErrorException(err));
        return failed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static class ExportDocxParams {
        private String uri;
        private String path;
        private String title;

        public ExportDocxParams() {}

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
