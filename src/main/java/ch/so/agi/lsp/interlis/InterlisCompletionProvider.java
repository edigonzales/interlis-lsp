package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Viewable;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InterlisCompletionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisCompletionProvider.class);
    private static final Pattern IMPORTS_PATTERN = Pattern.compile("(?i)\\bIMPORTS\\b");
    private static final Set<String> TYPE_REFERENCE_KEYWORDS = Set.of(
            "OF",
            "FROM",
            "EXTENDS",
            "REF",
            "REFS",
            "REFERS",
            "REFERENCE",
            "BASE",
            "BASED",
            "OID",
            "AS"
    );
    private static final Set<String> TYPE_REFERENCE_SKIP_TOKENS = Set.of(
            "MANDATORY",
            "OPTIONAL",
            "SET",
            "LIST",
            "BAG",
            "ARRAY",
            "ORDERED",
            "SORTED",
            "ANYCLASS",
            "ANYSTRUCTURE"
    );

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationCache compilationCache;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;
    private final ModelDiscoveryService modelDiscoveryService;

    InterlisCompletionProvider(InterlisLanguageServer server,
                               DocumentTracker documents,
                               CompilationCache compilationCache,
                               BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                               ModelDiscoveryService discoveryService) {
        this.server = server;
        this.documents = documents;
        this.compilationCache = compilationCache;
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
        this.modelDiscoveryService = discoveryService;
    }

    Either<List<CompletionItem>, CompletionList> complete(CompletionParams params) {
        try {
            if (params == null || params.getTextDocument() == null) {
                return Either.forLeft(Collections.emptyList());
            }

            String uri = params.getTextDocument().getUri();
            if (uri == null || uri.isBlank()) {
                return Either.forLeft(Collections.emptyList());
            }

            String text = documents.getText(uri);
            if (text == null) {
                text = InterlisTextDocumentService.readDocument(uri);
            }
            if (text == null) {
                text = "";
            }

            Position position = params.getPosition();
            int offset = DocumentTracker.toOffset(text, position);

            ImportsContext importsContext = findImportsContext(text, offset);
            if (importsContext != null) {
                List<CompletionItem> items = buildImportCompletions(text, offset, importsContext);
                if (!items.isEmpty()) {
                    return Either.forLeft(items);
                }
            }

            TransferDescription td = obtainTransferDescription(uri);
            if (td == null) {
                return Either.forLeft(Collections.emptyList());
            }

            int lineStart = lineStartOffset(text, offset);
            String lineText = text.substring(lineStart, Math.min(offset, text.length()));

            List<CompletionItem> dotted = completeDottedPath(text, offset, lineText, td);
            if (!dotted.isEmpty()) {
                return Either.forLeft(dotted);
            }

            List<CompletionItem> word = completeModelWord(text, offset, lineText, td);
            if (!word.isEmpty()) {
                return Either.forLeft(word);
            }

            return Either.forLeft(Collections.emptyList());
        } catch (Exception ex) {
            LOG.warn("Completion failed", ex);
            return Either.forLeft(Collections.emptyList());
        }
    }

    private List<CompletionItem> buildImportCompletions(String text, int caretOffset, ImportsContext context) {
        ClientSettings settings = server.getClientSettings();
        Set<String> already = new HashSet<>();
        for (String existing : context.already()) {
            if (existing != null) {
                already.add(existing.toUpperCase(Locale.ROOT));
            }
        }

        List<String> candidates = modelDiscoveryService.searchModels(settings, context.prefix(), already);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletionItem> items = new ArrayList<>(candidates.size());
        Range range = new Range(DocumentTracker.positionAt(text, context.prefixStartOffset()),
                DocumentTracker.positionAt(text, caretOffset));
        for (String candidate : candidates) {
            CompletionItem item = new CompletionItem(candidate);
            item.setKind(CompletionItemKind.Module);
            item.setTextEdit(Either.forLeft(new TextEdit(range, candidate)));
            items.add(item);
        }
        return items;
    }

    private List<CompletionItem> completeDottedPath(String text, int caretOffset, String lineText, TransferDescription td) {
        String path = trailingPath(lineText);
        if (path.isEmpty() || !path.contains(".")) {
            return Collections.emptyList();
        }

        String[] parts = path.split("\\.", -1);
        if (parts.length < 2) {
            return Collections.emptyList();
        }

        int pathStartOffset = Math.max(caretOffset - path.length(), 0);
        if (!isTypeReferenceContext(text, pathStartOffset)) {
            return Collections.emptyList();
        }

        String prefix = parts[parts.length - 1];
        Object parent = resolveChain(td, parts, parts.length - 1);
        if (parent == null) {
            return Collections.emptyList();
        }

        List<InterlisAstUtil.ChildCandidate> children = InterlisAstUtil.collectChildren(parent);
        if (children.isEmpty()) {
            return Collections.emptyList();
        }

        String upperPrefix = prefix.toUpperCase(Locale.ROOT);
        int replaceStartOffset = Math.max(caretOffset - prefix.length(), 0);
        Range range = new Range(DocumentTracker.positionAt(text, replaceStartOffset),
                DocumentTracker.positionAt(text, caretOffset));

        LinkedHashMap<String, CompletionItem> unique = new LinkedHashMap<>();
        for (InterlisAstUtil.ChildCandidate child : children) {
            String name = child.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!prefix.isEmpty() && !name.toUpperCase(Locale.ROOT).startsWith(upperPrefix)) {
                continue;
            }
            String key = name.toUpperCase(Locale.ROOT);
            if (unique.containsKey(key)) {
                continue;
            }
            CompletionItem item = new CompletionItem(name);
            item.setKind(child.kind());
            item.setTextEdit(Either.forLeft(new TextEdit(range, name)));
            unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    private List<CompletionItem> completeModelWord(String text, int caretOffset, String lineText, TransferDescription td) {
        String word = currentWord(lineText);
        if (word.isEmpty()) {
            return Collections.emptyList();
        }

        int wordStartOffset = Math.max(caretOffset - word.length(), 0);
        if (!isTypeReferenceContext(text, wordStartOffset)) {
            return Collections.emptyList();
        }

        List<String> modelNames = InterlisAstUtil.modelNamesForDocument(td);
        if (modelNames.isEmpty()) {
            return Collections.emptyList();
        }

        String upperPrefix = word.toUpperCase(Locale.ROOT);
        LinkedHashMap<String, CompletionItem> items = new LinkedHashMap<>();
        Range range = new Range(DocumentTracker.positionAt(text, Math.max(caretOffset - word.length(), 0)),
                DocumentTracker.positionAt(text, caretOffset));

        for (String modelName : modelNames) {
            if (modelName == null) {
                continue;
            }
            if (!modelName.toUpperCase(Locale.ROOT).startsWith(upperPrefix)) {
                continue;
            }
            String key = modelName.toUpperCase(Locale.ROOT);
            if (items.containsKey(key)) {
                continue;
            }
            CompletionItem item = new CompletionItem(modelName);
            item.setKind(CompletionItemKind.Module);
            item.setTextEdit(Either.forLeft(new TextEdit(range, modelName)));
            items.put(key, item);
        }

        return new ArrayList<>(items.values());
    }

    private TransferDescription obtainTransferDescription(String uri) {
        try {
            String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
            if (pathOrUri == null || pathOrUri.isBlank()) {
                return null;
            }
            ClientSettings settings = server.getClientSettings();
            Ili2cUtil.CompilationOutcome outcome = compilationCache.get(pathOrUri);
            if (outcome == null || outcome.getTransferDescription() == null) {
                outcome = compiler.apply(settings, pathOrUri);
                if (outcome != null) {
                    compilationCache.put(pathOrUri, outcome);
                }
            }
            return outcome != null ? outcome.getTransferDescription() : null;
        } catch (Exception ex) {
            LOG.warn("Failed to obtain transfer description for completion", ex);
            return null;
        }
    }

    private static int lineStartOffset(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int index = Math.min(Math.max(offset, 0), text.length());
        while (index > 0) {
            char ch = text.charAt(index - 1);
            if (ch == '\n' || ch == '\r') {
                break;
            }
            index--;
        }
        return index;
    }

    private static String trailingPath(String lineText) {
        int i = lineText.length() - 1;
        while (i >= 0) {
            char c = lineText.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                i--;
            } else {
                break;
            }
        }
        return lineText.substring(i + 1);
    }

    private static String currentWord(String lineText) {
        int i = lineText.length() - 1;
        while (i >= 0) {
            char c = lineText.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                i--;
            } else {
                break;
            }
        }
        return lineText.substring(i + 1);
    }

    private static boolean isTypeReferenceContext(String text, int tokenStart) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int index = Math.min(Math.max(tokenStart, 0), text.length());
        int i = index - 1;
        while (i >= 0) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                i--;
                continue;
            }
            if (ch == ':') {
                return true;
            }
            if (ch == ';' || ch == ',' || ch == '=' || ch == '(' || ch == ')' || ch == '{' || ch == '}') {
                return false;
            }
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                int end = i;
                while (i >= 0) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        i--;
                    } else {
                        break;
                    }
                }
                String token = text.substring(i + 1, end + 1);
                String upper = token.toUpperCase(Locale.ROOT);
                if (TYPE_REFERENCE_KEYWORDS.contains(upper)) {
                    return true;
                }
                if (TYPE_REFERENCE_SKIP_TOKENS.contains(upper) || upper.equals("TO")) {
                    // Continue scanning to find the actual trigger (e.g. REFERENCE TO, BAG OF)
                    continue;
                }
                return false;
            }
            return false;
        }
        return false;
    }

    private static Object resolveChain(TransferDescription td, String[] parts, int stop) {
        if (td == null || parts == null || stop <= 0) {
            return null;
        }
        Model model = InterlisAstUtil.resolveModel(td, parts[0]);
        if (model == null) {
            return null;
        }
        Object current = model;
        for (int i = 1; i < stop; i++) {
            String name = parts[i];
            if (name == null || name.isEmpty()) {
                return null;
            }
            if (current instanceof Model currentModel) {
                Object child = InterlisAstUtil.findChildInModelByName(currentModel, name);
                if (!(child instanceof Topic) && !(child instanceof Viewable)) {
                    return null;
                }
                current = child;
            } else if (current instanceof Topic topic) {
                Object child = InterlisAstUtil.findChildInContainerByName(topic, name);
                if (!(child instanceof Viewable)) {
                    return null;
                }
                current = child;
            } else if (current instanceof Viewable viewable) {
                if (!InterlisAstUtil.hasAttributeOrRole(viewable, name)) {
                    return null;
                }
                current = viewable;
            } else {
                return null;
            }
        }
        return current;
    }

    private ImportsContext findImportsContext(String text, int caretOffset) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        final int LOOKBACK = 2000;
        int start = Math.max(0, caretOffset - LOOKBACK);
        String tail = text.substring(start, caretOffset);

        Matcher matcher = IMPORTS_PATTERN.matcher(tail);
        int impStart = -1;
        int impEnd = -1;
        while (matcher.find()) {
            impStart = matcher.start();
            impEnd = matcher.end();
        }
        if (impStart < 0) {
            return null;
        }
        if (tail.indexOf(';', impStart) >= 0) {
            return null;
        }

        String segment = tail.substring(impEnd);
        int lastComma = segment.lastIndexOf(',');
        int tokenRelStart = lastComma >= 0 ? lastComma + 1 : 0;
        int p = tokenRelStart;
        while (p < segment.length() && Character.isWhitespace(segment.charAt(p))) {
            p++;
        }

        int prefixStartAbs = start + impEnd + p;
        String token = segment.substring(p);
        String prefix = token.trim();

        List<String> already = new ArrayList<>();
        String before = segment.substring(0, p).trim();
        if (!before.isEmpty()) {
            String[] names = before.split(",");
            for (String name : names) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    already.add(trimmed);
                }
            }
        }
        return new ImportsContext(prefixStartAbs, prefix, already);
    }

    private record ImportsContext(int prefixStartOffset, String prefix, List<String> already) {
    }
}
