package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ch.so.agi.glsp.interlis.InterlisGlspServer;

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
                || InterlisLanguageServer.CMD_GENERATE_UML.equals(params.getCommand())
                || InterlisLanguageServer.CMD_GENERATE_PLANTUML.equals(params.getCommand())) {
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

            if (InterlisLanguageServer.CMD_GENERATE_UML.equals(params.getCommand())) {
                LOG.info("uml generation called with: {}", pathOrUri);
                return handlers.generateUml(pathOrUri);
            }

            LOG.info("plantuml generation called with: {}", pathOrUri);
            return handlers.generatePlantUml(pathOrUri);
        }
        return CompletableFuture.completedFuture(null);
    }

    @JsonRequest(InterlisLanguageServer.REQ_EXPORT_DOCX)
    public CompletableFuture<String> exportDocx(Object rawParams) {
        DocumentExportParams params = coerceExportParams(rawParams);
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

    @JsonRequest(InterlisLanguageServer.REQ_EXPORT_HTML)
    public CompletableFuture<String> exportHtml(Object rawParams) {
        DocumentExportParams params = coerceExportParams(rawParams);
        if (params == null) {
            return invalidParams("Expected parameters with uri or path");
        }

        String candidate = firstNonBlank(params.getPath(), params.getUri());
        String normalized = normalizePath(candidate);
        if (normalized == null) {
            return invalidParams("Expected uri or path to be provided");
        }

        LOG.info("html export called with: {}", normalized);
        return handlers.exportHtml(normalized, params.getTitle());
    }

    @JsonRequest(InterlisLanguageServer.REQ_GLSP_INFO)
    public CompletableFuture<GlspInfo> glspInfo() {
        InterlisGlspServer glsp = server.getGlspServer();
        if (glsp == null) {
            return CompletableFuture.completedFuture(new GlspInfo("", 0, "", false));
        }
        return CompletableFuture.completedFuture(
                new GlspInfo(glsp.getHost(), glsp.getPort(), glsp.getEndpointPath(), glsp.isStarted()));
    }

    private DocumentExportParams coerceExportParams(Object rawParams) {
        if (rawParams == null) {
            return null;
        }

        Object normalized = decodePotentialJson(rawParams);

        if (normalized instanceof DocumentExportParams typed) {
            return typed;
        }

        DocumentExportParams params = new DocumentExportParams();

        if (normalized instanceof java.util.Map<?, ?> map) {
            params.setUri(coerceArgToString(map.get("uri")));
            params.setPath(coerceArgToString(map.get("path")));
            params.setTitle(coerceArgToString(map.get("title")));
            if (params.getUri() != null || params.getPath() != null || params.getTitle() != null) {
                return params;
            }
        }

        if (normalized instanceof JsonObject jsonObject) {
            params.setUri(coerceArgToString(jsonObject.get("uri")));
            params.setPath(coerceArgToString(jsonObject.get("path")));
            params.setTitle(coerceArgToString(jsonObject.get("title")));
            if (params.getUri() != null || params.getPath() != null || params.getTitle() != null) {
                return params;
            }
        }

        String fallback = coerceArgToString(normalized);
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
        if (arg instanceof String s) {
            Object decoded = decodePotentialJson(s);
            if (decoded != s) {
                return coerceArgToString(decoded, depth + 1);
            }
            return s;
        }
        if (arg instanceof JsonPrimitive jp && jp.isString()) return jp.getAsString();
        if (arg instanceof JsonElement jsonElement) {
            return coerceJsonElement(jsonElement, depth);
        }
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

    private static Object decodePotentialJson(Object raw) {
        if (!(raw instanceof String str)) {
            return raw;
        }

        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            return str;
        }

        char first = trimmed.charAt(0);
        if (first != '{' && first != '[' && first != '"') {
            return str;
        }

        try {
            JsonElement element = JsonParser.parseString(trimmed);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            }
            return element;
        } catch (JsonSyntaxException ex) {
            return str;
        }
    }

    private static String coerceJsonElement(JsonElement element, int depth) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            }
            if (primitive.isBoolean()) {
                return String.valueOf(primitive.getAsBoolean());
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber().toString();
            }
        }

        if (depth >= MAX_COERCE_DEPTH) {
            return element.toString();
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                String candidate = coerceArgToString(child, depth + 1);
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return null;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String direct = coerceFromJsonObject(obj, depth, "uri", "fsPath", "path", "href");
            if (direct != null) {
                return direct;
            }

            String nested = coerceFromJsonObject(obj, depth, "textDocument", "documentUri", "source");
            if (nested != null) {
                return nested;
            }

            JsonElement scheme = obj.get("scheme");
            JsonElement authority = obj.get("authority");
            JsonElement path = obj.get("path");
            String schemeStr = coerceArgToString(scheme, depth + 1);
            if (schemeStr != null && !schemeStr.isBlank()) {
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

            return element.toString();
        }

        return element.toString();
    }

    private static String coerceFromJsonObject(JsonObject obj, int depth, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) {
                continue;
            }
            JsonElement value = obj.get(key);
            if (value == obj) {
                continue;
            }
            String candidate = coerceArgToString(value, depth + 1);
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
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

    public static final class GlspInfo {
        private final String host;
        private final int port;
        private final String path;
        private final boolean running;

        public GlspInfo(String host, int port, String path, boolean running) {
            this.host = host != null ? host : "";
            this.port = port;
            this.path = path != null ? path : "";
            this.running = running;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getPath() {
            return path;
        }

        public boolean isRunning() {
            return running;
        }
    }

    public static class DocumentExportParams {
        private String uri;
        private String path;
        private String title;

        public DocumentExportParams() {}

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
