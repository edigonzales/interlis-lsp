package ch.so.agi.lsp.interlis.text;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
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
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

public final class InterlisDefinitionFinder {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisDefinitionFinder.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationCache compilationCache;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;

    public InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents) {
        this(server, documents, null);
    }

    public InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents, CompilationCache cache) {
        this(server, documents, cache, Ili2cUtil::compile);
    }

    public InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents,
                                    CompilationCache cache,
                                    BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler) {
        this.server = server;
        this.documents = documents;
        this.compilationCache = cache;
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
    }

    public Either<List<? extends Location>, List<? extends LocationLink>> findDefinition(TextDocumentPositionParams params) throws Exception {
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
        while (start > 0 && InterlisNameResolver.isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        int end = offset;
        int length = text.length();
        while (end < length && InterlisNameResolver.isIdentifierPart(text.charAt(end))) {
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

    private Location buildElementLocation(TransferDescription td, String token) {
        Element element = InterlisNameResolver.resolveElement(td, token);
        if (element == null) {
            return null;
        }

        Model model = InterlisNameResolver.findEnclosingModel(element);
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
                        relativeIndex = lineText.indexOf(InterlisNameResolver.lastSegment(token));
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
                String segment = InterlisNameResolver.lastSegment(token);
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
            if (CancellationUtil.isCancellation(ex)) {
                throw CancellationUtil.propagateCancellation(ex);
            }
            LOG.warn("Failed to build element definition location for {}", token, ex);
            return null;
        }
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
            if (CancellationUtil.isCancellation(ex)) {
                throw CancellationUtil.propagateCancellation(ex);
            }
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
