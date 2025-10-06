package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

final class InterlisRenameProvider {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisRenameProvider.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationCache compilationCache;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;

    InterlisRenameProvider(InterlisLanguageServer server,
                           DocumentTracker documents,
                           CompilationCache cache,
                           BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler) {
        this.server = server;
        this.documents = documents;
        this.compilationCache = cache != null ? cache : new CompilationCache();
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
    }

    WorkspaceEdit rename(RenameParams params) {
        WorkspaceEdit empty = emptyEdit();
        if (params == null || params.getTextDocument() == null) {
            return empty;
        }

        String uri = params.getTextDocument().getUri();
        if (uri == null || uri.isBlank()) {
            return empty;
        }

        String newName = params.getNewName();
        if (newName == null) {
            return empty;
        }
        newName = newName.trim();
        if (newName.isEmpty() || !isValidIdentifier(newName)) {
            return empty;
        }

        String documentText = documents != null ? documents.getText(uri) : null;
        if (documentText == null) {
            try {
                documentText = InterlisTextDocumentService.readDocument(uri);
            } catch (Exception ex) {
                if (CancellationUtil.isCancellation(ex)) {
                    throw CancellationUtil.propagateCancellation(ex);
                }
                LOG.warn("Failed to read document {} for rename", uri, ex);
                return empty;
            }
        }
        if (documentText == null || documentText.isEmpty()) {
            return empty;
        }

        Position position = params.getPosition();
        if (position == null) {
            return empty;
        }

        int offset = DocumentTracker.toOffset(documentText, position);
        int start = offset;
        while (start > 0 && InterlisNameResolver.isIdentifierPart(documentText.charAt(start - 1))) {
            start--;
        }

        int end = offset;
        int length = documentText.length();
        while (end < length && InterlisNameResolver.isIdentifierPart(documentText.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return empty;
        }

        String token = documentText.substring(start, end);
        if (token.isBlank()) {
            return empty;
        }

        String oldName = InterlisNameResolver.lastSegment(token);
        if (newName.equals(oldName) || oldName.isBlank()) {
            return empty;
        }

        TransferDescription transferDescription = null;
        String qualifiedName = null;
        String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        if (pathOrUri != null && !pathOrUri.isBlank()) {
            ClientSettings cfg = server.getClientSettings();
            Ili2cUtil.CompilationOutcome outcome = getOrCompile(pathOrUri, cfg);
            transferDescription = outcome != null ? outcome.getTransferDescription() : null;
            if (transferDescription != null) {
                Element element = InterlisNameResolver.resolveElement(transferDescription, token);
                if (element != null) {
                    try {
                        qualifiedName = element.getScopedName();
                    } catch (Exception ex) {
                        LOG.debug("Failed to resolve scoped name for {}", token, ex);
                    }
                }
            }
        }

        LinkedHashSet<String> spellings = collectSpellings(token, qualifiedName, oldName);

        LinkedHashSet<String> targetUris = collectCandidateUris(uri, transferDescription);

        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        for (String targetUri : targetUris) {
            String text = documents != null ? documents.getText(targetUri) : null;
            if (text == null) {
                try {
                    text = InterlisTextDocumentService.readDocument(targetUri);
                } catch (Exception ex) {
                    if (CancellationUtil.isCancellation(ex)) {
                        throw CancellationUtil.propagateCancellation(ex);
                    }
                    LOG.warn("Failed to read document {} for rename", targetUri, ex);
                    continue;
                }
            }
            if (text == null || text.isEmpty()) {
                continue;
            }
            boolean isPrimaryDocument = targetUri.equals(uri);
            int primaryStart = isPrimaryDocument ? start : -1;
            int primaryEnd = isPrimaryDocument ? end : -1;

            List<TextEdit> edits = computeEdits(text, oldName, newName, spellings, primaryStart, primaryEnd);
            if (!edits.isEmpty()) {
                changes.put(targetUri, edits);
            }
        }

        if (changes.isEmpty()) {
            return empty;
        }

        WorkspaceEdit edit = new WorkspaceEdit();
        edit.setChanges(changes);
        return edit;
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

    static LinkedHashSet<String> collectSpellings(String token, String qualifiedName, String oldName) {
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

    static List<TextEdit> computeEdits(String text,
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
            if (!spellings.contains(current)) {
                continue;
            }
            if (!Objects.equals(oldName, InterlisNameResolver.lastSegment(current))) {
                continue;
            }

            if (!shouldRenameToken(text, start, end, current, oldName, primaryStart, primaryEnd, primaryEndTokenStart)) {
                continue;
            }

            String replacement = replaceLastSegment(current, newName);
            if (replacement.equals(current)) {
                continue;
            }

            Position startPos = DocumentTracker.positionAt(text, start);
            Position endPos = DocumentTracker.positionAt(text, end);
            edits.add(new TextEdit(new Range(startPos, endPos), replacement));
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
        while (index >= 0) {
            char ch = text.charAt(index);
            if (!Character.isLetter(ch)) {
                break;
            }
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

    private static final java.util.Set<String> DEFINITION_KEYWORDS = java.util.Set.of(
            "CLASS",
            "STRUCTURE",
            "TABLE",
            "VIEW",
            "ASSOCIATION",
            "DOMAIN",
            "UNIT",
            "TOPIC",
            "MODEL",
            "ENUMERATION",
            "FUNCTION",
            "PROCEDURE"
    );

    private static String replaceLastSegment(String token, String newName) {
        int idx = token != null ? token.lastIndexOf('.') : -1;
        if (idx < 0) {
            return newName;
        }
        return token.substring(0, idx + 1) + newName;
    }

    private static boolean isValidIdentifier(String text) {
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
}
