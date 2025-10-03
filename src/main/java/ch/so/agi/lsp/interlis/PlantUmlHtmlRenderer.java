package ch.so.agi.lsp.interlis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.Deflater;

/**
 * Loads the PlantUML HTML template and injects the generated diagram text.
 */
public final class PlantUmlHtmlRenderer {
    private static final String TEMPLATE_PATH = "/plantuml_template.html";
    private static final String SOURCE_PLACEHOLDER = "${plantUmlSource}";
    private static final String URL_PLACEHOLDER = "${plantUmlUrl}";
    private static final String TEMPLATE = loadTemplate();

    private PlantUmlHtmlRenderer() {
    }

    public static String render(String plantUmlSource) {
        Objects.requireNonNull(plantUmlSource, "plantUmlSource must not be null");
        String escapedSource = escapeHtml(plantUmlSource);
        String url = "https://www.plantuml.com/plantuml/png/" + encodeForPlantUmlServer(plantUmlSource);
        return TEMPLATE.replace(SOURCE_PLACEHOLDER, escapedSource).replace(URL_PLACEHOLDER, url);
    }

    private static String loadTemplate() {
        try (InputStream in = PlantUmlHtmlRenderer.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Could not load PlantUML template from " + TEMPLATE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read PlantUML template", e);
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

    private static String encodeForPlantUmlServer(String source) {
        byte[] data = source.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        byte[] compressed = baos.toByteArray();
        return encode64(compressed);
    }

    private static String encode64(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 4 + 2) / 3);
        int len = data.length;
        for (int i = 0; i < len; i += 3) {
            int b1 = data[i] & 0xFF;
            int b2 = (i + 1) < len ? data[i + 1] & 0xFF : 0;
            int b3 = (i + 2) < len ? data[i + 2] & 0xFF : 0;

            sb.append(encode6bit(b1 >> 2));
            sb.append(encode6bit(((b1 & 0x3) << 4) | (b2 >> 4)));
            sb.append(encode6bit(((b2 & 0xF) << 2) | (b3 >> 6)));
            sb.append(encode6bit(b3 & 0x3F));
        }
        return sb.toString();
    }

    private static char encode6bit(int b) {
        int value = b & 0x3F;
        if (value < 10) {
            return (char) ('0' + value);
        }
        value -= 10;
        if (value < 26) {
            return (char) ('A' + value);
        }
        value -= 26;
        if (value < 26) {
            return (char) ('a' + value);
        }
        value -= 26;
        if (value == 0) {
            return '-';
        }
        if (value == 1) {
            return '_';
        }
        return '?';
    }
}
