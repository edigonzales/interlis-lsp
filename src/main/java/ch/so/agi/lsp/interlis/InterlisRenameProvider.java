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

        LinkedHashSet<String> spellings = new LinkedHashSet<>();
        spellings.add(token);
        spellings.add(oldName);

        Model model = null;
        String qualifiedName = null;
        String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        if (pathOrUri != null && !pathOrUri.isBlank()) {
            ClientSettings cfg = server.getClientSettings();
            Ili2cUtil.CompilationOutcome outcome = getOrCompile(pathOrUri, cfg);
            TransferDescription td = outcome != null ? outcome.getTransferDescription() : null;
            if (td != null) {
                Element element = InterlisNameResolver.resolveElement(td, token);
                if (element != null) {
                    model = InterlisNameResolver.findEnclosingModel(element);
                    try {
                        qualifiedName = element.getScopedName();
                    } catch (Exception ex) {
                        LOG.debug("Failed to resolve scoped name for {}", token, ex);
                    }
                }
            }
        }

        if (qualifiedName != null && !qualifiedName.isBlank()) {
            String[] segments = qualifiedName.split("\\.");
            for (int i = 0; i < segments.length; i++) {
                String suffix = String.join(".", Arrays.copyOfRange(segments, i, segments.length));
                if (!suffix.isBlank()) {
                    spellings.add(suffix);
                }
            }
        }

        LinkedHashSet<String> targetUris = new LinkedHashSet<>();
        targetUris.add(uri);
        if (model != null) {
            String fileName = model.getFileName();
            if (fileName != null && !fileName.isBlank()) {
                String normalized = InterlisTextDocumentService.toFilesystemPathIfPossible(fileName);
                try {
                    Path path = Paths.get(normalized);
                    targetUris.add(path.toUri().toString());
                } catch (Exception ex) {
                    LOG.debug("Unable to resolve model path {}", fileName, ex);
                }
            }
        }

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
            List<TextEdit> edits = computeEdits(text, oldName, newName, spellings);
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

    private static List<TextEdit> computeEdits(String text,
                                               String oldName,
                                               String newName,
                                               Set<String> spellings) {
        if (text == null || text.isEmpty() || oldName == null || oldName.isBlank()) {
            return Collections.emptyList();
        }

        List<TextEdit> edits = new ArrayList<>();
        int length = text.length();
        int index = 0;
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
