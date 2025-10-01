package ch.so.agi.lsp.interlis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads the Mermaid HTML template and injects the generated diagram text.
 */
public final class MermaidHtmlRenderer {
    private static final String TEMPLATE_PATH = "/mermaid_template.html";
    private static final String PLACEHOLDER = "${mermaidString}";
    private static final String TEMPLATE = loadTemplate();

    private MermaidHtmlRenderer() {
    }

    public static String render(String mermaidSource) {
        Objects.requireNonNull(mermaidSource, "mermaidSource must not be null");
        String escaped = escapeHtml(mermaidSource);
        return TEMPLATE.replace(PLACEHOLDER, escaped);
    }

    private static String loadTemplate() {
        try (InputStream in = MermaidHtmlRenderer.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Could not load Mermaid template from " + TEMPLATE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Mermaid template", e);
        }
    }

    private static String escapeHtml(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
