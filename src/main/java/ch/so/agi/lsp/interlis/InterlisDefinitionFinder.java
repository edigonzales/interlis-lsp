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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class InterlisDefinitionFinder {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisDefinitionFinder.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationProvider compiler;
    private final ConcurrentMap<String, CachedCompilation> compilationCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> rootDependencies = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> dependencyToRoots = new ConcurrentHashMap<>();

    interface CompilationProvider {
        Ili2cUtil.CompilationOutcome compile(ClientSettings settings, String fileUriOrPath);
    }

    private static final class CachedCompilation {
        final Integer version;
        final Ili2cUtil.CompilationOutcome outcome;
        final String sourceRootKey;
        final boolean dependencyProjection;

        CachedCompilation(Integer version, Ili2cUtil.CompilationOutcome outcome,
                          String sourceRootKey, boolean dependencyProjection) {
            this.version = version;
            this.outcome = outcome;
            this.sourceRootKey = sourceRootKey;
            this.dependencyProjection = dependencyProjection;
        }
    }

    InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents) {
        this(server, documents, Ili2cUtil::compile);
    }

    InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents, CompilationProvider compiler) {
        this.server = server;
        this.documents = documents;
        this.compiler = compiler;
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

        TokenContext token = extractToken(text, DocumentTracker.toOffset(text, params.getPosition()));
        if (token == null || token.segments.isEmpty()) {
            return Either.forLeft(Collections.emptyList());
        }

        String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        Integer version = documents != null ? documents.getVersion(uri) : null;
        Ili2cUtil.CompilationOutcome outcome = getOrCompile(pathOrUri, version);
        TransferDescription td = outcome.getTransferDescription();
        if (td == null) {
            return Either.forLeft(Collections.emptyList());
        }

        Element element = resolveElement(td, token);
        if (element != null) {
            Location location = buildLocation(element);
            if (location != null) {
                return Either.forLeft(Collections.singletonList(location));
            }
        }

        String targetPath = resolveModelPath(td, token.primarySegment());
        if (targetPath == null || targetPath.isBlank()) {
            return Either.forLeft(Collections.emptyList());
        }

        Location location = buildLocation(targetPath, token.primarySegment());
        if (location == null) {
            return Either.forLeft(Collections.emptyList());
        }

        List<Location> locations = new ArrayList<>();
        locations.add(location);
        return Either.forLeft(locations);
    }

    void cacheCompilation(String uri, List<String> dependencyUris, Ili2cUtil.CompilationOutcome outcome) {
        if (uri == null || outcome == null) {
            return;
        }
        String rootKey = toCacheKey(uri);
        if (rootKey == null || rootKey.isBlank()) {
            return;
        }

        CachedCompilation rootCached = new CachedCompilation(
                documents != null ? documents.getVersion(uri) : null,
                outcome,
                rootKey,
                false);

        clearRootDependencies(rootKey);

        compilationCache.put(rootKey, rootCached);

        Set<String> dependencyKeys = new LinkedHashSet<>();
        if (dependencyUris != null) {
            for (String dep : dependencyUris) {
                String depKey = toCacheKey(dep);
                if (depKey == null || depKey.isBlank() || Objects.equals(depKey, rootKey)) {
                    continue;
                }
                dependencyKeys.add(depKey);

                dependencyToRoots.compute(depKey, (key, roots) -> {
                    Set<String> updated = roots != null ? roots : ConcurrentHashMap.newKeySet();
                    updated.add(rootKey);
                    return updated;
                });

                if (hasUnsavedChanges(dep)) {
                    removeProjectionOwnedBy(rootKey, depKey);
                    continue;
                }

                CachedCompilation existing = compilationCache.get(depKey);
                if (existing != null && !existing.dependencyProjection) {
                    continue;
                }

                CachedCompilation projected = new CachedCompilation(null, outcome, rootKey, true);
                compilationCache.put(depKey, projected);
            }
        }

        if (dependencyKeys.isEmpty()) {
            rootDependencies.remove(rootKey);
            return;
        }

        rootDependencies.put(rootKey, dependencyKeys);
    }

    private boolean hasUnsavedChanges(String dependencyUri) {
        if (documents == null || dependencyUri == null) {
            return false;
        }

        String liveText = documents.getText(dependencyUri);
        if (liveText == null) {
            return false;
        }

        try {
            String diskText = InterlisTextDocumentService.readDocument(dependencyUri);
            return !Objects.equals(normalizeLineEndings(liveText), normalizeLineEndings(diskText));
        } catch (Exception ex) {
            LOG.debug("Failed to read {} while checking for unsaved edits", dependencyUri, ex);
            return true;
        }
    }

    private static String normalizeLineEndings(String text) {
        if (text == null) {
            return null;
        }
        if (!text.contains("\r")) {
            return text;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    void invalidateDocument(String uri) {
        if (uri == null) {
            return;
        }
        String key = toCacheKey(uri);
        if (key == null || key.isBlank()) {
            return;
        }

        compilationCache.remove(key);
        clearRootDependencies(key);

        Set<String> roots = dependencyToRoots.remove(key);
        if (roots == null || roots.isEmpty()) {
            return;
        }

        for (String rootKey : new LinkedHashSet<>(roots)) {
            compilationCache.remove(rootKey);
            clearRootDependencies(rootKey);
        }
    }

    void evictCompilation(String uri) {
        if (uri == null) {
            return;
        }
        String rootKey = toCacheKey(uri);
        if (rootKey == null || rootKey.isBlank()) {
            return;
        }

        compilationCache.remove(rootKey);
        clearRootDependencies(rootKey);
    }

    private Ili2cUtil.CompilationOutcome getOrCompile(String pathOrUri, Integer version) {
        String key = toCacheKey(pathOrUri);
        if (key == null || key.isBlank()) {
            return new Ili2cUtil.CompilationOutcome(null, "", Collections.emptyList());
        }

        CachedCompilation cached = compilationCache.get(key);
        if (cached != null && versionMatches(cached.version, version) && cached.outcome != null) {
            return cached.outcome;
        }

        ClientSettings cfg = server.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = compiler.compile(cfg, pathOrUri);
        compilationCache.put(key, new CachedCompilation(version, outcome, key, false));
        return outcome;
    }

    private void clearRootDependencies(String rootKey) {
        Set<String> dependencies = rootDependencies.remove(rootKey);
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        for (String dependencyKey : dependencies) {
            dependencyToRoots.compute(dependencyKey, (key, roots) -> {
                if (roots == null) {
                    removeProjectionOwnedBy(rootKey, dependencyKey);
                    return null;
                }

                roots.remove(rootKey);
                if (roots.isEmpty()) {
                    removeProjectionOwnedBy(rootKey, dependencyKey);
                    return null;
                }

                return roots;
            });
        }
    }

    private void removeProjectionOwnedBy(String rootKey, String dependencyKey) {
        CachedCompilation cached = compilationCache.get(dependencyKey);
        if (cached != null && cached.dependencyProjection && Objects.equals(cached.sourceRootKey, rootKey)) {
            compilationCache.remove(dependencyKey, cached);
        }
    }

    private static String toCacheKey(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.isBlank()) {
            return null;
        }

        String path = InterlisTextDocumentService.toFilesystemPathIfPossible(uriOrPath);
        try {
            Path p = Paths.get(path).toAbsolutePath().normalize();
            return p.toString();
        } catch (Exception ex) {
            try {
                return Paths.get(URI.create(uriOrPath)).toAbsolutePath().normalize().toString();
            } catch (Exception ignored) {
                return uriOrPath;
            }
        }
    }

    private static boolean versionMatches(Integer cachedVersion, Integer requestedVersion) {
        if (cachedVersion == null || requestedVersion == null) {
            return true;
        }
        return Objects.equals(cachedVersion, requestedVersion);
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static boolean isQualifiedIdentifierChar(char ch) {
        return isIdentifierPart(ch) || ch == '.';
    }

    private static TokenContext extractToken(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        int start = clamp(offset, 0, text.length());
        while (start > 0 && isQualifiedIdentifierChar(text.charAt(start - 1))) {
            start--;
        }

        int end = clamp(offset, 0, text.length());
        while (end < text.length() && isQualifiedIdentifierChar(text.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }

        String raw = text.substring(start, end);
        if (raw.isEmpty()) {
            return null;
        }

        List<String> segments = new ArrayList<>();
        int segmentStart = 0;
        for (int i = 0; i <= raw.length(); i++) {
            if (i == raw.length() || raw.charAt(i) == '.') {
                if (i > segmentStart) {
                    segments.add(raw.substring(segmentStart, i));
                }
                segmentStart = i + 1;
            }
        }

        if (segments.isEmpty()) {
            return null;
        }

        int cursorInToken = clamp(offset - start, 0, raw.length());
        if (cursorInToken == raw.length() && cursorInToken > 0) {
            cursorInToken--;
        }
        if (cursorInToken >= 0 && cursorInToken < raw.length() && raw.charAt(cursorInToken) == '.' && cursorInToken > 0) {
            cursorInToken--;
        }

        int activeSegment = 0;
        int accumulatedLength = 0;
        for (int i = 0; i < segments.size(); i++) {
            int segmentLength = segments.get(i).length();
            if (cursorInToken < accumulatedLength + segmentLength) {
                activeSegment = i;
                break;
            }
            accumulatedLength += segmentLength + 1; // include '.'
            activeSegment = i;
        }

        return new TokenContext(raw, segments, activeSegment);
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

    private Element resolveElement(TransferDescription td, TokenContext token) {
        if (td == null || token == null) {
            return null;
        }

        for (int end = token.activeSegment; end >= 0; end--) {
            String candidate = joinSegments(token.segments, end);
            if (candidate.isBlank()) {
                continue;
            }
            Element element = td.getElement(candidate);
            if (element != null) {
                return element;
            }
        }

        return null;
    }

    private static String joinSegments(List<String> segments, int endInclusive) {
        if (segments == null || segments.isEmpty() || endInclusive < 0) {
            return "";
        }
        int limit = Math.min(endInclusive + 1, segments.size());
        return String.join(".", segments.subList(0, limit));
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

    private Location buildLocation(Element element) {
        if (element == null) {
            return null;
        }

        String fileName;
        if (element instanceof Model) {
            fileName = ((Model) element).getFileName();
        } else {
            Model container = (Model) element.getContainer(Model.class);
            fileName = container != null ? container.getFileName() : null;
        }

        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        try {
            String normalizedPath = InterlisTextDocumentService.toFilesystemPathIfPossible(fileName);
            Path path = Paths.get(normalizedPath).toAbsolutePath();
            String targetText = Files.exists(path)
                    ? Files.readString(path, StandardCharsets.UTF_8)
                    : "";

            Position start = new Position(0, 0);
            Position end = start;

            if (!targetText.isEmpty()) {
                int lineNumber = Math.max(element.getSourceLine() - 1, 0);
                int lineStart = DocumentTracker.lineStartOffset(targetText, lineNumber);
                int lineEnd = DocumentTracker.lineStartOffset(targetText, lineNumber + 1);

                String name = element.getName();
                int idx = -1;
                if (name != null && !name.isBlank()) {
                    if (lineEnd > lineStart) {
                        int candidate = targetText.indexOf(name, lineStart);
                        if (candidate >= lineStart && candidate < lineEnd) {
                            idx = candidate;
                        }
                    }
                    if (idx < 0) {
                        idx = targetText.indexOf(name);
                    }
                }

                int startOffset = idx >= 0 ? idx : lineStart;
                int endOffset = idx >= 0 ? idx + (name != null ? name.length() : 0) : Math.max(lineEnd, lineStart);

                start = DocumentTracker.positionAt(targetText, startOffset);
                end = DocumentTracker.positionAt(targetText, Math.max(endOffset, startOffset));
            }

            Range range = new Range(start, end);
            return new Location(path.toUri().toString(), range);
        } catch (Exception ex) {
            LOG.warn("Failed to build element definition location for {}", element, ex);
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class TokenContext {
        final String rawToken;
        final List<String> segments;
        final int activeSegment;

        TokenContext(String rawToken, List<String> segments, int activeSegment) {
            this.rawToken = rawToken;
            this.segments = segments;
            this.activeSegment = activeSegment;
        }

        String primarySegment() {
            return segments.isEmpty() ? rawToken : segments.get(0);
        }
    }
}
