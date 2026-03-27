package ch.so.agi.lsp.interlis.text;

import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public final class InterlisRenameProvider {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisRenameProvider.class);

    private final InterlisSymbolQueryEngine queryEngine;

    public InterlisRenameProvider(InterlisLanguageServer server,
                                  DocumentTracker documents,
                                  CompilationCache cache,
                                  BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler) {
        this(server, documents, cache, compiler, new LiveAnalysisService());
    }

    public InterlisRenameProvider(InterlisLanguageServer server,
                                  DocumentTracker documents,
                                  CompilationCache cache,
                                  BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                                  LiveAnalysisService liveAnalysis) {
        this.queryEngine = new InterlisSymbolQueryEngine(server, documents, cache, compiler, liveAnalysis);
    }

    public Either3<Range, PrepareRenameResult, org.eclipse.lsp4j.PrepareRenameDefaultBehavior> prepareRename(PrepareRenameParams params) {
        InterlisSymbolQueryEngine.ResolvedTarget target = queryEngine.resolveTarget(params);
        if (target == null || target.symbol() == null || target.symbol().nameRange() == null) {
            return null;
        }
        return Either3.forSecond(new PrepareRenameResult(target.symbol().nameRange(), target.symbol().name()));
    }

    public WorkspaceEdit rename(RenameParams params) {
        WorkspaceEdit empty = emptyEdit();
        if (params == null || params.getNewName() == null) {
            return empty;
        }
        String newName = params.getNewName().trim();
        if (!isValidIdentifier(newName)) {
            return empty;
        }

        InterlisSymbolQueryEngine.ResolvedTarget target = queryEngine.resolveTarget(params);
        if (target == null || target.symbol() == null || target.symbol().symbol() == null) {
            return empty;
        }
        if (newName.equals(target.symbol().name())) {
            return empty;
        }

        List<InterlisSymbolQueryEngine.SymbolOccurrence> occurrences = queryEngine.findOccurrences(target, true);
        if (occurrences.isEmpty()) {
            return empty;
        }

        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (InterlisSymbolQueryEngine.SymbolOccurrence occurrence : occurrences) {
            if (occurrence == null || occurrence.range() == null || !isEditableUri(occurrence.uri())) {
                continue;
            }
            String replacement = occurrence.kind() == InterlisSymbolQueryEngine.SymbolOccurrenceKind.REFERENCE
                    ? replaceLastSegment(occurrence.existingText(), newName)
                    : newName;
            changes.computeIfAbsent(occurrence.uri(), key -> new ArrayList<>())
                    .add(new TextEdit(occurrence.range(), replacement));
        }

        if (changes.isEmpty()) {
            return empty;
        }
        WorkspaceEdit edit = new WorkspaceEdit();
        edit.setChanges(changes);
        return edit;
    }

    private static boolean isEditableUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        if (pathOrUri == null || pathOrUri.isBlank() || pathOrUri.startsWith("http")) {
            return false;
        }
        try {
            Path path = Paths.get(pathOrUri);
            return Files.exists(path) && Files.isWritable(path);
        } catch (Exception ex) {
            return false;
        }
    }

    public static LinkedHashSet<String> collectSpellings(String token, String qualifiedName, String oldName) {
        LinkedHashSet<String> spellings = new LinkedHashSet<>();
        if (token != null && !token.isBlank()) {
            spellings.add(token);
        }
        if (oldName != null && !oldName.isBlank()) {
            spellings.add(oldName);
        }
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return spellings;
        }

        String[] segments = Arrays.stream(qualifiedName.split("\\."))
                .filter(segment -> segment != null && !segment.isBlank())
                .toArray(String[]::new);
        for (int i = 0; i < segments.length; i++) {
            String suffix = String.join(".", Arrays.copyOfRange(segments, i, segments.length));
            if (!suffix.isBlank()) {
                spellings.add(suffix);
            }
        }

        if (segments.length > 1) {
            String last = segments[segments.length - 1];
            if (!last.isBlank()) {
                for (int i = 0; i < segments.length - 1; i++) {
                    String prefix = segments[i];
                    if (prefix != null && !prefix.isBlank()) {
                        spellings.add(prefix + "." + last);
                    }
                }
            }
        }

        return spellings;
    }

    static LinkedHashSet<String> collectCandidateUris(String primaryUri, TransferDescription td) {
        LinkedHashSet<String> uris = new LinkedHashSet<>();
        if (primaryUri != null && !primaryUri.isBlank()) {
            uris.add(primaryUri);
        }
        if (td == null) {
            return uris;
        }

        Set<Model> seen = new LinkedHashSet<>();
        Model[] models = td.getModelsFromLastFile();
        if (models != null) {
            for (Model model : models) {
                collectModelUrisRecursive(model, seen, uris);
            }
        }
        return uris;
    }

    private static void collectModelUrisRecursive(Model model, Set<Model> seen, Set<String> uris) {
        if (model == null || !seen.add(model)) {
            return;
        }
        String fileName = model.getFileName();
        if (fileName != null && !fileName.isBlank()) {
            String normalized = InterlisTextDocumentService.toFilesystemPathIfPossible(fileName);
            if (normalized != null && !normalized.isBlank()) {
                try {
                    Path path = Paths.get(normalized);
                    uris.add(path.toUri().toString());
                } catch (Exception ex) {
                    LOG.debug("Unable to resolve model path {}", fileName, ex);
                }
            }
        }
        Model[] imports = model.getImporting();
        if (imports != null) {
            for (Model imp : imports) {
                collectModelUrisRecursive(imp, seen, uris);
            }
        }
    }

    public static List<TextEdit> computeEdits(String text,
                                              String oldName,
                                              String newName,
                                              Set<String> spellings,
                                              int primaryStart,
                                              int primaryEnd) {
        if (text == null || text.isEmpty() || oldName == null || oldName.isBlank()) {
            return Collections.emptyList();
        }

        List<TextEdit> edits = new ArrayList<>();
        int length = text.length();
        int index = 0;
        int primaryEndTokenStart = findPrimaryEndToken(text, oldName, primaryStart);
        while (index < length) {
            char ch = text.charAt(index);
            if (!InterlisNameResolver.isIdentifierPart(ch)) {
                index++;
                continue;
            }

            int start = index;
            index++;
            while (index < length && InterlisNameResolver.isIdentifierPart(text.charAt(index))) {
                index++;
            }
            int end = index;
            String current = text.substring(start, end);
            if (!spellings.contains(current) || !oldName.equals(InterlisNameResolver.lastSegment(current))) {
                continue;
            }
            if (!shouldRenameToken(text, start, end, current, oldName, primaryStart, primaryEnd, primaryEndTokenStart)) {
                continue;
            }
            String replacement = replaceLastSegment(current, newName);
            edits.add(new TextEdit(new Range(DocumentTracker.positionAt(text, start), DocumentTracker.positionAt(text, end)), replacement));
        }
        return edits;
    }

    private static boolean shouldRenameToken(String text,
                                             int start,
                                             int end,
                                             String token,
                                             String oldName,
                                             int primaryStart,
                                             int primaryEnd,
                                             int primaryEndTokenStart) {
        boolean qualified = token != null && token.indexOf('.') >= 0;
        if (qualified) {
            return true;
        }
        if (primaryStart >= 0 && primaryEnd >= 0 && start == primaryStart && end == primaryEnd) {
            return true;
        }
        if (isDefinitionContext(text, start)) {
            return false;
        }
        if (isEndContext(text, start)) {
            return start == primaryEndTokenStart;
        }
        return true;
    }

    private static boolean isDefinitionContext(String text, int tokenStart) {
        if (text == null || text.isEmpty() || tokenStart <= 0) {
            return false;
        }
        int index = tokenStart - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        if (index < 0) {
            return false;
        }
        int wordEnd = index;
        while (index >= 0 && Character.isLetter(text.charAt(index))) {
            index--;
        }
        if (wordEnd <= index) {
            return false;
        }
        String preceding = text.substring(index + 1, wordEnd + 1).toUpperCase(java.util.Locale.ROOT);
        return DEFINITION_KEYWORDS.contains(preceding);
    }

    private static boolean isEndContext(String text, int tokenStart) {
        if (text == null || text.isEmpty() || tokenStart <= 0) {
            return false;
        }
        int index = tokenStart - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        if (index < 0) {
            return false;
        }
        int wordEnd = index;
        while (index >= 0 && Character.isLetter(text.charAt(index))) {
            index--;
        }
        if (wordEnd <= index) {
            return false;
        }
        String preceding = text.substring(index + 1, wordEnd + 1).toUpperCase(java.util.Locale.ROOT);
        return "END".equals(preceding);
    }

    private static int findPrimaryEndToken(String text, String oldName, int primaryStart) {
        if (text == null || text.isEmpty() || oldName == null || oldName.isBlank() || primaryStart < 0) {
            return -1;
        }
        int searchFrom = Math.max(0, primaryStart);
        int length = text.length();
        int nameLength = oldName.length();
        while (searchFrom < length) {
            int endIndex = text.indexOf("END", searchFrom);
            if (endIndex < 0) {
                return -1;
            }
            int cursor = endIndex + 3;
            while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor + nameLength <= length && text.regionMatches(cursor, oldName, 0, nameLength)) {
                int afterName = cursor + nameLength;
                if (afterName >= length || !InterlisNameResolver.isIdentifierPart(text.charAt(afterName))) {
                    return cursor;
                }
            }
            searchFrom = cursor + 1;
        }
        return -1;
    }

    private static final Set<String> DEFINITION_KEYWORDS = Set.of(
            "CLASS", "STRUCTURE", "TABLE", "VIEW", "ASSOCIATION",
            "DOMAIN", "UNIT", "TOPIC", "MODEL", "ENUMERATION", "FUNCTION", "PROCEDURE");

    static String replaceLastSegment(String token, String newName) {
        int idx = token != null ? token.lastIndexOf('.') : -1;
        return idx < 0 ? newName : token.substring(0, idx + 1) + newName;
    }

    static boolean isValidIdentifier(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        char first = text.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    private static WorkspaceEdit emptyEdit() {
        WorkspaceEdit edit = new WorkspaceEdit();
        edit.setChanges(Collections.emptyMap());
        return edit;
    }

    static Element resolveElementAtPosition(TransferDescription td,
                                            String uri,
                                            Position position,
                                            String name) {
        if (td == null || uri == null || uri.isBlank() || position == null || name == null || name.isBlank()) {
            return null;
        }

        Path targetPath = normalizePath(uri);
        if (targetPath == null) {
            return null;
        }

        int line = position.getLine() + 1;
        ElementMatch best = findClosestElement(td, targetPath, line, name);
        return best != null ? best.element : null;
    }

    private static ElementMatch findClosestElement(TransferDescription td,
                                                   Path targetPath,
                                                   int line,
                                                   String name) {
        if (td == null) {
            return null;
        }

        ElementMatch best = null;
        Iterator<Model> iterator = td.iterator();
        while (iterator.hasNext()) {
            Object next = iterator.next();
            if (next instanceof Element element) {
                ElementMatch candidate = findClosestElement(element, targetPath, line, name);
                best = chooseBetter(best, candidate);
            }
        }
        return best;
    }

    private static ElementMatch findClosestElement(Element element,
                                                   Path targetPath,
                                                   int line,
                                                   String name) {
        if (element == null) {
            return null;
        }

        ElementMatch best = null;
        Path elementPath = elementPath(element);
        if (elementPath != null && elementPath.equals(targetPath) && name.equals(element.getName())) {
            best = new ElementMatch(element, computeDistance(element.getSourceLine(), line), depth(element));
        }

        if (element instanceof ch.interlis.ili2c.metamodel.Container<?> container) {
            Iterator<?> iterator = container.iterator();
            while (iterator.hasNext()) {
                Object child = iterator.next();
                if (child instanceof Element childElement) {
                    ElementMatch candidate = findClosestElement(childElement, targetPath, line, name);
                    best = chooseBetter(best, candidate);
                }
            }
        }
        return best;
    }

    private static ElementMatch chooseBetter(ElementMatch current, ElementMatch candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.distance < current.distance) {
            return candidate;
        }
        if (candidate.distance == current.distance && candidate.depth > current.depth) {
            return candidate;
        }
        String candidateScoped = safeScopedName(candidate.element);
        String currentScoped = safeScopedName(current != null ? current.element : null);
        if (candidate.distance == current.distance
                && candidateScoped != null
                && (currentScoped == null || candidateScoped.length() > currentScoped.length())) {
            return candidate;
        }
        return current;
    }

    private static int computeDistance(int elementLine, int targetLine) {
        if (elementLine <= 0 || targetLine <= 0) {
            return elementLine == targetLine ? 0 : Integer.MAX_VALUE / 2;
        }
        return Math.abs(elementLine - targetLine);
    }

    private static int depth(Element element) {
        int depth = 0;
        ch.interlis.ili2c.metamodel.Container<?> container = element != null ? element.getContainer() : null;
        while (container instanceof Element parent) {
            depth++;
            container = parent.getContainer();
        }
        return depth;
    }

    private static String safeScopedName(Element element) {
        if (element == null) {
            return null;
        }
        try {
            return element.getScopedName();
        } catch (Exception ex) {
            return null;
        }
    }

    private static Path elementPath(Element element) {
        Model model = InterlisNameResolver.findEnclosingModel(element);
        if (model == null || model.getFileName() == null || model.getFileName().isBlank()) {
            return null;
        }
        return normalizePath(model.getFileName());
    }

    private static Path normalizePath(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.isBlank()) {
            return null;
        }
        String candidate = InterlisTextDocumentService.toFilesystemPathIfPossible(uriOrPath);
        if (candidate == null || candidate.isBlank()) {
            candidate = uriOrPath;
        }
        try {
            return Paths.get(candidate).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class ElementMatch {
        final Element element;
        final int distance;
        final int depth;

        private ElementMatch(Element element, int distance, int depth) {
            this.element = element;
            this.distance = distance;
            this.depth = depth;
        }
    }
}
