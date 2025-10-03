package ch.so.agi.lsp.interlis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/**
 * Loads the PlantUML HTML template and injects the generated diagram text.
 */
public final class PlantUmlHtmlRenderer {
    private static final String TEMPLATE_PATH = "/plantuml_template.html";
    private static final String SOURCE_PLACEHOLDER = "${plantUmlSource}";
    private static final String PNG_URL_PLACEHOLDER = "${plantUmlPngUrl}";
    private static final String SVG_URL_PLACEHOLDER = "${plantUmlSvgUrl}";
    private static final String PDF_URL_PLACEHOLDER = "${plantUmlPdfUrl}";
    private static final String TEMPLATE = loadTemplate();

    private PlantUmlHtmlRenderer() {
    }

    public static String render(String plantUmlSource) {
        Objects.requireNonNull(plantUmlSource, "plantUmlSource must not be null");
        String escapedSource = escapeHtml(plantUmlSource);
        DiagramAssets assets = renderAssets(plantUmlSource);
        return TEMPLATE
                .replace(SOURCE_PLACEHOLDER, escapedSource)
                .replace(PNG_URL_PLACEHOLDER, assets.pngDataUri())
                .replace(SVG_URL_PLACEHOLDER, assets.svgDataUri())
                .replace(PDF_URL_PLACEHOLDER, assets.pdfDataUri());
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

    private static DiagramAssets renderAssets(String source) {
        byte[] png = renderDiagram(source, FileFormat.PNG);
        byte[] svg = renderDiagram(source, FileFormat.SVG);
        byte[] pdf = renderDiagram(source, FileFormat.PDF);
        return new DiagramAssets(
                toDataUri(png, "image/png"),
                toDataUri(svg, "image/svg+xml"),
                toDataUri(pdf, "application/pdf"));
    }

    private static byte[] renderDiagram(String source, FileFormat format) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SourceStringReader reader = new SourceStringReader(source);
            Object description = reader.generateImage(baos, new FileFormatOption(format));
            if (description == null) {
                throw new IllegalStateException("PlantUML rendering produced no diagram for format " + format);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PlantUML diagram", e);
        }
    }

    private static String toDataUri(byte[] data, String mimeType) {
        String base64 = Base64.getEncoder().encodeToString(data);
        return "data:" + mimeType + ";base64," + base64;
    }

    private record DiagramAssets(String pngDataUri, String svgDataUri, String pdfDataUri) {
    }
}
