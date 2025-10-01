package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Element;
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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

final class InterlisDefinitionFinder {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisDefinitionFinder.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationCache compilationCache;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;

    InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents) {
        this(server, documents, null);
    }

    InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents, CompilationCache cache) {
        this(server, documents, cache, Ili2cUtil::compile);
    }

    InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents,
                             CompilationCache cache,
                             BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler) {
        this.server = server;
        this.documents = documents;
        this.compilationCache = cache;
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
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
        Ili2cUtil.CompilationOutcome outcome = getOrCompile(pathOrUri, cfg);
        TransferDescription td = outcome != null ? outcome.getTransferDescription() : null;
        if (td == null) {
            return Either.forLeft(Collections.emptyList());
        }

        Location location = buildElementLocation(td, token);
        if (location == null) {
            String targetPath = resolveModelPath(td, token);
            if (targetPath == null || targetPath.isBlank()) {
                return Either.forLeft(Collections.emptyList());
            }

            location = buildLocation(targetPath, token);
            if (location == null) {
                return Either.forLeft(Collections.emptyList());
            }
        }

        List<Location> locations = new ArrayList<>();
        locations.add(location);
        return Either.forLeft(locations);
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    private Location buildElementLocation(TransferDescription td, String token) {
        Element element = resolveElement(td, token);
        if (element == null) {
            return null;
        }

        Model model = findEnclosingModel(element);
        if (model == null) {
            return null;
        }

        String fileName = model.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalizedPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileName);
        try {
            Path path = Paths.get(normalizedPath);
            String targetText = Files.exists(path) ? Files.readString(path) : "";
            if (targetText.isEmpty()) {
                return null;
            }

            String elementName = element.getName();
            String lookup = (elementName != null && !elementName.isBlank()) ? elementName : token;
            int sourceLine = element.getSourceLine();
            Position start = new Position(Math.max(sourceLine - 1, 0), 0);
            Position end = start;

            int startOffset = -1;
            if (sourceLine > 0) {
                int lineStart = DocumentTracker.lineStartOffset(targetText, sourceLine - 1);
                int lineEnd = DocumentTracker.lineStartOffset(targetText, sourceLine);
                if (lineEnd < lineStart) {
                    lineEnd = targetText.length();
                }
                int relativeIndex = -1;
                if (lookup != null && !lookup.isBlank()) {
                    String lineText = targetText.substring(lineStart, Math.min(lineEnd, targetText.length()));
                    relativeIndex = lineText.indexOf(lookup);
                    if (relativeIndex < 0 && token != null && !token.isBlank()) {
                        relativeIndex = lineText.indexOf(lastSegment(token));
                    }
                }
                if (relativeIndex >= 0) {
                    startOffset = lineStart + relativeIndex;
                }
            }

            if (startOffset < 0 && lookup != null && !lookup.isBlank()) {
                startOffset = targetText.indexOf(lookup);
            }
            if (startOffset < 0 && token != null && !token.isBlank()) {
                String segment = lastSegment(token);
                if (!segment.isBlank()) {
                    startOffset = targetText.indexOf(segment);
                    if (startOffset >= 0) {
                        lookup = segment;
                    }
                }
            }

            if (startOffset < 0) {
                return null;
            }

            if (lookup == null || lookup.isBlank()) {
                lookup = token;
            }

            start = DocumentTracker.positionAt(targetText, startOffset);
            end = DocumentTracker.positionAt(targetText, startOffset + lookup.length());

            Range range = new Range(start, end);
            String targetUri = path.toUri().toString();
            return new Location(targetUri, range);
        } catch (Exception ex) {
            LOG.warn("Failed to build element definition location for {}", token, ex);
            return null;
        }
    }

    private static Element resolveElement(TransferDescription td, String token) {
        if (td == null || token == null || token.isBlank()) {
            return null;
        }

        Element element = td.getElement(token);
        if (element != null) {
            return element;
        }

        String[] segments = token.split("\\.");
        if (segments.length > 1) {
            Element current = td.getElement(segments[0]);
            if (current == null) {
                return null;
            }
            for (int i = 1; i < segments.length && current != null; i++) {
                if (!(current instanceof ch.interlis.ili2c.metamodel.Container<?> container)) {
                    current = null;
                    break;
                }
                current = findChild(container, segments[i]);
            }
            if (current != null) {
                return current;
            }
        }

        String segment = lastSegment(token);
        if (!segment.equals(token)) {
            element = td.getElement(segment);
        }

        return element;
    }

    private static Model findEnclosingModel(Element element) {
        if (element instanceof Model) {
            return (Model) element;
        }

        Element current = element;
        while (current != null) {
            ch.interlis.ili2c.metamodel.Container<?> container = current.getContainer();
            if (container instanceof Model) {
                return (Model) container;
            }
            current = container instanceof Element ? (Element) container : null;
        }
        return null;
    }

    private static Element findChild(ch.interlis.ili2c.metamodel.Container<?> container, String name) {
        if (container == null || name == null || name.isBlank()) {
            return null;
        }

        Iterator<?> iterator = container.iterator();
        while (iterator.hasNext()) {
            Object candidate = iterator.next();
            if (candidate instanceof Element child && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private static String lastSegment(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        int idx = token.lastIndexOf('.');
        return idx >= 0 ? token.substring(idx + 1) : token;
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

    private Ili2cUtil.CompilationOutcome getOrCompile(String pathOrUri, ClientSettings cfg) {
        Ili2cUtil.CompilationOutcome cached = compilationCache != null ? compilationCache.get(pathOrUri) : null;
        if (cached != null && cached.getTransferDescription() != null) {
            return cached;
        }

        Ili2cUtil.CompilationOutcome outcome = compiler.apply(cfg, pathOrUri);
        if (compilationCache != null) {
            compilationCache.put(pathOrUri, outcome);
        }
        return outcome;
    }
}
