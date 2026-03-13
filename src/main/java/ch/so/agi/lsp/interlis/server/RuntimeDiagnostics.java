package ch.so.agi.lsp.interlis.server;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiFunction;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Emits stable runtime diagnostics so the client can distinguish a real compile
 * from other requests and identify the concrete server artifact in use.
 */
public final class RuntimeDiagnostics {
    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_MARKER = "classpath";

    private RuntimeDiagnostics() {}

    public static void logServerBuild(InterlisLanguageServer server) {
        if (server == null) {
            return;
        }
        server.debugLogToClient(describeServerBuild() + System.lineSeparator());
    }

    public static void logDiagramRequest(InterlisLanguageServer server, String pathOrUri) {
        if (server == null) {
            return;
        }
        server.debugLogToClient("DIAGRAM_REQUEST path=" + normalizePath(pathOrUri) + System.lineSeparator());
    }

    public static void logRealCompile(InterlisLanguageServer server, String source, String pathOrUri) {
        if (server == null) {
            return;
        }
        server.debugLogToClient("REAL_COMPILE source=" + safeValue(source)
                + " path=" + normalizePath(pathOrUri)
                + System.lineSeparator());
    }

    public static Ili2cUtil.CompilationOutcome compile(InterlisLanguageServer server,
                                                       BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                                                       ClientSettings settings,
                                                       String pathOrUri,
                                                       String source) {
        logRealCompile(server, source, pathOrUri);
        return compiler.apply(settings, pathOrUri);
    }

    static String describeServerBuild() {
        Attributes attributes = readManifestAttributes();
        String version = firstNonBlank(
                attributes.getValue("Implementation-Version"),
                packageImplementationVersion(),
                UNKNOWN);
        String buildTime = firstNonBlank(attributes.getValue("Build-Time"), UNKNOWN);
        String marker = firstNonBlank(attributes.getValue("Build-Marker"), DEFAULT_MARKER);

        return "Server build: version=" + version
                + " builtAt=" + buildTime
                + " marker=" + marker;
    }

    private static Attributes readManifestAttributes() {
        try (InputStream input = RuntimeDiagnostics.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (input == null) {
                return new Attributes();
            }

            Manifest manifest = new Manifest(input);
            Attributes attributes = manifest.getMainAttributes();
            return attributes != null ? attributes : new Attributes();
        } catch (Exception ex) {
            return new Attributes();
        }
    }

    private static String packageImplementationVersion() {
        Package pkg = RuntimeDiagnostics.class.getPackage();
        return pkg != null ? pkg.getImplementationVersion() : null;
    }

    private static String normalizePath(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return "<unknown>";
        }

        try {
            if (pathOrUri.startsWith("file:")) {
                Path path = Paths.get(URI.create(pathOrUri));
                return path.toString();
            }
        } catch (Exception ignored) {
            // Fall back to the original string.
        }

        return pathOrUri;
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
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
}
