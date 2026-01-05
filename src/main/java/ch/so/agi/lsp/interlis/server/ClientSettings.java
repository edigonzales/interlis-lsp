package ch.so.agi.lsp.interlis.server;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSettings {
    private static final Logger LOG = LoggerFactory.getLogger(ClientSettings.class);

    /** Comma-separated string as received from the client (raw). */
    private String modelRepositories = "";

    /** Whether repository discovery should silence ili2c stdout logging. */
    private boolean suppressRepositoryLogs = true;

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

    @Override public String toString() {
        return "ClientSettings{modelRepositories='" + modelRepositories + "', suppressRepositoryLogs=" + suppressRepositoryLogs + "}";
    }

    /** Build from initOptions or didChangeConfiguration payloads. */
    public static ClientSettings from(Object any) {
        ClientSettings s = new ClientSettings();
        if (any == null) return s;

        // ---- Map payloads (common with vscode-languageclient) ----
        if (any instanceof Map<?, ?> top) {
            // VS Code often nests under the section name:
            Object section = top.containsKey("interlisLsp") ? top.get("interlisLsp") : top;
            if (section instanceof Map<?, ?> sec) {
                Object mr = sec.get("modelRepositories");
                if (mr instanceof String str) {
                    s.setModelRepositories(str);
                } else if (mr != null) {
                    s.setModelRepositories(String.valueOf(mr));
                }

                Object suppress = sec.get("suppressRepositoryLogs");
                if (suppress instanceof Boolean bool) {
                    s.setSuppressRepositoryLogs(bool);
                } else if (suppress instanceof String str) {
                    s.setSuppressRepositoryLogs(Boolean.parseBoolean(str));
                }
            }
            return s;
        }

        // ---- JSON payloads (Gson) ----
        try {
            JsonElement je = (any instanceof JsonElement) ? (JsonElement) any : JsonParser.parseString(any.toString());
            if (je.isJsonObject()) {
                JsonObject obj = je.getAsJsonObject();
                // If nested: obj = { interlisLsp: { ... } }
                JsonObject sec = obj.has("interlisLsp") && obj.get("interlisLsp").isJsonObject()
                        ? obj.getAsJsonObject("interlisLsp")
                        : obj;

                if (sec.has("modelRepositories") && sec.get("modelRepositories").isJsonPrimitive()) {
                    s.setModelRepositories(sec.getAsJsonPrimitive("modelRepositories").getAsString());
                }
                if (sec.has("suppressRepositoryLogs")) {
                    JsonElement el = sec.get("suppressRepositoryLogs");
                    if (el.isJsonPrimitive()) {
                        JsonPrimitive prim = el.getAsJsonPrimitive();
                        if (prim.isBoolean()) {
                            s.setSuppressRepositoryLogs(prim.getAsBoolean());
                        } else if (prim.isString()) {
                            s.setSuppressRepositoryLogs(Boolean.parseBoolean(prim.getAsString()));
                        }
                    }
                }
            }
        } catch (Exception ignore) { /* leave defaults */ }

        return s;
    }
}
