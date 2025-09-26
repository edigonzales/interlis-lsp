package ch.so.agi.lsp.interlis;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSettings {
    private static final Logger LOG = LoggerFactory.getLogger(ClientSettings.class);

    /** Comma-separated string as received from the client (raw). */
    private String modelRepositories = "";

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

    @Override public String toString() {
        return "ClientSettings{modelRepositories='" + modelRepositories + "'}";
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
            }
        } catch (Exception ignore) { /* leave defaults */ }

        return s;
    }
}
