package ch.so.agi.lsp.interlis.server;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;

public class ClientSettings {
    /** Comma-separated string as received from the client (raw). */
    private String modelRepositories = "";

    /** Whether repository discovery should silence ili2c stdout logging. */
    private boolean suppressRepositoryLogs = true;

    /** Requested ELK edge routing (for example ORTHOGONAL/POLYLINE/SPLINES). */
    private String edgeRouting = "";

    /** Whether association cardinality labels should be rendered. */
    private boolean showCardinalities = true;

    /** Parsed, trimmed list (derived from modelRepositories). */
    public List<String> getModelRepositoriesList() {
        if (modelRepositories == null || modelRepositories.isBlank()) return List.of();
        return Arrays.stream(modelRepositories.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    public String getModelRepositories() {
        return modelRepositories;
    }

    public void setModelRepositories(String v) {
        this.modelRepositories = v != null ? v : "";
    }

    public boolean isSuppressRepositoryLogs() {
        return suppressRepositoryLogs;
    }

    public void setSuppressRepositoryLogs(boolean suppressRepositoryLogs) {
        this.suppressRepositoryLogs = suppressRepositoryLogs;
    }

    public String getEdgeRouting() {
        return edgeRouting;
    }

    public void setEdgeRouting(String edgeRouting) {
        this.edgeRouting = edgeRouting != null ? edgeRouting.trim() : "";
    }

    public boolean isShowCardinalities() {
        return showCardinalities;
    }

    public void setShowCardinalities(boolean showCardinalities) {
        this.showCardinalities = showCardinalities;
    }

    @Override public String toString() {
        return "ClientSettings{modelRepositories='" + modelRepositories
                + "', suppressRepositoryLogs=" + suppressRepositoryLogs
                + ", edgeRouting='" + edgeRouting + '\''
                + ", showCardinalities=" + showCardinalities
                + '}';
    }

    /** Build from initOptions or didChangeConfiguration payloads. */
    public static ClientSettings from(Object any) {
        ClientSettings s = new ClientSettings();
        if (any == null) return s;

        // ---- Map payloads (common with vscode-languageclient) ----
        if (any instanceof Map<?, ?> top) {
            applyMapPayload(s, top);
            return s;
        }

        // ---- JSON payloads (Gson) ----
        try {
            JsonElement je = (any instanceof JsonElement) ? (JsonElement) any : JsonParser.parseString(any.toString());
            if (je.isJsonObject()) {
                applyJsonPayload(s, je.getAsJsonObject());
            }
        } catch (Exception ignore) { /* leave defaults */ }

        return s;
    }

    private static void applyMapPayload(ClientSettings target, Map<?, ?> top) {
        Map<?, ?> section = nestedSection(top);

        String modelRepositories = asString(firstNonNull(
                readMapPath(section, "modelRepositories"),
                top.get("interlisLsp.modelRepositories")));
        if (modelRepositories != null) {
            target.setModelRepositories(modelRepositories);
        }

        Boolean suppressRepositoryLogs = asBoolean(firstNonNull(
                readMapPath(section, "suppressRepositoryLogs"),
                top.get("interlisLsp.suppressRepositoryLogs")));
        if (suppressRepositoryLogs != null) {
            target.setSuppressRepositoryLogs(suppressRepositoryLogs);
        }

        String edgeRouting = asString(firstNonNull(
                readMapPath(section, "diagram", "layout", "edgeRouting"),
                readMapPath(section, "diagram.layout.edgeRouting"),
                top.get("interlisLsp.diagram.layout.edgeRouting")));
        if (edgeRouting != null) {
            target.setEdgeRouting(edgeRouting);
        }

        Boolean showCardinalities = asBoolean(firstNonNull(
                readMapPath(section, "diagram", "showCardinalities"),
                readMapPath(section, "diagram.showCardinalities"),
                top.get("interlisLsp.diagram.showCardinalities")));
        if (showCardinalities != null) {
            target.setShowCardinalities(showCardinalities);
        }
    }

    private static void applyJsonPayload(ClientSettings target, JsonObject top) {
        JsonObject section = nestedSection(top);

        String modelRepositories = asString(firstNonNull(
                readJsonPath(section, "modelRepositories"),
                top.get("interlisLsp.modelRepositories")));
        if (modelRepositories != null) {
            target.setModelRepositories(modelRepositories);
        }

        Boolean suppressRepositoryLogs = asBoolean(firstNonNull(
                readJsonPath(section, "suppressRepositoryLogs"),
                top.get("interlisLsp.suppressRepositoryLogs")));
        if (suppressRepositoryLogs != null) {
            target.setSuppressRepositoryLogs(suppressRepositoryLogs);
        }

        String edgeRouting = asString(firstNonNull(
                readJsonPath(section, "diagram", "layout", "edgeRouting"),
                readJsonPath(section, "diagram.layout.edgeRouting"),
                top.get("interlisLsp.diagram.layout.edgeRouting")));
        if (edgeRouting != null) {
            target.setEdgeRouting(edgeRouting);
        }

        Boolean showCardinalities = asBoolean(firstNonNull(
                readJsonPath(section, "diagram", "showCardinalities"),
                readJsonPath(section, "diagram.showCardinalities"),
                top.get("interlisLsp.diagram.showCardinalities")));
        if (showCardinalities != null) {
            target.setShowCardinalities(showCardinalities);
        }
    }

    private static Map<?, ?> nestedSection(Map<?, ?> top) {
        Object section = top.get("interlisLsp");
        return section instanceof Map<?, ?> map ? map : top;
    }

    private static JsonObject nestedSection(JsonObject top) {
        JsonElement section = top.get("interlisLsp");
        return section != null && section.isJsonObject() ? section.getAsJsonObject() : top;
    }

    private static Object readMapPath(Map<?, ?> map, String... path) {
        Object current = map;
        for (String segment : path) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(segment);
        }
        return current;
    }

    private static JsonElement readJsonPath(JsonObject obj, String... path) {
        JsonElement current = obj;
        for (String segment : path) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(segment);
        }
        return current;
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && !(value instanceof JsonNull)) {
                return value;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsString();
        }
        if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return Boolean.toString(primitive.getAsBoolean());
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsInt() != 0;
        }

        String asString = asString(value);
        if (asString == null) {
            return null;
        }
        String normalized = asString.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("1".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized)) {
            return false;
        }
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return null;
    }
}
