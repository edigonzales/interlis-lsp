package ch.so.agi.lsp.interlis.text;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.live.CompletionContext;
import ch.so.agi.lsp.interlis.live.DocumentSnapshot;
import ch.so.agi.lsp.interlis.live.InterlisLanguageLevel;
import ch.so.agi.lsp.interlis.live.InterlisMetamodelSupport;
import ch.so.agi.lsp.interlis.live.InterlisSymbolKind;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.live.LiveParseResult;
import ch.so.agi.lsp.interlis.live.LiveSymbol;
import ch.so.agi.lsp.interlis.live.SymbolId;
import ch.so.agi.lsp.interlis.model.ModelDiscoveryService;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InterlisCompletionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisCompletionProvider.class);

    private static final Pattern IMPORTS_PATTERN = Pattern.compile("(?i)\\bIMPORTS\\b");
    private static final Pattern END_PATTERN = Pattern.compile("(?i)\\bEND\\s+([A-Za-z0-9_]*)$");
    private static final Pattern EXTENDS_CONTEXT_PATTERN = Pattern.compile(
            "(?i)\\bEXTENDS\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*$");
    private static final Pattern REFERENCE_TARGET_PATTERN = Pattern.compile(
            "(?i):\\s*REFERENCE\\s+TO\\b(?:\\s*\\(\\s*EXTERNAL\\s*\\))?\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*$");
    private static final Pattern REFERENCE_POST_PATTERN = Pattern.compile("(?i):\\s*REFERENCE\\s*([A-Za-z_]*)\\s*$");
    private static final Pattern COLLECTION_OF_PATTERN = Pattern.compile(
            "(?i):\\s*(?:LIST|BAG)(?:\\s*\\{[^}]*\\})?\\s+OF\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*$");
    private static final Pattern META_TAIL_PATTERN = Pattern.compile("(?i):\\s*(CLASS|STRUCTURE|ATTRIBUTE)\\s*([A-Za-z_]*)\\s*$");
    private static final Pattern TEXT_LENGTH_PATTERN = Pattern.compile("(?i):\\s*(?:MANDATORY\\s+)?(TEXT|MTEXT)\\s*$");
    private static final Pattern TEXT_LENGTH_VALUE_PATTERN = Pattern.compile("(?i):\\s*(?:MANDATORY\\s+)?(?:TEXT|MTEXT)\\s*\\*\\s*$");
    private static final Pattern INLINE_NUMERIC_RANGE_PATTERN = Pattern.compile("(?i):\\s*(?:MANDATORY\\s+)?([-+]?[0-9]+(?:\\.[0-9]+)?)\\s*$");
    private static final Pattern INLINE_NUMERIC_UPPER_BOUND_PATTERN = Pattern.compile(
            "(?i):\\s*(?:MANDATORY\\s+)?([-+]?[0-9]+(?:\\.[0-9]+)?)\\s*\\.\\.\\s*$");
    private static final Pattern FORMAT_TYPE_PATTERN = Pattern.compile(
            "(?i):\\s*(?:MANDATORY\\s+)?FORMAT\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*$");
    private static final Pattern FORMAT_BOUNDS_PATTERN = Pattern.compile(
            "(?i):\\s*(?:MANDATORY\\s+)?FORMAT\\s+(INTERLIS\\.(?:XMLDate|XMLDateTime|XMLTime))\\s*$");
    private static final Pattern ATTRIBUTE_ROOT_PATTERN = Pattern.compile(
            ":\\s*(?:MANDATORY\\s+)?([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*$");

    private static final Set<InterlisSymbolKind> ATTRIBUTE_TYPE_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.DOMAIN, InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> COLLECTION_TARGET_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> REFERENCE_TARGET_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW);

    private static final List<String> ATTRIBUTE_ROOT_BASE_KEYWORDS = List.of(
            "MANDATORY", "FORMAT", "TEXT", "MTEXT", "NAME", "URI", "BOOLEAN", "NUMERIC",
            "UUIDOID", "COORD", "MULTICOORD", "POLYLINE", "MULTIPOLYLINE",
            "AREA", "MULTIAREA", "SURFACE", "MULTISURFACE", "REFERENCE", "BAG", "LIST");
    private static final List<String> NATIVE_DATE_TIME_KEYWORDS = List.of("DATE", "TIMEOFDAY", "DATETIME");
    private static final List<String> ATTRIBUTE_META_KEYWORDS = List.of(
            "CLASS", "STRUCTURE", "ATTRIBUTE", "ANYSTRUCTURE");
    private static final List<String> PORTABLE_DATE_TIME_TYPES = List.of(
            "INTERLIS.XMLDate", "INTERLIS.XMLDateTime", "INTERLIS.XMLTime");

    private static final int PRIORITY_TOKEN = 5;
    private static final int PRIORITY_LOCAL = 10;
    private static final int PRIORITY_IMPORTED = 20;
    private static final int PRIORITY_SNIPPET = 30;
    private static final int PRIORITY_KEYWORD = 40;
    private static final int PRIORITY_META = 50;

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationCache compilationCache;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;
    private final ModelDiscoveryService modelDiscoveryService;
    private final LiveAnalysisService liveAnalysis;

    InterlisCompletionProvider(InterlisLanguageServer server,
                               DocumentTracker documents,
                               CompilationCache compilationCache,
                               BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                               ModelDiscoveryService discoveryService,
                               LiveAnalysisService liveAnalysis) {
        this.server = server;
        this.documents = documents;
        this.compilationCache = compilationCache;
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
        this.modelDiscoveryService = discoveryService;
        this.liveAnalysis = liveAnalysis != null ? liveAnalysis : new LiveAnalysisService();
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
                return Either.forLeft(buildImportCompletions(text, offset, importsContext));
            }

            boolean endContext = isEndContext(text, offset);
            if (!endContext && !isPlausibleCompletionContext(text, offset)) {
                return Either.forLeft(Collections.emptyList());
            }

            LiveParseResult live = liveAnalysis.analyze(new DocumentSnapshot(
                    uri,
                    InterlisTextDocumentService.toFilesystemPathIfPossible(uri),
                    text,
                    documents.getVersion(uri)));

            if (endContext) {
                return Either.forLeft(completeEndName(text, offset, position, live));
            }

            CompletionContext context = findLiveContext(live, position);
            if (context == null) {
                context = detectHeuristicContext(text, offset, position, live);
            }
            if (context == null || context.kind() == CompletionContext.Kind.NONE) {
                return Either.forLeft(Collections.emptyList());
            }

            TransferDescription td = needsTransferDescription(context) ? obtainTransferDescription(uri) : null;
            List<CompletionItem> items = switch (context.kind()) {
                case ATTRIBUTE_TYPE_ROOT -> completeAttributeTypeRoot(context, live, td, offset);
                case TEXT_LENGTH_TAIL -> completeTextLengthTail(context);
                case TEXT_LENGTH_VALUE_TAIL -> completeTextLengthValueTail(context);
                case INLINE_NUMERIC_RANGE_TAIL -> completeInlineNumericRangeTail(context);
                case INLINE_NUMERIC_UPPER_BOUND_TAIL -> completeInlineNumericUpperBoundTail(context);
                case FORMAT_TYPE_TARGET -> completeFormatTypeTarget(context, live, td, offset);
                case FORMAT_BOUNDS_TAIL -> completeFormatBoundsTail(context);
                case COLLECTION_POST_KEYWORD -> completeCollectionPostKeyword(context);
                case COLLECTION_OF_TARGET -> completeCollectionTargets(context, live, td, offset);
                case REFERENCE_POST_KEYWORD -> completeReferencePostKeyword(context);
                case REFERENCE_TARGET -> completeReferenceTargets(context, live, td, offset);
                case EXTENDS_TARGET -> completeExtendsTargets(context, live, td, offset);
                case META_TYPE_TAIL -> completeMetaTypeTail(context);
                case QUALIFIED_MEMBER -> completeQualifiedMembersLegacy(context, live, td, offset);
                case IMPORT_MODEL, END_NAME, NONE -> Collections.emptyList();
            };
            return Either.forLeft(dedupe(items));
        } catch (Exception ex) {
            if (CancellationUtil.isCancellation(ex)) {
                throw CancellationUtil.propagateCancellation(ex);
            }
            LOG.warn("Completion failed", ex);
            return Either.forLeft(Collections.emptyList());
        }
    }

    private List<CompletionItem> completeEndName(String text, int caretOffset, Position position, LiveParseResult live) {
        int lineStart = lineStartOffset(text, caretOffset);
        String prefix = text.substring(lineStart, Math.min(caretOffset, text.length()));
        Matcher matcher = END_PATTERN.matcher(prefix);
        if (!matcher.find()) {
            return Collections.emptyList();
        }

        String typedPrefix = matcher.group(1) != null ? matcher.group(1) : "";
        Range replaceRange = new Range(
                DocumentTracker.positionAt(text, caretOffset - typedPrefix.length()),
                DocumentTracker.positionAt(text, caretOffset));
        LiveSymbol container = live != null ? live.scopeGraph().findEnclosingContainer(position) : null;
        if (container == null) {
            return Collections.emptyList();
        }
        return List.of(item(container.name(), container.kind().toCompletionKind(), replaceRange, PRIORITY_LOCAL, typedPrefix));
    }

    private List<CompletionItem> completeAttributeTypeRoot(CompletionContext context,
                                                           LiveParseResult live,
                                                           TransferDescription td,
                                                           int resolutionOffset) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return completeAttributeTypeRootQualifiedMembers(context, live, td, resolutionOffset);
        }
        List<CompletionItem> items = new ArrayList<>();
        addKeywords(items, attributeRootKeywords(live, context), context, PRIORITY_KEYWORD);
        addPortableDateTimeSuggestions(items, context, live != null ? live.languageLevel() : InterlisLanguageLevel.UNKNOWN);
        if (context.prefix() != null && !context.prefix().isBlank()) {
            addKeywords(items, ATTRIBUTE_META_KEYWORDS, context, PRIORITY_META);
        }
        addAttributeRootSnippets(items, context);
        return items;
    }

    private List<CompletionItem> completeAttributeTypeRootQualifiedMembers(CompletionContext context,
                                                                           LiveParseResult live,
                                                                           TransferDescription td,
                                                                           int resolutionOffset) {
        String qualifierPath = context.qualifierPath();
        if (qualifierPath != null && "INTERLIS".equalsIgnoreCase(qualifierPath)) {
            return completeQualifiedMembers(context, live, td, resolutionOffset);
        }
        return Collections.emptyList();
    }

    private List<CompletionItem> completeTextLengthTail(CompletionContext context) {
        List<CompletionItem> items = new ArrayList<>();
        addKeyword(items, "*", context, PRIORITY_TOKEN);
        addSnippet(items, "* <length>", "*${1:255}", context, PRIORITY_SNIPPET, "*");
        return items;
    }

    private List<CompletionItem> completeTextLengthValueTail(CompletionContext context) {
        return List.of(snippet(
                "<length>",
                "${1:255}",
                context.replaceRange(),
                PRIORITY_SNIPPET,
                context.prefix(),
                ""));
    }

    private List<CompletionItem> completeInlineNumericRangeTail(CompletionContext context) {
        List<CompletionItem> items = new ArrayList<>();
        addKeyword(items, "..", context, PRIORITY_TOKEN);
        addSnippet(items, ".. <upper>", ".. ${1}", context, PRIORITY_SNIPPET, "..");
        return items;
    }

    private List<CompletionItem> completeInlineNumericUpperBoundTail(CompletionContext context) {
        return List.of(snippet(
                "<upper>",
                "${1}",
                context.replaceRange(),
                PRIORITY_SNIPPET,
                context.prefix(),
                ""));
    }

    private List<CompletionItem> completeFormatTypeTarget(CompletionContext context,
                                                          LiveParseResult live,
                                                          TransferDescription td,
                                                          int resolutionOffset) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return completeQualifiedMembers(context, live, td, resolutionOffset);
        }
        List<CompletionItem> items = new ArrayList<>();
        addVisibleFormattedDomains(items, live, context, PRIORITY_LOCAL, resolutionOffset);
        addPortableDateTimeSuggestions(items, context, InterlisLanguageLevel.UNKNOWN);
        addModelStarters(items, live, td, context, PRIORITY_IMPORTED, resolutionOffset);
        addSnippet(items, "BASED ON ...", "BASED ON ${1:Structure} (${2:\"\"})", context, PRIORITY_SNIPPET, "BASED ON");
        return items;
    }

    private List<CompletionItem> completeFormatBoundsTail(CompletionContext context) {
        if (context.subject() == null || context.subject().isBlank()) {
            return Collections.emptyList();
        }
        String upper = context.subject().toUpperCase(Locale.ROOT);
        if ("INTERLIS.XMLDATE".equals(upper)) {
            return List.of(snippet(
                    "\"min\" .. \"max\"",
                    "\"${1:1990-1-1}\" .. \"${2:2100-12-31}\"",
                    context.replaceRange(),
                    PRIORITY_SNIPPET,
                    context.prefix(),
                    "\""));
        }
        if ("INTERLIS.XMLDATETIME".equals(upper)) {
            return List.of(snippet(
                    "\"min\" .. \"max\"",
                    "\"${1:1990-1-1T00:00:00.000}\" .. \"${2:2100-12-31T23:59:59.999}\"",
                    context.replaceRange(),
                    PRIORITY_SNIPPET,
                    context.prefix(),
                    "\""));
        }
        if ("INTERLIS.XMLTIME".equals(upper)) {
            return List.of(snippet(
                    "\"min\" .. \"max\"",
                    "\"${1:00:00:00.000}\" .. \"${2:23:59:59.999}\"",
                    context.replaceRange(),
                    PRIORITY_SNIPPET,
                    context.prefix(),
                    "\""));
        }
        return Collections.emptyList();
    }

    private List<CompletionItem> completeCollectionPostKeyword(CompletionContext context) {
        List<CompletionItem> items = new ArrayList<>();
        String tail = context.subject() != null ? context.subject() : "";
        boolean hasCompleteCardinality = tail.contains("}");
        if (!hasCompleteCardinality) {
            addKeyword(items, "{", context, PRIORITY_TOKEN);
        }
        addKeyword(items, "OF", context, PRIORITY_TOKEN);
        return items;
    }

    private List<CompletionItem> completeCollectionTargets(CompletionContext context,
                                                           LiveParseResult live,
                                                           TransferDescription td,
                                                           int resolutionOffset) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return completeQualifiedMembers(context, live, td, resolutionOffset);
        }
        List<CompletionItem> items = new ArrayList<>();
        addVisibleSymbols(items, live, context, PRIORITY_LOCAL, resolutionOffset);
        addModelStarters(items, live, td, context, PRIORITY_IMPORTED, resolutionOffset);
        addKeyword(items, "ANYSTRUCTURE", context, PRIORITY_KEYWORD);
        return items;
    }

    private List<CompletionItem> completeReferencePostKeyword(CompletionContext context) {
        List<CompletionItem> items = new ArrayList<>();
        addKeyword(items, "TO", context, PRIORITY_TOKEN);
        return items;
    }

    private List<CompletionItem> completeReferenceTargets(CompletionContext context,
                                                          LiveParseResult live,
                                                          TransferDescription td,
                                                          int resolutionOffset) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return completeQualifiedMembers(context, live, td, resolutionOffset);
        }
        List<CompletionItem> items = new ArrayList<>();
        addVisibleSymbols(items, live, context, PRIORITY_LOCAL, resolutionOffset);
        addModelStarters(items, live, td, context, PRIORITY_IMPORTED, resolutionOffset);
        addKeyword(items, "ANYCLASS", context, PRIORITY_KEYWORD);
        return items;
    }

    private List<CompletionItem> completeExtendsTargets(CompletionContext context,
                                                        LiveParseResult live,
                                                        TransferDescription td,
                                                        int resolutionOffset) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return completeQualifiedMembers(context, live, td, resolutionOffset);
        }
        List<CompletionItem> items = new ArrayList<>();
        addVisibleSymbols(items, live, context, PRIORITY_LOCAL, resolutionOffset);
        addModelStarters(items, live, td, context, PRIORITY_IMPORTED, resolutionOffset);
        return items;
    }

    private List<CompletionItem> completeMetaTypeTail(CompletionContext context) {
        List<CompletionItem> items = new ArrayList<>();
        addKeyword(items, "RESTRICTION", context, PRIORITY_TOKEN);
        if ("ATTRIBUTE".equalsIgnoreCase(context.subject())) {
            addKeyword(items, "OF", context, PRIORITY_TOKEN);
        }
        return items;
    }

    private List<CompletionItem> completeQualifiedMembers(CompletionContext context,
                                                          LiveParseResult live,
                                                          TransferDescription td,
                                                          int resolutionOffset) {
        String parentPath = context.qualifierPath();
        if ((parentPath == null || parentPath.isBlank()) && context.subject() != null && context.subject().contains(".")) {
            parentPath = context.subject().substring(0, context.subject().lastIndexOf('.'));
        }
        if (parentPath == null || parentPath.isBlank()) {
            return Collections.emptyList();
        }

        List<CompletionItem> items = new ArrayList<>();
        addInterlisBuiltInMembers(items, parentPath, context);
        if ("INTERLIS".equalsIgnoreCase(parentPath)
                && (context.kind() == CompletionContext.Kind.ATTRIBUTE_TYPE_ROOT
                || context.kind() == CompletionContext.Kind.FORMAT_TYPE_TARGET)) {
            return items;
        }
        LiveSymbol parent = resolveParentSymbol(live, parentPath, context.scopeOwnerId(), resolutionOffset);
        if (parent != null && live != null) {
            for (LiveSymbol child : live.scopeGraph().children(parent.id())) {
                if (!isAllowedQualifiedCandidate(context, child, live, resolutionOffset)) {
                    continue;
                }
                if (!startsWithIgnoreCase(child.name(), context.prefix())) {
                    continue;
                }
                items.add(item(child.name(), child.kind().toCompletionKind(), context.replaceRange(), PRIORITY_LOCAL, context.prefix()));
            }
        }

        if (td != null && parent == null) {
            String[] parts = parentPath.split("\\.");
            Object parentObject = resolveChain(td, parts, parts.length);
            if (parentObject != null) {
                addImportedQualifiedChildren(items, context, parentObject);
            }
        }
        return items;
    }

    private List<CompletionItem> completeQualifiedMembersLegacy(CompletionContext context,
                                                                LiveParseResult live,
                                                                TransferDescription td,
                                                                int resolutionOffset) {
        return completeQualifiedMembers(context, live, td, resolutionOffset);
    }

    private void addInterlisBuiltInMembers(List<CompletionItem> items,
                                           String parentPath,
                                           CompletionContext context) {
        if (!"INTERLIS".equalsIgnoreCase(parentPath)) {
            return;
        }
        if (context.kind() != CompletionContext.Kind.ATTRIBUTE_TYPE_ROOT
                && context.kind() != CompletionContext.Kind.FORMAT_TYPE_TARGET) {
            return;
        }
        for (String typeName : PORTABLE_DATE_TIME_TYPES) {
            String memberName = typeName.substring(typeName.indexOf('.') + 1);
            if (startsWithIgnoreCase(memberName, context.prefix())) {
                items.add(item(memberName, CompletionItemKind.Keyword, context.replaceRange(), PRIORITY_KEYWORD, context.prefix()));
            }
        }
    }

    private void addVisibleSymbols(List<CompletionItem> items,
                                   LiveParseResult live,
                                   CompletionContext context,
                                   int priority,
                                   int resolutionOffset) {
        if (live == null || context.allowedKinds() == null || context.allowedKinds().isEmpty()) {
            return;
        }
        List<LiveSymbol> visible = live.scopeGraph().visibleSymbolsAt(context.scopeOwnerId(), context.allowedKinds(), resolutionOffset);
        for (LiveSymbol symbol : visible) {
            if (!startsWithIgnoreCase(symbol.name(), context.prefix())) {
                continue;
            }
            items.add(item(symbol.name(), symbol.kind().toCompletionKind(), context.replaceRange(), priority, context.prefix()));
        }
    }

    private void addVisibleFormattedDomains(List<CompletionItem> items,
                                            LiveParseResult live,
                                            CompletionContext context,
                                            int priority,
                                            int resolutionOffset) {
        if (live == null) {
            return;
        }
        List<LiveSymbol> visible = live.scopeGraph().visibleSymbolsAt(context.scopeOwnerId(), Set.of(InterlisSymbolKind.DOMAIN), resolutionOffset);
        for (LiveSymbol symbol : visible) {
            if (!isFormattedLocalDomain(live, symbol) || !startsWithIgnoreCase(symbol.name(), context.prefix())) {
                continue;
            }
            items.add(item(symbol.name(), symbol.kind().toCompletionKind(), context.replaceRange(), priority, context.prefix()));
        }
    }

    private void addModelStarters(List<CompletionItem> items,
                                  LiveParseResult live,
                                  TransferDescription td,
                                  CompletionContext context,
                                  int priority,
                                  int resolutionOffset) {
        if (context.prefix() == null || context.prefix().isBlank()) {
            return;
        }

        LinkedHashSet<String> modelNames = new LinkedHashSet<>();
        if (live != null) {
            for (LiveSymbol root : live.scopeGraph().children(null)) {
                if (root.kind() == InterlisSymbolKind.MODEL && isVisibleAt(root, resolutionOffset)) {
                    modelNames.add(root.name());
                }
            }
        }
        if (td != null) {
            modelNames.addAll(InterlisAstUtil.modelNamesForDocument(td));
        }

        for (String modelName : modelNames) {
            if (startsWithIgnoreCase(modelName, context.prefix())) {
                items.add(item(modelName, CompletionItemKind.Module, context.replaceRange(), priority, context.prefix()));
            }
        }
    }

    private boolean isAllowedQualifiedCandidate(CompletionContext context,
                                                LiveSymbol child,
                                                LiveParseResult live,
                                                int resolutionOffset) {
        if (child == null || !isVisibleAt(child, resolutionOffset)) {
            return false;
        }
        if (!isAllowed(child.kind(), context.allowedKinds())) {
            return false;
        }
        if (context.kind() == CompletionContext.Kind.FORMAT_TYPE_TARGET) {
            return child.kind() == InterlisSymbolKind.DOMAIN && isFormattedLocalDomain(live, child);
        }
        return true;
    }

    private boolean isFormattedLocalDomain(LiveParseResult live, LiveSymbol symbol) {
        return live != null
                && symbol != null
                && symbol.kind() == InterlisSymbolKind.DOMAIN
                && symbol.id() != null
                && live.formattedDomainIds().contains(symbol.id());
    }

    private void addImportedQualifiedChildren(List<CompletionItem> items,
                                              CompletionContext context,
                                              Object parentObject) {
        if (parentObject instanceof ch.interlis.ili2c.metamodel.Model model) {
            addImportedContainerChildren(items, context, model);
            return;
        }
        if (parentObject instanceof ch.interlis.ili2c.metamodel.Container<?> container) {
            addImportedContainerChildren(items, context, container);
            return;
        }
        for (InterlisAstUtil.ChildCandidate child : InterlisAstUtil.collectChildren(parentObject)) {
            if (!isAllowed(child.symbolKind(), context.allowedKinds())) {
                continue;
            }
            if (!startsWithIgnoreCase(child.name(), context.prefix())) {
                continue;
            }
            items.add(item(child.name(), child.kind(), context.replaceRange(), PRIORITY_IMPORTED, context.prefix()));
        }
    }

    private void addImportedContainerChildren(List<CompletionItem> items,
                                              CompletionContext context,
                                              ch.interlis.ili2c.metamodel.Container<?> container) {
        for (java.util.Iterator<?> iterator = container.iterator(); iterator.hasNext(); ) {
            Object candidate = iterator.next();
            if (!(candidate instanceof ch.interlis.ili2c.metamodel.Element element)) {
                continue;
            }
            String name = element.getName();
            if (name == null || name.isBlank() || !startsWithIgnoreCase(name, context.prefix())) {
                continue;
            }
            InterlisSymbolKind symbolKind = InterlisMetamodelSupport.toSymbolKind(element);
            if (!isAllowed(symbolKind, context.allowedKinds())) {
                continue;
            }
            if (context.kind() == CompletionContext.Kind.FORMAT_TYPE_TARGET
                    && !InterlisMetamodelSupport.isFormattedDomain(element)) {
                continue;
            }
            items.add(item(name, symbolKind.toCompletionKind(), context.replaceRange(), PRIORITY_IMPORTED, context.prefix()));
        }
    }

    private void addKeywords(List<CompletionItem> items,
                             List<String> keywords,
                             CompletionContext context,
                             int priority) {
        for (String keyword : keywords) {
            addKeyword(items, keyword, context, priority);
        }
    }

    private void addKeyword(List<CompletionItem> items,
                            String keyword,
                            CompletionContext context,
                            int priority) {
        if (startsWithIgnoreCase(keyword, context.prefix())) {
            items.add(item(keyword, CompletionItemKind.Keyword, context.replaceRange(), priority, context.prefix()));
        }
    }

    private void addAttributeRootSnippets(List<CompletionItem> items, CompletionContext context) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return;
        }
        addSnippet(items, "LIST OF ...", "LIST OF ${1:Type}", context, PRIORITY_SNIPPET, "LIST");
        addSnippet(items, "LIST {...} OF ...", "LIST {${1:0..*}} OF ${2:Type}", context, PRIORITY_SNIPPET, "LIST");
        addSnippet(items, "BAG OF ...", "BAG OF ${1:Type}", context, PRIORITY_SNIPPET, "BAG");
        addSnippet(items, "BAG {...} OF ...", "BAG {${1:0..*}} OF ${2:Type}", context, PRIORITY_SNIPPET, "BAG");
        addSnippet(items, "REFERENCE TO ...", "REFERENCE TO ${1:Target}", context, PRIORITY_SNIPPET, "REFERENCE");
    }

    private void addSnippet(List<CompletionItem> items,
                            String label,
                            String snippetText,
                            CompletionContext context,
                            int priority,
                            String filterText) {
        if (!startsWithIgnoreCase(filterText, context.prefix())) {
            return;
        }
        items.add(snippet(label, snippetText, context.replaceRange(), priority, context.prefix(), filterText));
    }

    private CompletionContext findLiveContext(LiveParseResult live, Position position) {
        if (live == null || position == null) {
            return null;
        }

        CompletionContext best = null;
        for (CompletionContext context : live.completionContexts()) {
            if (context == null || context.replaceRange() == null || !contains(context.replaceRange(), position)) {
                continue;
            }
            if (best == null || isNarrower(context.replaceRange(), best.replaceRange())) {
                best = context;
            }
        }
        return best;
    }

    private boolean isPlausibleCompletionContext(String text, int caretOffset) {
        String lineText = text.substring(lineStartOffset(text, caretOffset), Math.min(caretOffset, text.length()));
        if (lineText.isBlank()) {
            return false;
        }
        if (lineText.indexOf(':') >= 0) {
            return true;
        }
        if (END_PATTERN.matcher(lineText).find()) {
            return true;
        }
        if (EXTENDS_CONTEXT_PATTERN.matcher(lineText).find()) {
            return true;
        }
        return lineText.endsWith(".");
    }

    private CompletionContext detectHeuristicContext(String text,
                                                     int caretOffset,
                                                     Position position,
                                                     LiveParseResult live) {
        String lineText = text.substring(lineStartOffset(text, caretOffset), Math.min(caretOffset, text.length()));
        LiveSymbol scopeOwner = live != null ? live.scopeGraph().findEnclosingContainer(position) : null;
        int lineStart = lineStartOffset(text, caretOffset);

        Matcher extendsMatcher = EXTENDS_CONTEXT_PATTERN.matcher(lineText);
        if (extendsMatcher.find()) {
            Set<InterlisSymbolKind> allowedKinds = allowedExtendsKinds(scopeOwner != null ? scopeOwner.kind() : null);
            if (!allowedKinds.isEmpty()) {
                return buildHeuristicPathAwareContext(
                        CompletionContext.Kind.EXTENDS_TARGET,
                        text,
                        groupValue(extendsMatcher, 1),
                        groupRange(text, extendsMatcher, 1, lineStart, caretOffset),
                        scopeOwner,
                        allowedKinds);
            }
        }

        Matcher referenceTargetMatcher = REFERENCE_TARGET_PATTERN.matcher(lineText);
        if (referenceTargetMatcher.find()) {
            return buildHeuristicPathAwareContext(
                    CompletionContext.Kind.REFERENCE_TARGET,
                    text,
                    groupValue(referenceTargetMatcher, 1),
                    groupRange(text, referenceTargetMatcher, 1, lineStart, caretOffset),
                    scopeOwner,
                    REFERENCE_TARGET_SYMBOL_KINDS);
        }

        Matcher referencePostMatcher = REFERENCE_POST_PATTERN.matcher(lineText);
        if (referencePostMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.REFERENCE_POST_KEYWORD,
                    groupValue(referencePostMatcher, 1),
                    lineText,
                    null,
                    groupRange(text, referencePostMatcher, 1, lineStart, caretOffset),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher formatBoundsMatcher = FORMAT_BOUNDS_PATTERN.matcher(lineText);
        if (formatBoundsMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.FORMAT_BOUNDS_TAIL,
                    "",
                    groupValue(formatBoundsMatcher, 1),
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher formatTypeMatcher = FORMAT_TYPE_PATTERN.matcher(lineText);
        if (formatTypeMatcher.find()) {
            return buildHeuristicPathAwareContext(
                    CompletionContext.Kind.FORMAT_TYPE_TARGET,
                    text,
                    groupValue(formatTypeMatcher, 1),
                    groupRange(text, formatTypeMatcher, 1, lineStart, caretOffset),
                    scopeOwner,
                    Set.of(InterlisSymbolKind.DOMAIN));
        }

        Matcher collectionOfMatcher = COLLECTION_OF_PATTERN.matcher(lineText);
        if (collectionOfMatcher.find()) {
            return buildHeuristicPathAwareContext(
                    CompletionContext.Kind.COLLECTION_OF_TARGET,
                    text,
                    groupValue(collectionOfMatcher, 1),
                    groupRange(text, collectionOfMatcher, 1, lineStart, caretOffset),
                    scopeOwner,
                    COLLECTION_TARGET_SYMBOL_KINDS);
        }

        if (lineText.matches("(?i).*:\\s*(LIST|BAG)(?:\\s*\\{[^}]*\\})?\\s*[A-Za-z_]*\\s*$")) {
            String prefix = currentWord(lineText);
            Range replaceRange = new Range(
                    DocumentTracker.positionAt(text, Math.max(caretOffset - prefix.length(), 0)),
                    DocumentTracker.positionAt(text, caretOffset));
            return new CompletionContext(
                    CompletionContext.Kind.COLLECTION_POST_KEYWORD,
                    prefix,
                    lineText,
                    null,
                    replaceRange,
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher metaMatcher = META_TAIL_PATTERN.matcher(lineText);
        if (metaMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.META_TYPE_TAIL,
                    groupValue(metaMatcher, 2),
                    groupValue(metaMatcher, 1),
                    null,
                    groupRange(text, metaMatcher, 2, lineStart, caretOffset),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher textLengthMatcher = TEXT_LENGTH_PATTERN.matcher(lineText);
        if (textLengthMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.TEXT_LENGTH_TAIL,
                    "",
                    groupValue(textLengthMatcher, 1),
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher textLengthValueMatcher = TEXT_LENGTH_VALUE_PATTERN.matcher(lineText);
        if (textLengthValueMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.TEXT_LENGTH_VALUE_TAIL,
                    "",
                    "",
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher inlineRangeMatcher = INLINE_NUMERIC_RANGE_PATTERN.matcher(lineText);
        if (inlineRangeMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.INLINE_NUMERIC_RANGE_TAIL,
                    "",
                    groupValue(inlineRangeMatcher, 1),
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher inlineUpperMatcher = INLINE_NUMERIC_UPPER_BOUND_PATTERN.matcher(lineText);
        if (inlineUpperMatcher.find()) {
            return new CompletionContext(
                    CompletionContext.Kind.INLINE_NUMERIC_UPPER_BOUND_TAIL,
                    "",
                    groupValue(inlineUpperMatcher, 1),
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    scopeOwner != null ? scopeOwner.kind() : null);
        }

        Matcher attributeMatcher = ATTRIBUTE_ROOT_PATTERN.matcher(lineText);
        if (attributeMatcher.find() && supportsAttributeTypeContext(scopeOwner != null ? scopeOwner.kind() : null)) {
            return buildHeuristicPathAwareContext(
                    CompletionContext.Kind.ATTRIBUTE_TYPE_ROOT,
                    text,
                    groupValue(attributeMatcher, 1),
                    groupRange(text, attributeMatcher, 1, lineStart, caretOffset),
                    scopeOwner,
                    ATTRIBUTE_TYPE_SYMBOL_KINDS);
        }

        return null;
    }

    private CompletionContext buildHeuristicPathAwareContext(CompletionContext.Kind directKind,
                                                             String text,
                                                             String subject,
                                                             Range replaceRange,
                                                             LiveSymbol scopeOwner,
                                                             Set<InterlisSymbolKind> allowedKinds) {
        String effectiveSubject = subject != null ? subject : "";
        String prefix = effectiveSubject;
        String qualifierPath = null;
        Range effectiveRange = replaceRange;
        if (effectiveSubject.contains(".")) {
            prefix = effectiveSubject.substring(effectiveSubject.lastIndexOf('.') + 1);
            qualifierPath = effectiveSubject.substring(0, effectiveSubject.lastIndexOf('.'));
            int replaceEnd = DocumentTracker.toOffset(text, replaceRange.getEnd());
            int replaceStart = Math.max(replaceEnd - prefix.length(), 0);
            effectiveRange = new Range(
                    DocumentTracker.positionAt(text, replaceStart),
                    DocumentTracker.positionAt(text, replaceEnd));
        }
        return new CompletionContext(
                directKind,
                prefix,
                effectiveSubject,
                qualifierPath,
                effectiveRange,
                scopeOwner != null ? scopeOwner.id() : null,
                allowedKinds,
                scopeOwner != null ? scopeOwner.kind() : null);
    }

    private LiveSymbol resolveParentSymbol(LiveParseResult live,
                                           String parentPath,
                                           SymbolId scopeOwnerId,
                                           int resolutionOffset) {
        if (live == null || parentPath == null || parentPath.isBlank()) {
            return null;
        }
        if (!parentPath.contains(".")) {
            return live.scopeGraph().resolveSimpleAt(parentPath, scopeOwnerId, Collections.emptySet(), resolutionOffset);
        }
        List<LiveSymbol> matches = live.scopeGraph().findQualifiedMatchesAt(parentPath, Collections.emptySet(), resolutionOffset);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private boolean needsTransferDescription(CompletionContext context) {
        if (context == null) {
            return false;
        }
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return true;
        }
        return switch (context.kind()) {
            case QUALIFIED_MEMBER -> true;
            case ATTRIBUTE_TYPE_ROOT, FORMAT_TYPE_TARGET, COLLECTION_OF_TARGET, REFERENCE_TARGET, EXTENDS_TARGET ->
                    context.prefix() != null && !context.prefix().isBlank();
            case IMPORT_MODEL, END_NAME, TEXT_LENGTH_TAIL, TEXT_LENGTH_VALUE_TAIL,
                    INLINE_NUMERIC_RANGE_TAIL, INLINE_NUMERIC_UPPER_BOUND_TAIL, FORMAT_BOUNDS_TAIL,
                    COLLECTION_POST_KEYWORD, REFERENCE_POST_KEYWORD, META_TYPE_TAIL, NONE -> false;
        };
    }

    private TransferDescription obtainTransferDescription(String uri) {
        try {
            String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
            return InteractiveCompilationResolver.resolveTransferDescriptionForInteractiveFeature(
                    server,
                    documents,
                    compilationCache,
                    compiler,
                    uri,
                    pathOrUri,
                    "completion-fallback");
        } catch (Exception ex) {
            if (CancellationUtil.isCancellation(ex)) {
                throw CancellationUtil.propagateCancellation(ex);
            }
            LOG.warn("Failed to obtain transfer description for completion", ex);
            return null;
        }
    }

    private List<CompletionItem> buildImportCompletions(String text, int caretOffset, ImportsContext context) {
        ClientSettings settings = server.getClientSettings();
        InterlisLanguageLevel languageLevel = InterlisLanguageLevel.detect(text);
        Set<String> already = new LinkedHashSet<>();
        for (String existing : context.already()) {
            if (existing != null) {
                already.add(existing.toUpperCase(Locale.ROOT));
            }
        }

        List<String> candidates = modelDiscoveryService.searchModels(settings, context.prefix(), already, languageLevel);
        List<CompletionItem> items = new ArrayList<>();
        Range range = new Range(DocumentTracker.positionAt(text, context.prefixStartOffset()),
                DocumentTracker.positionAt(text, caretOffset));
        for (String candidate : candidates) {
            items.add(item(candidate, CompletionItemKind.Module, range, PRIORITY_IMPORTED, context.prefix()));
        }
        return dedupe(items);
    }

    private CompletionItem item(String label,
                                CompletionItemKind kind,
                                Range range,
                                int priority,
                                String prefix) {
        CompletionItem item = new CompletionItem(label);
        item.setKind(kind);
        item.setSortText(sortText(priority, label, prefix, label));
        if (range != null) {
            item.setTextEdit(Either.forLeft(new TextEdit(range, label)));
        }
        return item;
    }

    private CompletionItem snippet(String label,
                                   String snippetText,
                                   Range range,
                                   int priority,
                                   String prefix,
                                   String filterText) {
        CompletionItem item = new CompletionItem(label);
        item.setKind(CompletionItemKind.Snippet);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setSortText(sortText(priority, filterText, prefix, label));
        item.setFilterText(filterText);
        if (range != null) {
            item.setTextEdit(Either.forLeft(new TextEdit(range, snippetText)));
        }
        return item;
    }

    private String sortText(int priority, String candidate, String prefix, String label) {
        boolean exact = prefix != null && !prefix.isBlank() && candidate != null && candidate.equalsIgnoreCase(prefix);
        boolean starts = startsWithIgnoreCase(candidate, prefix);
        int matchRank = exact ? 0 : (starts ? 1 : 2);
        return String.format(Locale.ROOT, "%02d_%d_%s", priority, matchRank, label.toUpperCase(Locale.ROOT));
    }

    private List<CompletionItem> dedupe(List<CompletionItem> items) {
        LinkedHashMap<String, CompletionItem> unique = new LinkedHashMap<>();
        for (CompletionItem item : items) {
            if (item == null || item.getLabel() == null) {
                continue;
            }
            unique.putIfAbsent(item.getLabel().toUpperCase(Locale.ROOT), item);
        }
        return new ArrayList<>(unique.values());
    }

    private ImportsContext findImportsContext(String text, int caretOffset) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        final int lookback = 2000;
        int start = Math.max(0, caretOffset - lookback);
        String tail = text.substring(start, caretOffset);

        Matcher matcher = IMPORTS_PATTERN.matcher(tail);
        int importsStart = -1;
        int importsEnd = -1;
        while (matcher.find()) {
            importsStart = matcher.start();
            importsEnd = matcher.end();
        }
        if (importsStart < 0 || tail.indexOf(';', importsStart) >= 0) {
            return null;
        }

        String segment = tail.substring(importsEnd);
        int lastComma = segment.lastIndexOf(',');
        int tokenRelStart = lastComma >= 0 ? lastComma + 1 : 0;
        while (tokenRelStart < segment.length() && Character.isWhitespace(segment.charAt(tokenRelStart))) {
            tokenRelStart++;
        }

        int prefixStartAbs = start + importsEnd + tokenRelStart;
        String prefix = segment.substring(tokenRelStart).trim();
        List<String> already = new ArrayList<>();
        String before = segment.substring(0, tokenRelStart).trim();
        if (!before.isEmpty()) {
            for (String name : before.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    already.add(trimmed);
                }
            }
        }
        return new ImportsContext(prefixStartAbs, prefix, already);
    }

    private static boolean isEndContext(String text, int caretOffset) {
        int lineStart = lineStartOffset(text, caretOffset);
        String prefix = text.substring(lineStart, Math.min(caretOffset, text.length()));
        return END_PATTERN.matcher(prefix).find();
    }

    private static String groupValue(Matcher matcher, int groupIndex) {
        if (matcher == null || groupIndex > matcher.groupCount()) {
            return "";
        }
        String value = matcher.group(groupIndex);
        return value != null ? value : "";
    }

    private static Range groupRange(String text,
                                    Matcher matcher,
                                    int groupIndex,
                                    int lineStartOffset,
                                    int caretOffset) {
        if (matcher == null || groupIndex > matcher.groupCount() || matcher.group(groupIndex) == null) {
            return new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset));
        }
        return new Range(
                DocumentTracker.positionAt(text, lineStartOffset + matcher.start(groupIndex)),
                DocumentTracker.positionAt(text, lineStartOffset + matcher.end(groupIndex)));
    }

    private static boolean supportsAttributeTypeContext(InterlisSymbolKind ownerKind) {
        return ownerKind == null
                || ownerKind == InterlisSymbolKind.CLASS
                || ownerKind == InterlisSymbolKind.STRUCTURE
                || ownerKind == InterlisSymbolKind.ASSOCIATION;
    }

    private static Set<InterlisSymbolKind> allowedExtendsKinds(InterlisSymbolKind ownerKind) {
        if (ownerKind == null) {
            return Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE,
                    InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW);
        }
        return switch (ownerKind) {
            case CLASS -> Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE);
            case STRUCTURE -> Set.of(InterlisSymbolKind.STRUCTURE);
            case ASSOCIATION -> Set.of(InterlisSymbolKind.ASSOCIATION);
            case VIEW -> Set.of(InterlisSymbolKind.VIEW);
            default -> Collections.emptySet();
        };
    }

    private static boolean isAllowed(InterlisSymbolKind kind, Set<InterlisSymbolKind> allowedKinds) {
        return kind != null && (allowedKinds == null || allowedKinds.isEmpty() || allowedKinds.contains(kind));
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        if (value == null) {
            return false;
        }
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        return value.toUpperCase(Locale.ROOT).startsWith(prefix.toUpperCase(Locale.ROOT));
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

    private static boolean contains(Range range, Position position) {
        if (range == null || position == null) {
            return false;
        }
        return compare(position, range.getStart()) >= 0 && compare(position, range.getEnd()) <= 0;
    }

    private static boolean isNarrower(Range candidate, Range incumbent) {
        if (candidate == null || incumbent == null) {
            return false;
        }
        int candidateLines = candidate.getEnd().getLine() - candidate.getStart().getLine();
        int incumbentLines = incumbent.getEnd().getLine() - incumbent.getStart().getLine();
        if (candidateLines != incumbentLines) {
            return candidateLines < incumbentLines;
        }
        int candidateChars = candidate.getEnd().getCharacter() - candidate.getStart().getCharacter();
        int incumbentChars = incumbent.getEnd().getCharacter() - incumbent.getStart().getCharacter();
        return candidateChars < incumbentChars;
    }

    private static int compare(Position left, Position right) {
        if (left.getLine() != right.getLine()) {
            return Integer.compare(left.getLine(), right.getLine());
        }
        return Integer.compare(left.getCharacter(), right.getCharacter());
    }

    private static int lineStartOffset(String text, int offset) {
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

    private static boolean isVisibleAt(LiveSymbol symbol, int resolutionOffset) {
        return symbol != null && symbol.id() != null && symbol.id().startOffset() < resolutionOffset;
    }

    private List<String> attributeRootKeywords(LiveParseResult live, CompletionContext context) {
        List<String> keywords = new ArrayList<>(ATTRIBUTE_ROOT_BASE_KEYWORDS);
        if (hasMandatoryPrefix(live, context)) {
            keywords.remove("MANDATORY");
        }
        InterlisLanguageLevel level = live != null ? live.languageLevel() : InterlisLanguageLevel.UNKNOWN;
        if (level.supportsNativeDateTypes()) {
            keywords.addAll(NATIVE_DATE_TIME_KEYWORDS);
        }
        return keywords;
    }

    private void addPortableDateTimeSuggestions(List<CompletionItem> items,
                                                CompletionContext context,
                                                InterlisLanguageLevel languageLevel) {
        for (String typeName : PORTABLE_DATE_TIME_TYPES) {
            if (startsWithIgnoreCase(typeName, context.prefix())) {
                int priority = languageLevel.supportsNativeDateTypes() ? PRIORITY_KEYWORD : PRIORITY_IMPORTED;
                items.add(item(typeName, CompletionItemKind.Keyword, context.replaceRange(), priority, context.prefix()));
            }
        }
    }

    private boolean hasMandatoryPrefix(LiveParseResult live, CompletionContext context) {
        if (live == null || context == null || context.replaceRange() == null || live.snapshot() == null) {
            return false;
        }
        String text = live.snapshot().text();
        int caretOffset = DocumentTracker.toOffset(text, context.replaceRange().getStart());
        int lineStart = lineStartOffset(text, caretOffset);
        String segment = text.substring(lineStart, Math.min(caretOffset, text.length()));
        int colonIndex = segment.lastIndexOf(':');
        if (colonIndex < 0) {
            return false;
        }
        return segment.substring(colonIndex + 1).trim().toUpperCase(Locale.ROOT).startsWith("MANDATORY");
    }

    private static Object resolveChain(TransferDescription td, String[] parts, int stop) {
        if (td == null || parts == null || stop <= 0) {
            return null;
        }
        ch.interlis.ili2c.metamodel.Model model = InterlisAstUtil.resolveModel(td, parts[0]);
        if (model == null) {
            return null;
        }
        Object current = model;
        for (int i = 1; i < stop; i++) {
            String name = parts[i];
            if (name == null || name.isEmpty()) {
                return null;
            }
            if (current instanceof ch.interlis.ili2c.metamodel.Model currentModel) {
                Object child = InterlisAstUtil.findChildInModelByName(currentModel, name);
                if (child == null) {
                    return null;
                }
                current = child;
            } else if (current instanceof ch.interlis.ili2c.metamodel.Container<?> container) {
                Object child = InterlisAstUtil.findChildInContainerByName(container, name);
                if (child == null) {
                    return null;
                }
                current = child;
            } else {
                return null;
            }
        }
        return current;
    }

    private record ImportsContext(int prefixStartOffset, String prefix, List<String> already) {
    }
}
