package ch.so.agi.lsp.interlis;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;

/**
 * Loads the PlantUML HTML template and injects the generated diagram text.
 */
public final class PlantUmlHtmlRenderer {
    private static final String TEMPLATE_PATH = "/plantuml_template.html";
    private static final String SOURCE_PLACEHOLDER = "${plantUmlSource}";
    private static final String PNG_URL_PLACEHOLDER = "${plantUmlPngUrl}";
    private static final String SVG_URL_PLACEHOLDER = "${plantUmlSvgUrl}";
    private static final String PNG_DATA_ATTR_PLACEHOLDER = "${plantUmlPngDataAttributes}";
    private static final String PNG_SIZE_ATTR_PLACEHOLDER = "${plantUmlPngSizeAttributes}";
    private static final String PLANTUML_SERVER_BASE = "https://uml.planttext.com/plantuml";
    private static final String TEMPLATE = loadTemplate();

    private PlantUmlHtmlRenderer() {
    }

    public static String render(String plantUmlSource) {
        Objects.requireNonNull(plantUmlSource, "plantUmlSource must not be null");
        String escapedSource = escapeHtml(plantUmlSource);
        String encoded = encodeForServer(plantUmlSource);
        String pngUrl = PLANTUML_SERVER_BASE + "/png/" + encoded;
        String svgUrl = PLANTUML_SERVER_BASE + "/svg/" + encoded;
        ImageDimensions dimensions = fetchImageDimensions(pngUrl);
        String dataAttributes = dimensions != null ? dimensions.toDataAttributes() : "";
        String sizeAttributes = dimensions != null ? dimensions.toSizeAttributes() : "";
        return TEMPLATE
                .replace(SOURCE_PLACEHOLDER, escapedSource)
                .replace(PNG_URL_PLACEHOLDER, pngUrl)
                .replace(SVG_URL_PLACEHOLDER, svgUrl)
                .replace(PNG_DATA_ATTR_PLACEHOLDER, dataAttributes)
                .replace(PNG_SIZE_ATTR_PLACEHOLDER, sizeAttributes);
    }

    private static ImageDimensions fetchImageDimensions(String pngUrl) {
        try {
            URLConnection connection = new URL(pngUrl).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                BufferedImage image = ImageIO.read(input);
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width > 0 && height > 0) {
                        return new ImageDimensions(width, height);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore failures and fall back to client-side detection.
        }
        return null;
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

    private static String encodeForServer(String source) {
        byte[] data = source.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = deflate(data);
        return encode64(compressed);
    }

    private static byte[] deflate(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(9, true))) {
            dos.write(data);
            dos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deflate PlantUML source", e);
        }
    }

    private static String encode64(byte[] data) {
        StringBuilder sb = new StringBuilder(((data.length + 2) / 3) * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b1 = data[i] & 0xFF;
            int b2 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
            int b3 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;

            sb.append(encode6bit(b1 >> 2));
            sb.append(encode6bit(((b1 & 0x3) << 4) | (b2 >> 4)));
            sb.append(encode6bit(((b2 & 0xF) << 2) | (b3 >> 6)));
            sb.append(encode6bit(b3 & 0x3F));
        }
        return sb.toString();
    }

    private static char encode6bit(int b) {
        if (b < 10) {
            return (char) ('0' + b);
        }
        b -= 10;
        if (b < 26) {
            return (char) ('A' + b);
        }
        b -= 26;
        if (b < 26) {
            return (char) ('a' + b);
        }
        b -= 26;
        if (b == 0) {
            return '-';
        }
        if (b == 1) {
            return '_';
        }
        return '?';
    }
    private static final class ImageDimensions {
        private final int width;
        private final int height;

        private ImageDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private String toDataAttributes() {
            return "data-natural-width=\"" + width + "\" data-natural-height=\"" + height + "\"";
        }

        private String toSizeAttributes() {
            return "width=\"" + width + "\" height=\"" + height + "\"";
        }
    }
}
