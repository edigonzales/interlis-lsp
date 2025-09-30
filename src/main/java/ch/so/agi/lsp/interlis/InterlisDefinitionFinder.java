package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class InterlisDefinitionFinder {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisDefinitionFinder.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;

    InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents) {
        this.server = server;
        this.documents = documents;
    }

    Either<List<? extends Location>, List<? extends LocationLink>> findDefinition(TextDocumentPositionParams params) throws Exception {
        if (params == null || params.getTextDocument() == null) {
            return Either.forLeft(Collections.emptyList());
        }

        String uri = params.getTextDocument().getUri();
        if (uri == null || uri.isEmpty()) {
            return Either.forLeft(Collections.emptyList());
        }

        String text = documents != null ? documents.getText(uri) : null;
        if (text == null || text.isEmpty()) {
            text = InterlisTextDocumentService.readDocument(uri);
        }
        if (text == null || text.isEmpty()) {
            return Either.forLeft(Collections.emptyList());
        }

        Position position = params.getPosition();
        int offset = DocumentTracker.toOffset(text, position);

        int start = offset;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        int end = offset;
        int length = text.length();
        while (end < length && isIdentifierPart(text.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return Either.forLeft(Collections.emptyList());
        }

        String token = text.substring(start, end);
        if (token.isEmpty()) {
            return Either.forLeft(Collections.emptyList());
        }

        String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(cfg, pathOrUri);
        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return Either.forLeft(Collections.emptyList());
        }

        String targetPath = resolveModelPath(td, token);
        if (targetPath == null || targetPath.isBlank()) {
            return Either.forLeft(Collections.emptyList());
        }

        Location location = buildLocation(targetPath, token);
        if (location == null) {
            return Either.forLeft(Collections.emptyList());
        }

        List<Location> locations = new ArrayList<>();
        locations.add(location);
        return Either.forLeft(locations);
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static String resolveModelPath(TransferDescription td, String name) {
        if (td == null || name == null || name.isBlank()) {
            return null;
        }

        for (Model model : td.getModelsFromLastFile()) {
            if (model == null) {
                continue;
            }

            if (Objects.equals(name, model.getName())) {
                return model.getFileName();
            }

            Model[] imports = model.getImporting();
            if (imports != null) {
                for (Model imp : imports) {
                    if (imp != null && Objects.equals(name, imp.getName())) {
                        return imp.getFileName();
                    }
                }
            }
        }

        return null;
    }

    private Location buildLocation(String pathOrUri, String token) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }

        try {
            String normalizedPath = InterlisTextDocumentService.toFilesystemPathIfPossible(pathOrUri);
            Path path = Paths.get(normalizedPath);
            String targetText = Files.exists(path) ? Files.readString(path) : "";

            Position start = new Position(0, 0);
            Position end = start;

            if (!token.isBlank() && !targetText.isEmpty()) {
                int idx = targetText.indexOf(token);
                if (idx >= 0) {
                    start = DocumentTracker.positionAt(targetText, idx);
                    end = DocumentTracker.positionAt(targetText, idx + token.length());
                }
            }

            Range range = new Range(start, end);
            String targetUri = path.toUri().toString();
            return new Location(targetUri, range);
        } catch (Exception ex) {
            LOG.warn("Failed to build definition location for {}", pathOrUri, ex);
            return null;
        }
    }
}
