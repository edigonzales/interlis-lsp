package ch.so.agi.lsp.interlis.text;

import ch.interlis.ili2c.metamodel.ClassType;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
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
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
    private static final String DOTTED_NAME_REGEX = "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*";
    private static final String DOTTED_NAME_PREFIX_REGEX = DOTTED_NAME_REGEX + "\\.?";
    private static final String DOMAIN_DECLARATION_PREFIX_REGEX =
            "(?i)^\\s*DOMAIN\\s+(?:[A-Za-z_][A-Za-z0-9_]*|UUIDOID)"
                    + "(?:\\s*\\(\\s*(?:ABSTRACT|FINAL|GENERIC)\\s*\\))?"
                    + "(?:\\s+EXTENDS\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)?\\s*=\\s*";
    private static final String UNIT_DECLARATION_PREFIX_REGEX =
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*"
                    + "(?:\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\])?"
                    + "(?:\\s*\\(\\s*ABSTRACT\\s*\\))?"
                    + "(?:\\s+EXTENDS\\s+" + DOTTED_NAME_REGEX + ")?\\s*=\\s*";
    private static final Pattern DOMAIN_RHS_PLAUSIBLE_PATTERN = Pattern.compile("(?i)^\\s*DOMAIN\\b.*=\\s*.*$");
    private static final Pattern UNIT_RHS_PLAUSIBLE_PATTERN = Pattern.compile("(?i)^\\s*UNIT\\b.*=\\s*.*$");
    private static final Pattern DOMAIN_ALL_OF_TARGET_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "ALL\\s+OF\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*;?\\s*$");
    private static final Pattern DOMAIN_META_TAIL_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(CLASS|STRUCTURE|ATTRIBUTE)\\s*([A-Za-z_]*)\\s*;?\\s*$");
    private static final Pattern DOMAIN_TEXT_LENGTH_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?(TEXT|MTEXT)\\s*;?\\s*$");
    private static final Pattern DOMAIN_TEXT_LENGTH_VALUE_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?(?:TEXT|MTEXT)\\s*\\*\\s*;?\\s*$");
    private static final Pattern DOMAIN_INLINE_NUMERIC_RANGE_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?([-+]?[0-9]+(?:\\.[0-9]+)?)\\s*;?\\s*$");
    private static final Pattern DOMAIN_INLINE_NUMERIC_UPPER_BOUND_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?([-+]?[0-9]+(?:\\.[0-9]+)?)\\s*\\.\\.\\s*;?\\s*$");
    private static final Pattern DOMAIN_FORMAT_TYPE_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?FORMAT\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*;?\\s*$");
    private static final Pattern DOMAIN_FORMAT_BOUNDS_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?FORMAT\\s+(INTERLIS\\.(?:XMLDate|XMLDateTime|XMLTime))\\s*;?\\s*$");
    private static final Pattern DOMAIN_ROOT_PATTERN = Pattern.compile(
            DOMAIN_DECLARATION_PREFIX_REGEX + "(?:MANDATORY\\s+)?([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*;?\\s*$");
    private static final Pattern UNIT_ROOT_PATTERN = Pattern.compile(
            UNIT_DECLARATION_PREFIX_REGEX + "([0-9]*)\\s*;?\\s*$");
    private static final Pattern UNIT_BRACKET_TARGET_PATTERN = Pattern.compile(
            UNIT_DECLARATION_PREFIX_REGEX + "\\[\\s*(" + DOTTED_NAME_PREFIX_REGEX + ")?\\s*;?\\s*$");
    private static final Pattern UNIT_COMPOSED_TARGET_PATTERN = Pattern.compile(
            UNIT_DECLARATION_PREFIX_REGEX + "\\((?:[^;)]*(?:\\*\\*|\\*|/)\\s*)?(" + DOTTED_NAME_PREFIX_REGEX + ")?\\s*;?\\s*$");
    private static final Pattern UNIT_COMPOSED_OPERATOR_PATTERN = Pattern.compile(
            UNIT_DECLARATION_PREFIX_REGEX + "\\([^;)]*" + DOTTED_NAME_REGEX + "\\s*;?\\s*$");
    private static final Pattern CONTAINER_BODY_ROOT_PATTERN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)?\\s*$");
    private static final Pattern HEADER_AFTER_NAME_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+$");
    private static final Pattern BLOCK_HEADER_AFTER_NAME_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_]*)\\s*(=)\\s*;?\\s*$");
    private static final Pattern BLOCK_HEADER_AFTER_NAME_PREFIX_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_]*)$");
    private static final Pattern HEADER_MODIFIER_OPEN_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(\\s*([A-Za-z_]*)\\s*$");
    private static final Pattern BLOCK_HEADER_MODIFIER_OPEN_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+\\(\\s*([A-Za-z_]*)\\s*(=)\\s*;?\\s*$");
    private static final Pattern HEADER_MODIFIER_CLOSE_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*$");
    private static final Pattern BLOCK_HEADER_MODIFIER_CLOSE_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*(=)\\s*;?\\s*$");
    private static final Pattern HEADER_AFTER_MODIFIER_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*\\)\\s*$");
    private static final Pattern BLOCK_HEADER_AFTER_MODIFIER_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*\\)\\s+([A-Za-z_]*)\\s*(=)\\s*;?\\s*$");
    private static final Pattern BLOCK_HEADER_AFTER_MODIFIER_PREFIX_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*\\)\\s+([A-Za-z_]*)$");
    private static final Pattern HEADER_AFTER_EXTENDS_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\s*\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*\\))?\\s+EXTENDS\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\s+$");
    private static final Pattern BLOCK_HEADER_EXTENDS_TARGET_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\s+\\(\\s*(ABSTRACT|EXTENDED|FINAL|GENERIC)\\s*\\))?\\s+EXTENDS\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.?)?\\s*(=)\\s*;?\\s*$");
    private static final Pattern UNIT_HEADER_AFTER_NAME_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\]\\s+$");
    private static final Pattern UNIT_BLOCK_HEADER_AFTER_NAME_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\]\\s+([A-Za-z_]*)$");
    private static final Pattern UNIT_HEADER_MODIFIER_OPEN_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\]\\s*\\(\\s*([A-Za-z_]*)\\s*$");
    private static final Pattern UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\]\\s*\\(\\s*(ABSTRACT)\\s*$");
    private static final Pattern UNIT_HEADER_AFTER_MODIFIER_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\]\\s*\\(\\s*(ABSTRACT)\\s*\\)\\s+$");
    private static final Pattern UNIT_BLOCK_HEADER_AFTER_MODIFIER_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\]\\s*\\(\\s*(ABSTRACT)\\s*\\)\\s+([A-Za-z_]*)$");
    private static final Pattern UNIT_HEADER_AFTER_EXTENDS_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\](?:\\s*\\(\\s*(ABSTRACT)\\s*\\))?\\s+EXTENDS\\s+"
                    + DOTTED_NAME_REGEX + "\\s+$");
    private static final Pattern UNIT_BLOCK_HEADER_EXTENDS_TARGET_WITH_ABBR_PATTERN = Pattern.compile(
            "(?i)^\\s*UNIT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\[\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\](?:\\s*\\(\\s*(ABSTRACT)\\s*\\))?\\s+EXTENDS\\s*("
                    + DOTTED_NAME_PREFIX_REGEX + ")?$");
    private static final Pattern METAATTRIBUTE_ROOT_PATTERN = Pattern.compile("^\\s*!!@\\s*([A-Za-z0-9_\\.]*)$");
    private static final Pattern METAATTRIBUTE_VALUE_PATTERN = Pattern.compile(
            "^\\s*!!@\\s*([A-Za-z0-9_\\.]+)\\s*=\\s*(.*)$");
    private static final Pattern TARGET_DECLARATION_NAME_PATTERN = Pattern.compile(
            "(?i)^(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT|ASSOCIATION|VIEW|GRAPHIC|FUNCTION|CONTEXT)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern TARGET_LEADING_IDENTIFIER_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern TARGET_CONSTRAINT_PATTERN = Pattern.compile(
            "(?i)^(MANDATORY\\s+CONSTRAINT|CONSTRAINT|EXISTENCE\\s+CONSTRAINT|SET\\s+CONSTRAINT)\\b");
    private static final Pattern TARGET_ATTRIBUTE_DECLARATION_PATTERN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(.*)$");
    private static final Pattern TARGET_ATTRIBUTE_DIRECT_TYPE_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:MANDATORY\\s+)?([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\b");
    private static final Pattern TARGET_ENUM_ELEMENT_PATTERN = Pattern.compile(
            "^(?:[A-Za-z_][A-Za-z0-9_]*|LOCAL|BASKET)(?:\\.[A-Za-z_][A-Za-z0-9_]*)*(?:\\s*\\(|\\s*,|\\s*$).*");
    private static final List<String> METAATTRIBUTE_ILI2DB_STRUCTURE_MAPPINGS = List.of(
            "MultiSurface", "MultiLine", "MultiPoint", "Multilingual", "Localised");
    private static final List<String> METAATTRIBUTE_ILI2DB_ATTRIBUTE_MAPPINGS = List.of(
            "ARRAY", "JSON", "EXPAND");
    private static final List<String> METAATTRIBUTE_SEVERITIES = List.of("on", "warning", "off");

    private enum MetaAttributeTargetProfile {
        CLASS_DEF,
        STRUCTURE_DEF,
        ATTRIBUTE_ANY,
        ATTRIBUTE_STRUCTLIKE,
        ATTRIBUTE_REFERENCE_OR_STRUCTLIKE,
        ROLE_DEF,
        CONSTRAINT_DEF,
        ENUM_ELEMENT
    }

    private record MetaAttributeTarget(Set<MetaAttributeTargetProfile> profiles) {
        static MetaAttributeTarget none() {
            return new MetaAttributeTarget(Set.of());
        }

        boolean has(MetaAttributeTargetProfile profile) {
            return profiles != null && profiles.contains(profile);
        }

        boolean isResolved() {
            return profiles != null && !profiles.isEmpty();
        }
    }

    private static final Set<InterlisSymbolKind> ATTRIBUTE_TYPE_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.DOMAIN, InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> DOMAIN_REFERENCE_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.DOMAIN);
    private static final Set<InterlisSymbolKind> UNIT_REFERENCE_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.UNIT);
    private static final Set<InterlisSymbolKind> COLLECTION_TARGET_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> REFERENCE_TARGET_SYMBOL_KINDS =
            Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW);

    private static final List<String> ATTRIBUTE_ROOT_BASE_KEYWORDS = List.of(
            "MANDATORY", "FORMAT", "TEXT", "MTEXT", "NAME", "URI", "BOOLEAN", "NUMERIC",
            "UUIDOID", "COORD", "MULTICOORD", "POLYLINE", "MULTIPOLYLINE",
            "AREA", "MULTIAREA", "SURFACE", "MULTISURFACE", "REFERENCE", "BAG", "LIST");
    private static final List<String> DOMAIN_ROOT_BASE_KEYWORDS = List.of(
            "MANDATORY", "FORMAT", "TEXT", "MTEXT", "NAME", "URI", "BOOLEAN", "NUMERIC",
            "UUIDOID", "OID", "COORD", "MULTICOORD", "POLYLINE", "MULTIPOLYLINE",
            "AREA", "MULTIAREA", "SURFACE", "MULTISURFACE", "BLACKBOX",
            "CLASS", "STRUCTURE", "ATTRIBUTE", "ALL OF");
    private static final List<String> NATIVE_DATE_TIME_KEYWORDS = List.of("DATE", "TIMEOFDAY", "DATETIME");
    private static final List<String> ATTRIBUTE_META_KEYWORDS = List.of(
            "CLASS", "STRUCTURE", "ATTRIBUTE", "ANYSTRUCTURE");
    private static final List<String> PORTABLE_DATE_TIME_TYPES = List.of(
            "INTERLIS.XMLDate", "INTERLIS.XMLDateTime", "INTERLIS.XMLTime");
    private static final List<String> TOPIC_BODY_KEYWORDS = List.of(
            "CLASS",
            "STRUCTURE",
            "ASSOCIATION",
            "VIEW",
            "GRAPHIC",
            "DOMAIN",
            "UNIT",
            "FUNCTION",
            "CONTEXT",
            "CONSTRAINTS",
            "SIGN BASKET",
            "REFSYSTEM BASKET");
    private static final List<String> MODEL_BODY_KEYWORDS = List.of(
            "TOPIC",
            "CLASS",
            "STRUCTURE",
            "DOMAIN",
            "UNIT",
            "FUNCTION",
            "CONTEXT",
            "LINE FORM");

    private static final int PRIORITY_TOKEN = 5;
    private static final int PRIORITY_LOCAL = 10;
    private static final int PRIORITY_IMPORTED = 20;
    private static final int PRIORITY_TOPIC_BODY_KEYWORD = 25;
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
                case CONTAINER_BODY_ROOT -> completeContainerBodyRoot(context, live);
                case DECLARATION_HEADER_AFTER_NAME -> completeDeclarationHeaderAfterName(context);
                case DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_NAME -> completeDeclarationHeaderBlockSuffixAfterName(context);
                case DECLARATION_HEADER_MODIFIER_VALUE -> completeDeclarationHeaderModifierValue(context);
                case DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_VALUE -> completeDeclarationHeaderBlockSuffixModifierValue(context);
                case DECLARATION_HEADER_MODIFIER_CLOSE -> completeDeclarationHeaderModifierClose(context);
                case DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_CLOSE -> completeDeclarationHeaderBlockSuffixModifierClose(context);
                case DECLARATION_HEADER_AFTER_MODIFIER -> completeDeclarationHeaderAfterModifier(context);
                case DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_MODIFIER -> completeDeclarationHeaderBlockSuffixAfterModifier(context);
                case DECLARATION_HEADER_AFTER_EXTENDS -> completeDeclarationHeaderAfterExtends(context);
                case DECLARATION_HEADER_BLOCK_SUFFIX_EXTENDS_TARGET -> completeDeclarationHeaderBlockSuffixExtendsTarget(context, live, td, offset);
                case ATTRIBUTE_TYPE_ROOT -> completeAttributeTypeRoot(context, live, td, offset);
                case DOMAIN_TYPE_ROOT -> completeDomainTypeRoot(context, live);
                case UNIT_TYPE_ROOT -> completeUnitTypeRoot(context);
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
                case METAATTRIBUTE_ROOT -> completeMetaAttributeRoot(context, text, offset, live, td);
                case METAATTRIBUTE_VALUE -> completeMetaAttributeValue(context, text, offset, live, td);
                case UNIT_BRACKET_TARGET -> completeUnitBracketTargets(context, live, td, offset);
                case UNIT_COMPOSED_TARGET -> completeUnitComposedTargets(context, live, td, offset);
                case UNIT_COMPOSED_OPERATOR -> completeUnitComposedOperators(context);
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

    private List<CompletionItem> completeContainerBodyRoot(CompletionContext context, LiveParseResult live) {
        List<CompletionItem> items = new ArrayList<>();
        if (context.ownerKind() == InterlisSymbolKind.TOPIC) {
            addKeywords(items, TOPIC_BODY_KEYWORDS, context, PRIORITY_TOPIC_BODY_KEYWORD);
            addTopicBodySnippets(items, context, live);
            return items;
        }
        if (context.ownerKind() == InterlisSymbolKind.MODEL) {
            addKeywords(items, MODEL_BODY_KEYWORDS, context, PRIORITY_TOPIC_BODY_KEYWORD);
            addModelBodySnippets(items, context, live);
            return items;
        }
        return items;
    }

    private List<CompletionItem> completeDeclarationHeaderAfterName(CompletionContext context) {
        return completeDeclarationHeaderAfterName(context, false);
    }

    private List<CompletionItem> completeDeclarationHeaderBlockSuffixAfterName(CompletionContext context) {
        return completeDeclarationHeaderAfterName(context, true);
    }

    private List<CompletionItem> completeDeclarationHeaderModifierValue(CompletionContext context) {
        return completeDeclarationHeaderModifierValue(context, false);
    }

    private List<CompletionItem> completeDeclarationHeaderBlockSuffixModifierValue(CompletionContext context) {
        return completeDeclarationHeaderModifierValue(context, true);
    }

    private List<CompletionItem> completeDeclarationHeaderModifierClose(CompletionContext context) {
        return completeDeclarationHeaderModifierClose(context, false);
    }

    private List<CompletionItem> completeDeclarationHeaderBlockSuffixModifierClose(CompletionContext context) {
        return completeDeclarationHeaderModifierClose(context, true);
    }

    private List<CompletionItem> completeDeclarationHeaderAfterModifier(CompletionContext context) {
        return completeDeclarationHeaderAfterModifier(context, false);
    }

    private List<CompletionItem> completeDeclarationHeaderBlockSuffixAfterModifier(CompletionContext context) {
        return completeDeclarationHeaderAfterModifier(context, true);
    }

    private List<CompletionItem> completeDeclarationHeaderAfterExtends(CompletionContext context) {
        return List.of(keywordWithInsertText("=", "=", context.replaceRange(), PRIORITY_TOKEN, context.prefix()));
    }

    private List<CompletionItem> completeDeclarationHeaderBlockSuffixExtendsTarget(CompletionContext context,
                                                                                   LiveParseResult live,
                                                                                   TransferDescription td,
                                                                                   int resolutionOffset) {
        return appendTrailingSpace(completeExtendsTargets(context, live, td, resolutionOffset));
    }

    private List<CompletionItem> completeDeclarationHeaderAfterName(CompletionContext context, boolean fixedEqualsSuffix) {
        List<CompletionItem> items = new ArrayList<>();
        addUnitHeaderAbbreviationVariant(items, context, fixedEqualsSuffix);
        addHeaderModifierVariants(items, context, fixedEqualsSuffix);
        addHeaderContinuation(items, "EXTENDS", headerExtendsInsertText(context, fixedEqualsSuffix), PRIORITY_TOKEN, context);
        if (!fixedEqualsSuffix) {
            addHeaderContinuation(items, "=", headerEqualsInsertText(context, false), PRIORITY_TOKEN, context);
        }
        return items;
    }

    private List<CompletionItem> completeDeclarationHeaderModifierValue(CompletionContext context, boolean fixedEqualsSuffix) {
        List<CompletionItem> items = new ArrayList<>();
        for (String modifier : allowedHeaderModifiers(context.ownerKind())) {
            addHeaderModifierValue(items, modifier, context, fixedEqualsSuffix);
        }
        return items;
    }

    private List<CompletionItem> completeDeclarationHeaderModifierClose(CompletionContext context, boolean fixedEqualsSuffix) {
        return List.of(keywordWithInsertText(
                ")",
                headerModifierCloseInsertText(context, fixedEqualsSuffix),
                context.replaceRange(),
                PRIORITY_TOKEN,
                context.prefix()));
    }

    private List<CompletionItem> completeDeclarationHeaderAfterModifier(CompletionContext context, boolean fixedEqualsSuffix) {
        List<CompletionItem> items = new ArrayList<>();
        addHeaderContinuation(items, "EXTENDS", headerExtendsInsertText(context, fixedEqualsSuffix), PRIORITY_TOKEN, context);
        if (!fixedEqualsSuffix) {
            addHeaderContinuation(items, "=", headerEqualsInsertText(context, false), PRIORITY_TOKEN, context);
        }
        return items;
    }

    private List<CompletionItem> completeAttributeTypeRoot(CompletionContext context,
                                                           LiveParseResult live,
                                                           TransferDescription td,
                                                           int resolutionOffset) {
        if (context.ownerKind() == InterlisSymbolKind.DOMAIN) {
            return completeDomainTypeRoot(context, live);
        }
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

    private List<CompletionItem> completeDomainTypeRoot(CompletionContext context,
                                                        LiveParseResult live) {
        if (context.qualifierPath() != null && !context.qualifierPath().isBlank()) {
            return completeDomainTypeRootQualifiedMembers(context, live);
        }
        List<CompletionItem> items = new ArrayList<>();
        addKeywords(items, domainRootKeywords(live, context), context, PRIORITY_KEYWORD);
        addPortableDateTimeSuggestions(items, context, live != null ? live.languageLevel() : InterlisLanguageLevel.UNKNOWN);
        addDomainRootSnippets(items, context);
        return items;
    }

    private List<CompletionItem> completeUnitTypeRoot(CompletionContext context) {
        List<CompletionItem> items = new ArrayList<>();
        addUnitRootSnippets(items, context);
        return items;
    }

    private List<CompletionItem> completeUnitBracketTargets(CompletionContext context,
                                                            LiveParseResult live,
                                                            TransferDescription td,
                                                            int resolutionOffset) {
        return completeUnitReferenceTargets(context, live, td, resolutionOffset);
    }

    private List<CompletionItem> completeUnitComposedTargets(CompletionContext context,
                                                             LiveParseResult live,
                                                             TransferDescription td,
                                                             int resolutionOffset) {
        return appendTrailingSpace(completeUnitReferenceTargets(context, live, td, resolutionOffset));
    }

    private List<CompletionItem> completeUnitReferenceTargets(CompletionContext context,
                                                              LiveParseResult live,
                                                              TransferDescription td,
                                                              int resolutionOffset) {
        return completeExtendsTargets(context, live, td, resolutionOffset);
    }

    private List<CompletionItem> completeUnitComposedOperators(CompletionContext context) {
        return List.of(
                keywordWithInsertText("*", "* ", context.replaceRange(), PRIORITY_TOKEN, context.prefix()),
                keywordWithInsertText("/", "/ ", context.replaceRange(), PRIORITY_TOKEN, context.prefix()),
                keywordWithInsertText("**", "** ", context.replaceRange(), PRIORITY_TOKEN, context.prefix()),
                keywordWithInsertText(")", ")", context.replaceRange(), PRIORITY_TOKEN, context.prefix()));
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

    private List<CompletionItem> completeDomainTypeRootQualifiedMembers(CompletionContext context,
                                                                        LiveParseResult live) {
        String qualifierPath = context.qualifierPath();
        if (qualifierPath != null && "INTERLIS".equalsIgnoreCase(qualifierPath)) {
            return completeQualifiedMembers(context, live, null, 0);
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

    private List<CompletionItem> completeMetaAttributeRoot(CompletionContext context,
                                                           String text,
                                                           int caretOffset,
                                                           LiveParseResult live,
                                                           TransferDescription td) {
        MetaAttributeTarget target = detectMetaAttributeTarget(text, caretOffset, live, td);
        if (!target.isResolved()) {
            return Collections.emptyList();
        }

        List<CompletionItem> items = new ArrayList<>();
        if (target.has(MetaAttributeTargetProfile.STRUCTURE_DEF)) {
            for (String value : METAATTRIBUTE_ILI2DB_STRUCTURE_MAPPINGS) {
                addSnippet(items, "ili2db.mapping=" + value, "ili2db.mapping=" + value, context, PRIORITY_SNIPPET, "ili2db.mapping=" + value);
            }
            addSnippet(items, "ili2db.dispName=\"...\"", "ili2db.dispName=\"${1:Text}\"", context, PRIORITY_SNIPPET, "ili2db.dispName=\"...\"");
        }
        if (target.has(MetaAttributeTargetProfile.CLASS_DEF)) {
            addSnippet(items, "ili2db.dispName=\"...\"", "ili2db.dispName=\"${1:Text}\"", context, PRIORITY_SNIPPET, "ili2db.dispName=\"...\"");
            addSnippet(items, "ili2db.oid=INTERLIS.UUIDOID", "ili2db.oid=${1:INTERLIS.UUIDOID}", context, PRIORITY_SNIPPET, "ili2db.oid=INTERLIS.UUIDOID");
            addSnippet(items, "ilivalid.keymsg=\"...\"", "ilivalid.keymsg=\"${1:Text}\"", context, PRIORITY_SNIPPET, "ilivalid.keymsg=\"...\"");
            addSnippet(items, "ilivalid.keymsg_<lang>=\"...\"", "ilivalid.keymsg_${1:de}=\"${2:Text}\"", context, PRIORITY_SNIPPET, "ilivalid.keymsg_<lang>=\"...\"");
        }
        if (target.has(MetaAttributeTargetProfile.ATTRIBUTE_ANY)) {
            addSnippet(items, "ili2db.dispName=\"...\"", "ili2db.dispName=\"${1:Text}\"", context, PRIORITY_SNIPPET, "ili2db.dispName=\"...\"");
            addMetaAttributeSeverityAssignmentSnippets(items, "ilivalid.type", context);
            addMetaAttributeSeverityAssignmentSnippets(items, "ilivalid.multiplicity", context);
        }
        if (target.has(MetaAttributeTargetProfile.ATTRIBUTE_STRUCTLIKE)) {
            for (String value : METAATTRIBUTE_ILI2DB_ATTRIBUTE_MAPPINGS) {
                addSnippet(items, "ili2db.mapping=" + value, "ili2db.mapping=" + value, context, PRIORITY_SNIPPET, "ili2db.mapping=" + value);
            }
        }
        if (target.has(MetaAttributeTargetProfile.ATTRIBUTE_REFERENCE_OR_STRUCTLIKE)) {
            addSnippet(items, "ilivalid.requiredIn=bid1", "ilivalid.requiredIn=${1:bid1}", context, PRIORITY_SNIPPET, "ilivalid.requiredIn=bid1");
        }
        if (target.has(MetaAttributeTargetProfile.ROLE_DEF)) {
            addMetaAttributeSeverityAssignmentSnippets(items, "ilivalid.target", context);
            addMetaAttributeSeverityAssignmentSnippets(items, "ilivalid.multiplicity", context);
            addSnippet(items, "ilivalid.requiredIn=bid1", "ilivalid.requiredIn=${1:bid1}", context, PRIORITY_SNIPPET, "ilivalid.requiredIn=bid1");
        }
        if (target.has(MetaAttributeTargetProfile.CONSTRAINT_DEF)) {
            addMetaAttributeSeverityAssignmentSnippets(items, "ilivalid.check", context);
            addSnippet(items, "category=...", "category=${1:category}", context, PRIORITY_SNIPPET, "category=...");
            addSnippet(items, "ilivalid.msg=\"...\"", "ilivalid.msg=\"${1:Text}\"", context, PRIORITY_SNIPPET, "ilivalid.msg=\"...\"");
            addSnippet(items, "ilivalid.msg_<lang>=\"...\"", "ilivalid.msg_${1:de}=\"${2:Text}\"", context, PRIORITY_SNIPPET, "ilivalid.msg_<lang>=\"...\"");
            addSnippet(items, "message=\"...\"", "message=\"${1:Text}\"", context, PRIORITY_SNIPPET, "message=\"...\"");
            addSnippet(items, "message_<lang>=\"...\"", "message_${1:de}=\"${2:Text}\"", context, PRIORITY_SNIPPET, "message_<lang>=\"...\"");
            addSnippet(items, "name=c1023", "name=${1:c1023}", context, PRIORITY_SNIPPET, "name=c1023");
        }
        if (target.has(MetaAttributeTargetProfile.ENUM_ELEMENT)) {
            addSnippet(items, "ili2db.dispName=\"...\"", "ili2db.dispName=\"${1:Text}\"", context, PRIORITY_SNIPPET, "ili2db.dispName=\"...\"");
        }
        return items;
    }

    private List<CompletionItem> completeMetaAttributeValue(CompletionContext context,
                                                            String text,
                                                            int caretOffset,
                                                            LiveParseResult live,
                                                            TransferDescription td) {
        MetaAttributeTarget target = detectMetaAttributeTarget(text, caretOffset, live, td);
        if (!target.isResolved()) {
            return Collections.emptyList();
        }

        String metaAttribute = context.subject() != null ? context.subject().trim() : "";
        if (metaAttribute.isBlank()) {
            return Collections.emptyList();
        }

        List<CompletionItem> items = new ArrayList<>();
        if ("ili2db.mapping".equalsIgnoreCase(metaAttribute)) {
            if (target.has(MetaAttributeTargetProfile.STRUCTURE_DEF)) {
                for (String value : METAATTRIBUTE_ILI2DB_STRUCTURE_MAPPINGS) {
                    addKeyword(items, value, context, PRIORITY_TOKEN);
                }
            }
            if (target.has(MetaAttributeTargetProfile.ATTRIBUTE_STRUCTLIKE)) {
                for (String value : METAATTRIBUTE_ILI2DB_ATTRIBUTE_MAPPINGS) {
                    addKeyword(items, value, context, PRIORITY_TOKEN);
                }
            }
            return items;
        }
        if (isMetaAttributeSeverityKey(metaAttribute)) {
            addMetaAttributeSeverityValues(items, context);
            return items;
        }
        if (isQuotedMetaAttributeKey(metaAttribute)) {
            addSnippet(items, "\"...\"", "\"${1:Text}\"", context, PRIORITY_SNIPPET, "\"");
            return items;
        }
        if ("ili2db.oid".equalsIgnoreCase(metaAttribute)) {
            addSnippet(items, "INTERLIS.UUIDOID", "${1:INTERLIS.UUIDOID}", context, PRIORITY_SNIPPET, "INTERLIS.UUIDOID");
            return items;
        }
        if ("ilivalid.requiredIn".equalsIgnoreCase(metaAttribute)) {
            addSnippet(items, "bid1", "${1:bid1}", context, PRIORITY_SNIPPET, "bid1");
            return items;
        }
        if ("category".equalsIgnoreCase(metaAttribute)) {
            addSnippet(items, "category", "${1:category}", context, PRIORITY_SNIPPET, "category");
            return items;
        }
        if ("name".equalsIgnoreCase(metaAttribute)) {
            addSnippet(items, "c1023", "${1:c1023}", context, PRIORITY_SNIPPET, "c1023");
            return items;
        }
        return Collections.emptyList();
    }

    private void addMetaAttributeSeverityAssignmentSnippets(List<CompletionItem> items,
                                                            String metaAttributeName,
                                                            CompletionContext context) {
        for (String severity : METAATTRIBUTE_SEVERITIES) {
            addSnippet(items,
                    metaAttributeName + "=" + severity,
                    metaAttributeName + "=" + severity,
                    context,
                    PRIORITY_SNIPPET,
                    metaAttributeName + "=" + severity);
        }
    }

    private void addMetaAttributeSeverityValues(List<CompletionItem> items, CompletionContext context) {
        for (String severity : METAATTRIBUTE_SEVERITIES) {
            addKeyword(items, severity, context, PRIORITY_TOKEN);
        }
    }

    private List<CompletionItem> appendTrailingSpace(List<CompletionItem> items) {
        for (CompletionItem item : items) {
            if (item == null || item.getTextEdit() == null || !item.getTextEdit().isLeft()) {
                continue;
            }
            TextEdit edit = item.getTextEdit().getLeft();
            item.setTextEdit(Either.forLeft(new TextEdit(edit.getRange(), edit.getNewText() + " ")));
        }
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
                || context.kind() == CompletionContext.Kind.DOMAIN_TYPE_ROOT
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
                && context.kind() != CompletionContext.Kind.DOMAIN_TYPE_ROOT
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

    private void addTopicBodySnippets(List<CompletionItem> items,
                                      CompletionContext context,
                                      LiveParseResult live) {
        String baseIndent = topicBodyIndentation(live, context);
        String childIndent = baseIndent + "  ";
        String firstLineIndent = topicBodySnippetFirstLineIndent(live, context, baseIndent);
        addNamedBlockSnippet(items, "CLASS Name = ... END Name;", "CLASS", context, firstLineIndent, baseIndent, childIndent);
        addNamedBlockSnippet(items, "STRUCTURE Name = ... END Name;", "STRUCTURE", context, firstLineIndent, baseIndent, childIndent);
        addNamedBlockSnippet(items, "ASSOCIATION Name = ... END Name;", "ASSOCIATION", context, firstLineIndent, baseIndent, childIndent);
        addNamedBlockSnippet(items, "VIEW Name = ... END Name;", "VIEW", context, firstLineIndent, baseIndent, childIndent);
        addNamedBlockSnippet(items, "GRAPHIC Name = ... END Name;", "GRAPHIC", context, firstLineIndent, baseIndent, childIndent);
        addSnippet(items, "DOMAIN Name = ...;",
                firstLineIndent + "DOMAIN ${1:Name} ${2:}= $0;",
                context, PRIORITY_SNIPPET, "DOMAIN");
        addSnippet(items, "UNIT Name = ...;",
                firstLineIndent + "UNIT ${1:Name} ${2:}= $0;",
                context, PRIORITY_SNIPPET, "UNIT");
        addSnippet(items, "CONTEXT Name = ...;",
                firstLineIndent + "CONTEXT ${1:Name} = ${2};",
                context, PRIORITY_SNIPPET, "CONTEXT");
        addSnippet(items, "CONSTRAINTS OF ... = ... END;",
                firstLineIndent + "CONSTRAINTS OF ${1:Class} =\n" + childIndent + "$0\n" + baseIndent + "END;",
                context, PRIORITY_SNIPPET, "CONSTRAINTS", InsertTextMode.AsIs);
        addSnippet(items, "SIGN BASKET ...",
                firstLineIndent + "SIGN BASKET ${1:Name} ~ ${2:Topic};",
                context, PRIORITY_SNIPPET, "SIGN BASKET");
        addSnippet(items, "REFSYSTEM BASKET ...",
                firstLineIndent + "REFSYSTEM BASKET ${1:Name} ~ ${2:Topic};",
                context, PRIORITY_SNIPPET, "REFSYSTEM BASKET");
    }

    private void addModelBodySnippets(List<CompletionItem> items,
                                      CompletionContext context,
                                      LiveParseResult live) {
        String baseIndent = topicBodyIndentation(live, context);
        String childIndent = baseIndent + "  ";
        String firstLineIndent = topicBodySnippetFirstLineIndent(live, context, baseIndent);

        addNamedBlockSnippet(items, "TOPIC Name = ... END Name;", "TOPIC", context, firstLineIndent, baseIndent, childIndent);
        addNamedBlockSnippet(items, "CLASS Name = ... END Name;", "CLASS", context, firstLineIndent, baseIndent, childIndent);
        addNamedBlockSnippet(items, "STRUCTURE Name = ... END Name;", "STRUCTURE", context, firstLineIndent, baseIndent, childIndent);
        addSnippet(items, "DOMAIN Name = ...;",
                firstLineIndent + "DOMAIN ${1:Name} ${2:}= $0;",
                context, PRIORITY_SNIPPET, "DOMAIN");
        addSnippet(items, "UNIT Name = ...;",
                firstLineIndent + "UNIT ${1:Name} ${2:}= $0;",
                context, PRIORITY_SNIPPET, "UNIT");
        addSnippet(items, "CONTEXT Name = ...;",
                firstLineIndent + "CONTEXT ${1:Name} = ${2};",
                context, PRIORITY_SNIPPET, "CONTEXT");
    }

    private void addNamedBlockSnippet(List<CompletionItem> items,
                                      String label,
                                      String keyword,
                                      CompletionContext context,
                                      String firstLineIndent,
                                      String baseIndent,
                                      String childIndent) {
        String endNameMirror = "${1/^([A-Za-z_][A-Za-z0-9_]*).*$/$1/}";
        addSnippet(items, label,
                firstLineIndent + keyword + " ${1:Name} ${2:}=\n" + childIndent + "$0\n" + baseIndent + "END " + endNameMirror + ";",
                context, PRIORITY_SNIPPET, keyword, InsertTextMode.AsIs);
    }

    private void addDomainRootSnippets(List<CompletionItem> items, CompletionContext context) {
        addSnippet(items, "TEXT*<length>", "TEXT*${1:255}", context, PRIORITY_SNIPPET, "TEXT");
        addSnippet(items, "MTEXT*<length>", "MTEXT*${1:255}", context, PRIORITY_SNIPPET, "MTEXT");
        addSnippet(items, "(A, B, C)", "(${1:A}, ${2:B}, ${3:C})", context, PRIORITY_SNIPPET, "(");
        addSnippet(items, "1 .. 10", "${1:1} .. ${2:10}", context, PRIORITY_SNIPPET, "");
        addSnippet(items, "CLASS RESTRICTION (...)", "CLASS RESTRICTION (${1:Viewable})", context, PRIORITY_SNIPPET, "CLASS");
        addSnippet(items, "ALL OF BaseDomain", "ALL OF ${1:BaseDomain}", context, PRIORITY_SNIPPET, "ALL");
    }

    private void addUnitRootSnippets(List<CompletionItem> items, CompletionContext context) {
        addSnippet(items, "[BaseUnit]", "[${1:BaseUnit}]", context, PRIORITY_SNIPPET, "[");
        addSnippet(items, "1000 [BaseUnit]", "${1:1000} [${2:BaseUnit}]", context, PRIORITY_SNIPPET, "1");
        addSnippet(items, "(UnitA / UnitB)", "(${1:UnitA} / ${2})", context, PRIORITY_SNIPPET, "(");
        addSnippet(items, "(UnitA * UnitB)", "(${1:UnitA} * ${2})", context, PRIORITY_SNIPPET, "(");
    }

    private void addSnippet(List<CompletionItem> items,
                            String label,
                            String snippetText,
                            CompletionContext context,
                            int priority,
                            String filterText) {
        addSnippet(items, label, snippetText, context, priority, filterText, null);
    }

    private void addSnippet(List<CompletionItem> items,
                            String label,
                            String snippetText,
                            CompletionContext context,
                            int priority,
                            String filterText,
                            InsertTextMode insertTextMode) {
        if (!startsWithIgnoreCase(filterText, context.prefix())) {
            return;
        }
        items.add(snippet(label, snippetText, context.replaceRange(), priority, context.prefix(), filterText, insertTextMode));
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
            return true;
        }
        if (looksLikeMetaAttributeContext(lineText)) {
            return true;
        }
        if (lineText.indexOf(':') >= 0) {
            return true;
        }
        if (END_PATTERN.matcher(lineText).find()) {
            return true;
        }
        if (looksLikeDeclarationHeaderContext(lineText)) {
            return true;
        }
        if (looksLikeDomainRhsContext(lineText)) {
            return true;
        }
        if (looksLikeUnitRhsContext(lineText)) {
            return true;
        }
        if (EXTENDS_CONTEXT_PATTERN.matcher(lineText).find()) {
            return true;
        }
        if (lineText.endsWith(".")) {
            return true;
        }
        return CONTAINER_BODY_ROOT_PATTERN.matcher(lineText).matches();
    }

    private CompletionContext detectHeuristicContext(String text,
                                                     int caretOffset,
                                                     Position position,
                                                     LiveParseResult live) {
        int lineStart = lineStartOffset(text, caretOffset);
        int lineEnd = lineEndOffset(text, caretOffset);
        String lineText = text.substring(lineStart, Math.min(caretOffset, text.length()));
        String lineSuffix = text.substring(Math.min(caretOffset, text.length()), lineEnd);
        LiveSymbol scopeOwner = live != null ? live.scopeGraph().findEnclosingContainer(position) : null;
        Position lineStartPosition = DocumentTracker.positionAt(text, lineStart);
        LiveSymbol lineScopeOwner = live != null ? live.scopeGraph().findEnclosingContainer(lineStartPosition) : null;
        LiveSymbol domainScopeOwner = supportsDomainTypeContext(scopeOwner != null ? scopeOwner.kind() : null)
                ? scopeOwner
                : supportsDomainTypeContext(lineScopeOwner != null ? lineScopeOwner.kind() : null)
                ? lineScopeOwner
                : null;
        LiveSymbol unitScopeOwner = supportsUnitTypeContext(scopeOwner != null ? scopeOwner.kind() : null)
                ? scopeOwner
                : supportsUnitTypeContext(lineScopeOwner != null ? lineScopeOwner.kind() : null)
                ? lineScopeOwner
                : null;
        boolean fixedEqualsSuffix = isFixedEqualsSuffix(lineSuffix);

        CompletionContext metaAttributeContext = detectMetaAttributeContext(text, lineStart, caretOffset, lineText);
        if (metaAttributeContext != null) {
            return metaAttributeContext;
        }

        if (fixedEqualsSuffix) {
            Matcher blockExtendsMatcher = EXTENDS_CONTEXT_PATTERN.matcher(lineText);
            if (blockExtendsMatcher.find()) {
                InterlisSymbolKind declarationKind = declarationKindForExtendsLine(lineText, scopeOwner != null ? scopeOwner.kind() : null);
                Set<InterlisSymbolKind> allowedKinds = allowedExtendsKinds(declarationKind);
                if (!allowedKinds.isEmpty()) {
                    return buildHeuristicPathAwareContext(
                            CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_EXTENDS_TARGET,
                            text,
                            groupValue(blockExtendsMatcher, 1),
                            groupRange(text, blockExtendsMatcher, 1, lineStart, caretOffset),
                            scopeOwner,
                            allowedKinds);
                }
            }

            CompletionContext unitBlockHeaderContext = detectUnitHeaderContextWithAbbreviation(
                    text, lineStart, caretOffset, lineText, scopeOwner, true);
            if (unitBlockHeaderContext != null) {
                return unitBlockHeaderContext;
            }

            Matcher blockAfterModifierMatcher = BLOCK_HEADER_AFTER_MODIFIER_PREFIX_PATTERN.matcher(lineText);
            if (blockAfterModifierMatcher.matches()) {
                InterlisSymbolKind declarationKind = declarationKind(blockAfterModifierMatcher.group(1));
                if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)
                        && isAllowedHeaderModifier(declarationKind, blockAfterModifierMatcher.group(2))) {
                    return new CompletionContext(
                            CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_MODIFIER,
                            groupValue(blockAfterModifierMatcher, 3),
                            lineText,
                            null,
                            groupRange(text, blockAfterModifierMatcher, 3, lineStart, caretOffset),
                            scopeOwner != null ? scopeOwner.id() : null,
                            null,
                            declarationKind);
                }
            }

            Matcher blockModifierCloseMatcher = HEADER_MODIFIER_CLOSE_PATTERN.matcher(lineText);
            if (blockModifierCloseMatcher.matches()) {
                InterlisSymbolKind declarationKind = declarationKind(blockModifierCloseMatcher.group(1));
                if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)
                        && isAllowedHeaderModifier(declarationKind, blockModifierCloseMatcher.group(2))) {
                    return new CompletionContext(
                            CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_CLOSE,
                            "",
                            lineText,
                            null,
                            new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                            scopeOwner != null ? scopeOwner.id() : null,
                            null,
                            declarationKind);
                }
            }

            Matcher blockModifierOpenMatcher = HEADER_MODIFIER_OPEN_PATTERN.matcher(lineText);
            if (blockModifierOpenMatcher.matches()) {
                InterlisSymbolKind declarationKind = declarationKind(blockModifierOpenMatcher.group(1));
                if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)) {
                    return new CompletionContext(
                            CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_VALUE,
                            groupValue(blockModifierOpenMatcher, 2),
                            lineText,
                            null,
                            groupRange(text, blockModifierOpenMatcher, 2, lineStart, caretOffset),
                            scopeOwner != null ? scopeOwner.id() : null,
                            null,
                            declarationKind);
                }
            }

            Matcher blockAfterNameMatcher = BLOCK_HEADER_AFTER_NAME_PREFIX_PATTERN.matcher(lineText);
            if (blockAfterNameMatcher.matches()) {
                InterlisSymbolKind declarationKind = declarationKind(blockAfterNameMatcher.group(1));
                if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)) {
                    return new CompletionContext(
                            CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_NAME,
                            groupValue(blockAfterNameMatcher, 3),
                            lineText,
                            null,
                            groupRange(text, blockAfterNameMatcher, 3, lineStart, caretOffset),
                            scopeOwner != null ? scopeOwner.id() : null,
                            null,
                            declarationKind);
                }
            }
        }

        Matcher afterExtendsMatcher = HEADER_AFTER_EXTENDS_PATTERN.matcher(lineText);
        if (afterExtendsMatcher.matches()) {
            InterlisSymbolKind declarationKind = declarationKind(afterExtendsMatcher.group(1));
            if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)
                    && isAllowedHeaderModifier(declarationKind, afterExtendsMatcher.group(2))) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_AFTER_EXTENDS,
                        "",
                        lineText,
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        declarationKind);
            }
        }

        Matcher extendsMatcher = EXTENDS_CONTEXT_PATTERN.matcher(lineText);
        if (extendsMatcher.find()) {
            InterlisSymbolKind extendsOwnerKind = declarationKindForExtendsLine(lineText, scopeOwner != null ? scopeOwner.kind() : null);
            Set<InterlisSymbolKind> allowedKinds = allowedExtendsKinds(extendsOwnerKind);
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

        CompletionContext unitHeaderContext = detectUnitHeaderContextWithAbbreviation(
                text, lineStart, caretOffset, lineText, scopeOwner, false);
        if (unitHeaderContext != null) {
            return unitHeaderContext;
        }

        Matcher afterModifierMatcher = HEADER_AFTER_MODIFIER_PATTERN.matcher(lineText);
        if (afterModifierMatcher.matches()) {
            InterlisSymbolKind declarationKind = declarationKind(afterModifierMatcher.group(1));
            if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)
                    && isAllowedHeaderModifier(declarationKind, afterModifierMatcher.group(2))) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_AFTER_MODIFIER,
                        "",
                        lineText,
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        declarationKind);
            }
        }

        Matcher modifierCloseMatcher = HEADER_MODIFIER_CLOSE_PATTERN.matcher(lineText);
        if (modifierCloseMatcher.matches()) {
            InterlisSymbolKind declarationKind = declarationKind(modifierCloseMatcher.group(1));
            if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)
                    && isAllowedHeaderModifier(declarationKind, modifierCloseMatcher.group(2))) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_MODIFIER_CLOSE,
                        "",
                        lineText,
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        declarationKind);
            }
        }

        Matcher modifierOpenMatcher = HEADER_MODIFIER_OPEN_PATTERN.matcher(lineText);
        if (modifierOpenMatcher.matches()) {
            InterlisSymbolKind declarationKind = declarationKind(modifierOpenMatcher.group(1));
            if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_MODIFIER_VALUE,
                        groupValue(modifierOpenMatcher, 2),
                        lineText,
                        null,
                        groupRange(text, modifierOpenMatcher, 2, lineStart, caretOffset),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        declarationKind);
            }
        }

        Matcher afterNameMatcher = HEADER_AFTER_NAME_PATTERN.matcher(lineText);
        if (afterNameMatcher.matches()) {
            InterlisSymbolKind declarationKind = declarationKind(afterNameMatcher.group(1));
            if (supportsDeclarationHeaderContext(scopeOwner != null ? scopeOwner.kind() : null, declarationKind)) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_AFTER_NAME,
                        "",
                        lineText,
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        declarationKind);
            }
        }

        if (domainScopeOwner != null) {
            Matcher domainAllOfMatcher = DOMAIN_ALL_OF_TARGET_PATTERN.matcher(lineText);
            if (domainAllOfMatcher.matches()) {
                return buildHeuristicPathAwareContext(
                        CompletionContext.Kind.EXTENDS_TARGET,
                        text,
                        groupValue(domainAllOfMatcher, 1),
                        groupRange(text, domainAllOfMatcher, 1, lineStart, caretOffset),
                        domainScopeOwner,
                        DOMAIN_REFERENCE_SYMBOL_KINDS);
            }

            Matcher domainFormatBoundsMatcher = DOMAIN_FORMAT_BOUNDS_PATTERN.matcher(lineText);
            if (domainFormatBoundsMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.FORMAT_BOUNDS_TAIL,
                        "",
                        groupValue(domainFormatBoundsMatcher, 1),
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        domainScopeOwner.id(),
                        null,
                        InterlisSymbolKind.DOMAIN);
            }

            Matcher domainFormatTypeMatcher = DOMAIN_FORMAT_TYPE_PATTERN.matcher(lineText);
            if (domainFormatTypeMatcher.matches()) {
                return buildHeuristicPathAwareContext(
                        CompletionContext.Kind.FORMAT_TYPE_TARGET,
                        text,
                        groupValue(domainFormatTypeMatcher, 1),
                        groupRange(text, domainFormatTypeMatcher, 1, lineStart, caretOffset),
                        domainScopeOwner,
                        Set.of(InterlisSymbolKind.DOMAIN));
            }

            Matcher domainMetaMatcher = DOMAIN_META_TAIL_PATTERN.matcher(lineText);
            if (domainMetaMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.META_TYPE_TAIL,
                        groupValue(domainMetaMatcher, 2),
                        groupValue(domainMetaMatcher, 1),
                        null,
                        groupRange(text, domainMetaMatcher, 2, lineStart, caretOffset),
                        domainScopeOwner.id(),
                        null,
                        InterlisSymbolKind.DOMAIN);
            }

            Matcher domainTextLengthMatcher = DOMAIN_TEXT_LENGTH_PATTERN.matcher(lineText);
            if (domainTextLengthMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.TEXT_LENGTH_TAIL,
                        "",
                        groupValue(domainTextLengthMatcher, 1),
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        domainScopeOwner.id(),
                        null,
                        InterlisSymbolKind.DOMAIN);
            }

            Matcher domainTextLengthValueMatcher = DOMAIN_TEXT_LENGTH_VALUE_PATTERN.matcher(lineText);
            if (domainTextLengthValueMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.TEXT_LENGTH_VALUE_TAIL,
                        "",
                        "",
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        domainScopeOwner.id(),
                        null,
                        InterlisSymbolKind.DOMAIN);
            }

            Matcher domainInlineRangeMatcher = DOMAIN_INLINE_NUMERIC_RANGE_PATTERN.matcher(lineText);
            if (domainInlineRangeMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.INLINE_NUMERIC_RANGE_TAIL,
                        "",
                        groupValue(domainInlineRangeMatcher, 1),
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        domainScopeOwner.id(),
                        null,
                        InterlisSymbolKind.DOMAIN);
            }

            Matcher domainInlineUpperMatcher = DOMAIN_INLINE_NUMERIC_UPPER_BOUND_PATTERN.matcher(lineText);
            if (domainInlineUpperMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.INLINE_NUMERIC_UPPER_BOUND_TAIL,
                        "",
                        groupValue(domainInlineUpperMatcher, 1),
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        domainScopeOwner.id(),
                        null,
                        InterlisSymbolKind.DOMAIN);
            }

            Matcher domainRootMatcher = DOMAIN_ROOT_PATTERN.matcher(lineText);
            if (domainRootMatcher.matches()) {
                return buildHeuristicDomainTypeRootContext(
                        text,
                        groupValue(domainRootMatcher, 1),
                        groupRange(text, domainRootMatcher, 1, lineStart, caretOffset),
                        domainScopeOwner);
            }
        }

        if (unitScopeOwner != null) {
            CompletionContext unitRhsContext = detectHeuristicUnitTypeContext(
                    text, lineStart, caretOffset, lineText, unitScopeOwner);
            if (unitRhsContext != null) {
                return unitRhsContext;
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

        Matcher containerBodyMatcher = CONTAINER_BODY_ROOT_PATTERN.matcher(lineText);
        if (containerBodyMatcher.matches()
                && scopeOwner != null
                && (scopeOwner.kind() == InterlisSymbolKind.TOPIC || scopeOwner.kind() == InterlisSymbolKind.MODEL)) {
            String prefix = groupValue(containerBodyMatcher, 1);
            int replaceStart = lineStart;
            if (prefix != null && !prefix.isBlank()) {
                replaceStart = lineStart + containerBodyMatcher.start(1);
            }
            return new CompletionContext(
                    CompletionContext.Kind.CONTAINER_BODY_ROOT,
                    prefix != null ? prefix : "",
                    prefix != null ? prefix : "",
                    null,
                    new Range(DocumentTracker.positionAt(text, replaceStart), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner.id(),
                    null,
                    scopeOwner.kind());
        }

        return null;
    }

    private CompletionContext detectMetaAttributeContext(String text,
                                                         int lineStart,
                                                         int caretOffset,
                                                         String lineText) {
        Matcher valueMatcher = METAATTRIBUTE_VALUE_PATTERN.matcher(lineText);
        if (valueMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.METAATTRIBUTE_VALUE,
                    groupValue(valueMatcher, 2),
                    groupValue(valueMatcher, 1),
                    null,
                    groupRange(text, valueMatcher, 2, lineStart, caretOffset),
                    null,
                    null,
                    null);
        }

        Matcher rootMatcher = METAATTRIBUTE_ROOT_PATTERN.matcher(lineText);
        if (rootMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.METAATTRIBUTE_ROOT,
                    groupValue(rootMatcher, 1),
                    groupValue(rootMatcher, 1),
                    null,
                    groupRange(text, rootMatcher, 1, lineStart, caretOffset),
                    null,
                    null,
                    null);
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

    private CompletionContext buildHeuristicDomainTypeRootContext(String text,
                                                                  String subject,
                                                                  Range replaceRange,
                                                                  LiveSymbol scopeOwner) {
        String effectiveSubject = subject != null ? subject : "";
        String prefix = effectiveSubject;
        String qualifierPath = null;
        Range effectiveRange = replaceRange;
        if (effectiveSubject.contains(".")) {
            prefix = effectiveSubject.substring(effectiveSubject.lastIndexOf('.') + 1);
            qualifierPath = effectiveSubject.substring(0, effectiveSubject.lastIndexOf('.'));
        }
        if (effectiveRange == null) {
            int offset = DocumentTracker.toOffset(text, scopeOwner != null && scopeOwner.nameRange() != null
                    ? scopeOwner.nameRange().getEnd()
                    : new Position(0, 0));
            effectiveRange = new Range(DocumentTracker.positionAt(text, offset), DocumentTracker.positionAt(text, offset));
        }
        return new CompletionContext(
                CompletionContext.Kind.DOMAIN_TYPE_ROOT,
                prefix,
                effectiveSubject,
                qualifierPath,
                effectiveRange,
                scopeOwner != null ? scopeOwner.id() : null,
                DOMAIN_REFERENCE_SYMBOL_KINDS,
                InterlisSymbolKind.DOMAIN);
    }

    private CompletionContext detectUnitHeaderContextWithAbbreviation(String text,
                                                                      int lineStart,
                                                                      int caretOffset,
                                                                      String lineText,
                                                                      LiveSymbol scopeOwner,
                                                                      boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            Matcher afterModifierMatcher = UNIT_BLOCK_HEADER_AFTER_MODIFIER_WITH_ABBR_PATTERN.matcher(lineText);
            if (afterModifierMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_MODIFIER,
                        groupValue(afterModifierMatcher, 2),
                        lineText,
                        null,
                        groupRange(text, afterModifierMatcher, 2, lineStart, caretOffset),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        InterlisSymbolKind.UNIT);
            }

            Matcher modifierCloseMatcher = UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_PATTERN.matcher(lineText);
            if (modifierCloseMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_CLOSE,
                        "",
                        lineText,
                        null,
                        new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        InterlisSymbolKind.UNIT);
            }

            Matcher modifierOpenMatcher = UNIT_HEADER_MODIFIER_OPEN_WITH_ABBR_PATTERN.matcher(lineText);
            if (modifierOpenMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_VALUE,
                        groupValue(modifierOpenMatcher, 1),
                        lineText,
                        null,
                        groupRange(text, modifierOpenMatcher, 1, lineStart, caretOffset),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        InterlisSymbolKind.UNIT);
            }

            Matcher afterNameMatcher = UNIT_BLOCK_HEADER_AFTER_NAME_WITH_ABBR_PATTERN.matcher(lineText);
            if (afterNameMatcher.matches()) {
                return new CompletionContext(
                        CompletionContext.Kind.DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_NAME,
                        groupValue(afterNameMatcher, 1),
                        lineText,
                        null,
                        groupRange(text, afterNameMatcher, 1, lineStart, caretOffset),
                        scopeOwner != null ? scopeOwner.id() : null,
                        null,
                        InterlisSymbolKind.UNIT);
            }
            return null;
        }

        Matcher afterExtendsMatcher = UNIT_HEADER_AFTER_EXTENDS_WITH_ABBR_PATTERN.matcher(lineText);
        if (afterExtendsMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.DECLARATION_HEADER_AFTER_EXTENDS,
                    "",
                    lineText,
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    InterlisSymbolKind.UNIT);
        }

        Matcher afterModifierMatcher = UNIT_HEADER_AFTER_MODIFIER_WITH_ABBR_PATTERN.matcher(lineText);
        if (afterModifierMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.DECLARATION_HEADER_AFTER_MODIFIER,
                    "",
                    lineText,
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    InterlisSymbolKind.UNIT);
        }

        Matcher modifierCloseMatcher = UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_PATTERN.matcher(lineText);
        if (modifierCloseMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.DECLARATION_HEADER_MODIFIER_CLOSE,
                    "",
                    lineText,
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    InterlisSymbolKind.UNIT);
        }

        Matcher modifierOpenMatcher = UNIT_HEADER_MODIFIER_OPEN_WITH_ABBR_PATTERN.matcher(lineText);
        if (modifierOpenMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.DECLARATION_HEADER_MODIFIER_VALUE,
                    groupValue(modifierOpenMatcher, 1),
                    lineText,
                    null,
                    groupRange(text, modifierOpenMatcher, 1, lineStart, caretOffset),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    InterlisSymbolKind.UNIT);
        }

        Matcher afterNameMatcher = UNIT_HEADER_AFTER_NAME_WITH_ABBR_PATTERN.matcher(lineText);
        if (afterNameMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.DECLARATION_HEADER_AFTER_NAME,
                    "",
                    lineText,
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    InterlisSymbolKind.UNIT);
        }

        return null;
    }

    private CompletionContext detectHeuristicUnitTypeContext(String text,
                                                             int lineStart,
                                                             int caretOffset,
                                                             String lineText,
                                                             LiveSymbol scopeOwner) {
        Matcher bracketTargetMatcher = UNIT_BRACKET_TARGET_PATTERN.matcher(lineText);
        if (bracketTargetMatcher.matches()) {
            return buildHeuristicPathAwareContext(
                    CompletionContext.Kind.UNIT_BRACKET_TARGET,
                    text,
                    groupValue(bracketTargetMatcher, 1),
                    groupRange(text, bracketTargetMatcher, 1, lineStart, caretOffset),
                    scopeOwner,
                    UNIT_REFERENCE_SYMBOL_KINDS);
        }

        Matcher composedOperatorMatcher = UNIT_COMPOSED_OPERATOR_PATTERN.matcher(lineText);
        if (composedOperatorMatcher.matches()) {
            return new CompletionContext(
                    CompletionContext.Kind.UNIT_COMPOSED_OPERATOR,
                    "",
                    lineText,
                    null,
                    new Range(DocumentTracker.positionAt(text, caretOffset), DocumentTracker.positionAt(text, caretOffset)),
                    scopeOwner != null ? scopeOwner.id() : null,
                    null,
                    InterlisSymbolKind.UNIT);
        }

        Matcher composedTargetMatcher = UNIT_COMPOSED_TARGET_PATTERN.matcher(lineText);
        if (composedTargetMatcher.matches()) {
            return buildHeuristicPathAwareContext(
                    CompletionContext.Kind.UNIT_COMPOSED_TARGET,
                    text,
                    groupValue(composedTargetMatcher, 1),
                    groupRange(text, composedTargetMatcher, 1, lineStart, caretOffset),
                    scopeOwner,
                    UNIT_REFERENCE_SYMBOL_KINDS);
        }

        Matcher rootMatcher = UNIT_ROOT_PATTERN.matcher(lineText);
        if (rootMatcher.matches()) {
            return buildHeuristicUnitTypeRootContext(
                    text,
                    groupValue(rootMatcher, 1),
                    groupRange(text, rootMatcher, 1, lineStart, caretOffset),
                    scopeOwner);
        }

        return null;
    }

    private CompletionContext buildHeuristicUnitTypeRootContext(String text,
                                                                String subject,
                                                                Range replaceRange,
                                                                LiveSymbol scopeOwner) {
        String effectiveSubject = subject != null ? subject : "";
        Range effectiveRange = replaceRange;
        if (effectiveRange == null) {
            int offset = DocumentTracker.toOffset(text, scopeOwner != null && scopeOwner.nameRange() != null
                    ? scopeOwner.nameRange().getEnd()
                    : new Position(0, 0));
            effectiveRange = new Range(DocumentTracker.positionAt(text, offset), DocumentTracker.positionAt(text, offset));
        }
        return new CompletionContext(
                CompletionContext.Kind.UNIT_TYPE_ROOT,
                effectiveSubject,
                effectiveSubject,
                null,
                effectiveRange,
                scopeOwner != null ? scopeOwner.id() : null,
                UNIT_REFERENCE_SYMBOL_KINDS,
                InterlisSymbolKind.UNIT);
    }

    private MetaAttributeTarget detectMetaAttributeTarget(String text,
                                                          int caretOffset,
                                                          LiveParseResult live,
                                                          TransferDescription td) {
        if (text == null) {
            return MetaAttributeTarget.none();
        }
        String[] lines = text.split("\\R", -1);
        int currentLine = DocumentTracker.positionAt(text, caretOffset).getLine();
        int targetLine = nextSignificantLine(lines, currentLine + 1);
        if (targetLine < 0 || targetLine >= lines.length) {
            return MetaAttributeTarget.none();
        }
        return classifyMetaAttributeTarget(lines, targetLine, text, live, td);
    }

    private MetaAttributeTarget classifyMetaAttributeTarget(String[] lines,
                                                            int targetLine,
                                                            String text,
                                                            LiveParseResult live,
                                                            TransferDescription td) {
        String targetLineText = lines[targetLine];
        String trimmedTargetLine = targetLineText != null ? targetLineText.trim() : "";
        if (trimmedTargetLine.isEmpty()) {
            return MetaAttributeTarget.none();
        }

        LiveSymbol targetSymbol = findTargetSymbolOnLine(targetLineText, targetLine, live);
        if (targetSymbol != null) {
            return switch (targetSymbol.kind()) {
                case CLASS -> new MetaAttributeTarget(Set.of(MetaAttributeTargetProfile.CLASS_DEF));
                case STRUCTURE -> new MetaAttributeTarget(Set.of(MetaAttributeTargetProfile.STRUCTURE_DEF));
                case ATTRIBUTE -> classifyAttributeMetaAttributeTarget(targetLineText, targetLine, text, live, td);
                case ROLE -> new MetaAttributeTarget(Set.of(MetaAttributeTargetProfile.ROLE_DEF));
                default -> MetaAttributeTarget.none();
            };
        }

        if (TARGET_CONSTRAINT_PATTERN.matcher(trimmedTargetLine).find()) {
            return new MetaAttributeTarget(Set.of(MetaAttributeTargetProfile.CONSTRAINT_DEF));
        }
        if (looksLikeEnumElementTarget(lines, targetLine, trimmedTargetLine)) {
            return new MetaAttributeTarget(Set.of(MetaAttributeTargetProfile.ENUM_ELEMENT));
        }
        return MetaAttributeTarget.none();
    }

    private MetaAttributeTarget classifyAttributeMetaAttributeTarget(String targetLineText,
                                                                    int targetLine,
                                                                    String text,
                                                                    LiveParseResult live,
                                                                    TransferDescription td) {
        EnumSet<MetaAttributeTargetProfile> profiles = EnumSet.of(MetaAttributeTargetProfile.ATTRIBUTE_ANY);
        String upper = targetLineText != null ? targetLineText.toUpperCase(Locale.ROOT) : "";
        if (upper.contains("REFERENCE TO")) {
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_REFERENCE_OR_STRUCTLIKE);
            return new MetaAttributeTarget(Set.copyOf(profiles));
        }
        if (upper.contains("LIST") || upper.contains("BAG")) {
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_STRUCTLIKE);
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_REFERENCE_OR_STRUCTLIKE);
            return new MetaAttributeTarget(Set.copyOf(profiles));
        }
        if (upper.contains("ANYSTRUCTURE") || upper.matches(".*:\\s*(?:MANDATORY\\s+)?STRUCTURE\\b.*")) {
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_STRUCTLIKE);
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_REFERENCE_OR_STRUCTLIKE);
            return new MetaAttributeTarget(Set.copyOf(profiles));
        }

        String directTypeToken = extractDirectAttributeTypeToken(targetLineText);
        if (directTypeToken != null && !directTypeToken.isBlank()
                && resolvesStructLikeAttributeType(directTypeToken, targetLine, targetLineText, text, live, td)) {
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_STRUCTLIKE);
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_REFERENCE_OR_STRUCTLIKE);
        } else if (directTypeToken != null && !directTypeToken.isBlank()
                && resolvesReferenceLikeAttributeType(directTypeToken, targetLine, targetLineText, text, live, td)) {
            profiles.add(MetaAttributeTargetProfile.ATTRIBUTE_REFERENCE_OR_STRUCTLIKE);
        }
        return new MetaAttributeTarget(Set.copyOf(profiles));
    }

    private String extractDirectAttributeTypeToken(String targetLineText) {
        Matcher matcher = TARGET_ATTRIBUTE_DECLARATION_PATTERN.matcher(targetLineText != null ? targetLineText : "");
        if (!matcher.matches()) {
            return null;
        }
        String rhs = matcher.group(2);
        if (rhs == null || rhs.isBlank()) {
            return null;
        }
        Matcher typeMatcher = TARGET_ATTRIBUTE_DIRECT_TYPE_PATTERN.matcher(rhs);
        if (!typeMatcher.find()) {
            return null;
        }
        return typeMatcher.group(1);
    }

    private boolean resolvesStructLikeAttributeType(String token,
                                                    int targetLine,
                                                    String targetLineText,
                                                    String text,
                                                    LiveParseResult live,
                                                    TransferDescription td) {
        LiveSymbol symbol = resolveTypeSymbolForMetaAttribute(token, targetLine, targetLineText, text, live);
        if (symbol != null) {
            if (symbol.kind() == InterlisSymbolKind.STRUCTURE) {
                return true;
            }
            if (symbol.kind() == InterlisSymbolKind.DOMAIN && td != null) {
                Element element = resolveElementForMetaAttributeSymbol(td, symbol);
                return isStructureLikeElement(element);
            }
        }
        if (td == null) {
            return false;
        }
        return isStructureLikeElement(InterlisNameResolver.resolveElement(td, token));
    }

    private boolean resolvesReferenceLikeAttributeType(String token,
                                                       int targetLine,
                                                       String targetLineText,
                                                       String text,
                                                       LiveParseResult live,
                                                       TransferDescription td) {
        LiveSymbol symbol = resolveTypeSymbolForMetaAttribute(token, targetLine, targetLineText, text, live);
        if (symbol != null && symbol.kind() == InterlisSymbolKind.DOMAIN && td != null) {
            Element element = resolveElementForMetaAttributeSymbol(td, symbol);
            return isReferenceLikeElement(element);
        }
        if (td == null) {
            return false;
        }
        return isReferenceLikeElement(InterlisNameResolver.resolveElement(td, token));
    }

    private LiveSymbol resolveTypeSymbolForMetaAttribute(String token,
                                                         int targetLine,
                                                         String targetLineText,
                                                         String text,
                                                         LiveParseResult live) {
        if (live == null || live.scopeGraph() == null || token == null || token.isBlank()) {
            return null;
        }
        Position targetPosition = targetNamePosition(targetLineText, targetLine);
        LiveSymbol scopeOwner = targetPosition != null ? live.scopeGraph().findEnclosingContainer(targetPosition) : null;
        SymbolId scopeOwnerId = scopeOwner != null ? scopeOwner.id() : null;
        int lineStartOffset = DocumentTracker.toOffset(text, new Position(targetLine, 0));
        int resolutionOffset = lineEndOffset(text, lineStartOffset);
        Set<InterlisSymbolKind> allowedKinds = Set.of(InterlisSymbolKind.STRUCTURE, InterlisSymbolKind.DOMAIN);
        if (token.contains(".")) {
            List<LiveSymbol> matches = live.scopeGraph().findQualifiedMatchesAt(token, allowedKinds, resolutionOffset);
            return matches.size() == 1 ? matches.get(0) : null;
        }
        return live.scopeGraph().resolveSimpleAt(token, scopeOwnerId, allowedKinds, resolutionOffset);
    }

    private Element resolveElementForMetaAttributeSymbol(TransferDescription td, LiveSymbol symbol) {
        if (td == null || symbol == null) {
            return null;
        }
        Element element = InterlisNameResolver.resolveElement(td, symbol.qualifiedName());
        if (element != null) {
            return element;
        }
        return InterlisNameResolver.resolveElement(td, symbol.name());
    }

    private boolean isStructureLikeElement(Element element) {
        if (element instanceof Table table) {
            return !table.isIdentifiable();
        }
        if (element instanceof Domain domain) {
            return isStructureLikeType(domain.getType());
        }
        return false;
    }

    private boolean isReferenceLikeElement(Element element) {
        if (element instanceof Domain domain) {
            return isReferenceLikeType(domain.getType());
        }
        return false;
    }

    private boolean isReferenceLikeType(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof ReferenceType) {
            return true;
        }
        if (type instanceof TypeAlias alias) {
            Domain aliased = alias.getAliasing();
            if (aliased != null && isReferenceLikeType(aliased.getType())) {
                return true;
            }
            try {
                return isReferenceLikeType(alias.resolveAliases());
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean isStructureLikeType(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof CompositionType compositionType) {
            Table componentType = compositionType.getComponentType();
            return componentType != null && !componentType.isIdentifiable();
        }
        if (type instanceof ClassType classType) {
            return classType.isStructure();
        }
        if (type instanceof TypeAlias alias) {
            Domain aliased = alias.getAliasing();
            if (aliased != null && isStructureLikeType(aliased.getType())) {
                return true;
            }
            try {
                return isStructureLikeType(alias.resolveAliases());
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private LiveSymbol findTargetSymbolOnLine(String targetLineText,
                                              int targetLine,
                                              LiveParseResult live) {
        if (live == null || live.scopeGraph() == null) {
            return null;
        }
        Position position = targetNamePosition(targetLineText, targetLine);
        return position != null ? live.scopeGraph().findSymbolAt(position) : null;
    }

    private Position targetNamePosition(String targetLineText, int targetLine) {
        if (targetLineText == null) {
            return null;
        }
        int indent = firstNonWhitespaceIndex(targetLineText);
        if (indent < 0) {
            return null;
        }
        String trimmed = targetLineText.substring(indent);
        Matcher declarationMatcher = TARGET_DECLARATION_NAME_PATTERN.matcher(trimmed);
        if (declarationMatcher.find()) {
            return new Position(targetLine, indent + declarationMatcher.start(2));
        }
        Matcher identifierMatcher = TARGET_LEADING_IDENTIFIER_PATTERN.matcher(trimmed);
        if (identifierMatcher.find()) {
            return new Position(targetLine, indent + identifierMatcher.start(1));
        }
        return null;
    }

    private boolean looksLikeEnumElementTarget(String[] lines,
                                               int targetLine,
                                               String trimmedTargetLine) {
        if (trimmedTargetLine == null || !TARGET_ENUM_ELEMENT_PATTERN.matcher(trimmedTargetLine).matches()) {
            return false;
        }
        for (int line = targetLine - 1; line >= 0; line--) {
            String previous = lines[line] != null ? lines[line].trim() : "";
            if (previous.isEmpty() || previous.startsWith("!!")) {
                continue;
            }
            if (previous.contains("(") || previous.endsWith(",")) {
                return true;
            }
            if (previous.endsWith(";")
                    || previous.toUpperCase(Locale.ROOT).startsWith("END ")
                    || TARGET_DECLARATION_NAME_PATTERN.matcher(previous).find()
                    || TARGET_CONSTRAINT_PATTERN.matcher(previous).find()) {
                return false;
            }
        }
        return false;
    }

    private int nextSignificantLine(String[] lines, int startLine) {
        if (lines == null) {
            return -1;
        }
        for (int line = Math.max(startLine, 0); line < lines.length; line++) {
            String trimmed = lines[line] != null ? lines[line].trim() : "";
            if (trimmed.isEmpty() || trimmed.startsWith("!!")) {
                continue;
            }
            return line;
        }
        return -1;
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
            case CONTAINER_BODY_ROOT -> false;
            case DECLARATION_HEADER_AFTER_NAME, DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_NAME,
                    DECLARATION_HEADER_MODIFIER_VALUE, DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_VALUE,
                    DECLARATION_HEADER_MODIFIER_CLOSE, DECLARATION_HEADER_BLOCK_SUFFIX_MODIFIER_CLOSE,
                    DECLARATION_HEADER_AFTER_MODIFIER, DECLARATION_HEADER_BLOCK_SUFFIX_AFTER_MODIFIER,
                    DECLARATION_HEADER_AFTER_EXTENDS, DOMAIN_TYPE_ROOT, UNIT_TYPE_ROOT,
                    UNIT_COMPOSED_OPERATOR -> false;
            case METAATTRIBUTE_ROOT, METAATTRIBUTE_VALUE -> true;
            case DECLARATION_HEADER_BLOCK_SUFFIX_EXTENDS_TARGET ->
                    (context.qualifierPath() != null && !context.qualifierPath().isBlank())
                            || (context.prefix() != null && !context.prefix().isBlank());
            case QUALIFIED_MEMBER -> true;
            case ATTRIBUTE_TYPE_ROOT, FORMAT_TYPE_TARGET, COLLECTION_OF_TARGET, REFERENCE_TARGET, EXTENDS_TARGET,
                    UNIT_BRACKET_TARGET, UNIT_COMPOSED_TARGET ->
                    context.prefix() != null && !context.prefix().isBlank();
            case IMPORT_MODEL, END_NAME, TEXT_LENGTH_TAIL, TEXT_LENGTH_VALUE_TAIL,
                    INLINE_NUMERIC_RANGE_TAIL, INLINE_NUMERIC_UPPER_BOUND_TAIL, FORMAT_BOUNDS_TAIL,
                    COLLECTION_POST_KEYWORD, REFERENCE_POST_KEYWORD, META_TYPE_TAIL, NONE -> false;
        };
    }

    private String topicBodyIndentation(LiveParseResult live, CompletionContext context) {
        String containerIndent = containerIndentation(live, context);
        if (containerIndent != null) {
            return containerIndent + "  ";
        }
        return lineIndentationAt(live, context);
    }

    private String topicBodySnippetFirstLineIndent(LiveParseResult live,
                                                   CompletionContext context,
                                                   String baseIndent) {
        if (context == null || context.replaceRange() == null || context.replaceRange().getStart() == null) {
            return "";
        }
        return context.replaceRange().getStart().getCharacter() == 0 ? baseIndent : "";
    }

    private String containerIndentation(LiveParseResult live, CompletionContext context) {
        if (live == null || context == null || context.scopeOwnerId() == null || live.scopeGraph() == null) {
            return null;
        }
        LiveSymbol owner = live.scopeGraph().symbol(context.scopeOwnerId());
        if (owner == null) {
            return null;
        }
        Range anchor = owner.fullRange() != null ? owner.fullRange() : owner.nameRange();
        if (anchor == null || anchor.getStart() == null || live.snapshot() == null) {
            return null;
        }
        return indentationAtPosition(live.snapshot().text(), anchor.getStart());
    }

    private String lineIndentationAt(LiveParseResult live, CompletionContext context) {
        if (live == null || live.snapshot() == null || context == null || context.replaceRange() == null) {
            return "";
        }
        Position start = context.replaceRange().getStart();
        return indentationAtPosition(live.snapshot().text(), start);
    }

    private String indentationAtPosition(String text, Position start) {
        if (text == null || start == null) {
            return "";
        }
        int lineOffset = DocumentTracker.toOffset(text, new Position(start.getLine(), 0));
        int caretOffset = DocumentTracker.toOffset(text, start);
        if (lineOffset < 0 || caretOffset < lineOffset || caretOffset > text.length()) {
            return "";
        }
        String linePrefix = text.substring(lineOffset, caretOffset);
        int indentLength = 0;
        while (indentLength < linePrefix.length()) {
            char ch = linePrefix.charAt(indentLength);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            indentLength++;
        }
        return linePrefix.substring(0, indentLength);
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
        return item(label, label, kind, range, priority, prefix, null);
    }

    private CompletionItem item(String label,
                                String insertText,
                                CompletionItemKind kind,
                                Range range,
                                int priority,
                                String prefix,
                                String filterText) {
        CompletionItem item = new CompletionItem(label);
        item.setKind(kind);
        item.setSortText(sortText(priority, filterText != null ? filterText : label, prefix, label));
        if (filterText != null) {
            item.setFilterText(filterText);
        }
        if (range != null) {
            item.setTextEdit(Either.forLeft(new TextEdit(range, insertText)));
        }
        return item;
    }

    private CompletionItem snippet(String label,
                                   String snippetText,
                                   Range range,
                                   int priority,
                                   String prefix,
                                   String filterText) {
        return snippet(label, snippetText, range, priority, prefix, filterText, null);
    }

    private CompletionItem snippet(String label,
                                   String snippetText,
                                   Range range,
                                   int priority,
                                   String prefix,
                                   String filterText,
                                   InsertTextMode insertTextMode) {
        CompletionItem item = new CompletionItem(label);
        item.setKind(CompletionItemKind.Snippet);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        if (insertTextMode != null) {
            item.setInsertTextMode(insertTextMode);
        }
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

    private static boolean supportsDomainTypeContext(InterlisSymbolKind ownerKind) {
        return ownerKind == InterlisSymbolKind.MODEL
                || ownerKind == InterlisSymbolKind.TOPIC;
    }

    private static boolean supportsUnitTypeContext(InterlisSymbolKind ownerKind) {
        return ownerKind == InterlisSymbolKind.MODEL
                || ownerKind == InterlisSymbolKind.TOPIC;
    }

    private static boolean looksLikeDeclarationHeaderContext(String lineText) {
        return HEADER_AFTER_NAME_PATTERN.matcher(lineText).matches()
                || UNIT_HEADER_AFTER_NAME_WITH_ABBR_PATTERN.matcher(lineText).matches()
                || BLOCK_HEADER_AFTER_NAME_PREFIX_PATTERN.matcher(lineText).matches()
                || UNIT_BLOCK_HEADER_AFTER_NAME_WITH_ABBR_PATTERN.matcher(lineText).matches()
                || HEADER_MODIFIER_OPEN_PATTERN.matcher(lineText).matches()
                || UNIT_HEADER_MODIFIER_OPEN_WITH_ABBR_PATTERN.matcher(lineText).matches()
                || HEADER_MODIFIER_CLOSE_PATTERN.matcher(lineText).matches()
                || UNIT_HEADER_MODIFIER_CLOSE_WITH_ABBR_PATTERN.matcher(lineText).matches()
                || BLOCK_HEADER_AFTER_MODIFIER_PREFIX_PATTERN.matcher(lineText).matches()
                || UNIT_BLOCK_HEADER_AFTER_MODIFIER_WITH_ABBR_PATTERN.matcher(lineText).matches()
                || HEADER_AFTER_MODIFIER_PATTERN.matcher(lineText).matches()
                || UNIT_HEADER_AFTER_MODIFIER_WITH_ABBR_PATTERN.matcher(lineText).matches()
                || HEADER_AFTER_EXTENDS_PATTERN.matcher(lineText).matches()
                || UNIT_HEADER_AFTER_EXTENDS_WITH_ABBR_PATTERN.matcher(lineText).matches();
    }

    private static boolean looksLikeDomainRhsContext(String lineText) {
        return DOMAIN_RHS_PLAUSIBLE_PATTERN.matcher(lineText).matches();
    }

    private static boolean looksLikeUnitRhsContext(String lineText) {
        return UNIT_RHS_PLAUSIBLE_PATTERN.matcher(lineText).matches();
    }

    private static boolean looksLikeMetaAttributeContext(String lineText) {
        return METAATTRIBUTE_ROOT_PATTERN.matcher(lineText).matches()
                || METAATTRIBUTE_VALUE_PATTERN.matcher(lineText).matches();
    }

    private static boolean isMetaAttributeSeverityKey(String metaAttribute) {
        if (metaAttribute == null || metaAttribute.isBlank()) {
            return false;
        }
        String normalized = metaAttribute.toLowerCase(Locale.ROOT);
        return "ilivalid.type".equals(normalized)
                || "ilivalid.multiplicity".equals(normalized)
                || "ilivalid.target".equals(normalized)
                || "ilivalid.check".equals(normalized);
    }

    private static boolean isQuotedMetaAttributeKey(String metaAttribute) {
        if (metaAttribute == null || metaAttribute.isBlank()) {
            return false;
        }
        String normalized = metaAttribute.toLowerCase(Locale.ROOT);
        return "ili2db.dispname".equals(normalized)
                || "ilivalid.keymsg".equals(normalized)
                || normalized.startsWith("ilivalid.keymsg_")
                || "ilivalid.msg".equals(normalized)
                || normalized.startsWith("ilivalid.msg_")
                || "message".equals(normalized)
                || normalized.startsWith("message_");
    }

    private static Set<InterlisSymbolKind> allowedExtendsKinds(InterlisSymbolKind ownerKind) {
        if (ownerKind == null) {
            return Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE,
                    InterlisSymbolKind.TOPIC, InterlisSymbolKind.DOMAIN, InterlisSymbolKind.UNIT,
                    InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW);
        }
        return switch (ownerKind) {
            case CLASS -> Set.of(InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE);
            case STRUCTURE -> Set.of(InterlisSymbolKind.STRUCTURE);
            case TOPIC -> Set.of(InterlisSymbolKind.TOPIC);
            case DOMAIN -> Set.of(InterlisSymbolKind.DOMAIN);
            case UNIT -> Set.of(InterlisSymbolKind.UNIT);
            case ASSOCIATION -> Set.of(InterlisSymbolKind.ASSOCIATION);
            case VIEW -> Set.of(InterlisSymbolKind.VIEW);
            default -> Collections.emptySet();
        };
    }

    private void addUnitHeaderAbbreviationVariant(List<CompletionItem> items,
                                                  CompletionContext context,
                                                  boolean fixedEqualsSuffix) {
        if (context == null || context.ownerKind() != InterlisSymbolKind.UNIT || unitHeaderHasAbbreviation(context.subject())) {
            return;
        }
        addHeaderContinuation(items, "[Name]", unitHeaderAbbreviationInsertText(context, fixedEqualsSuffix), PRIORITY_TOKEN, context);
    }

    private void addHeaderModifierVariants(List<CompletionItem> items,
                                           CompletionContext context,
                                           boolean fixedEqualsSuffix) {
        for (String modifier : allowedHeaderModifiers(context.ownerKind())) {
            String label = "(" + modifier + ")";
            items.add(keywordWithInsertText(
                    label,
                    headerModifierInsertText(context, label, fixedEqualsSuffix),
                    context.replaceRange(),
                    PRIORITY_TOKEN,
                    context.prefix()));
        }
    }

    private void addHeaderModifierValue(List<CompletionItem> items,
                                        String modifier,
                                        CompletionContext context,
                                        boolean fixedEqualsSuffix) {
        items.add(keywordWithInsertText(
                modifier,
                headerModifierValueInsertText(context, modifier, fixedEqualsSuffix),
                context.replaceRange(),
                PRIORITY_TOKEN,
                context.prefix()));
    }

    private void addHeaderContinuation(List<CompletionItem> items,
                                       String label,
                                       String insertText,
                                       int priority,
                                       CompletionContext context) {
        items.add(keywordWithInsertText(label, insertText, context.replaceRange(), priority, context.prefix()));
    }

    private static InterlisSymbolKind declarationKindForExtendsLine(String lineText, InterlisSymbolKind fallback) {
        if (lineText == null || lineText.isBlank()) {
            return fallback;
        }
        Matcher matcher = Pattern.compile("(?i)^\\s*(CLASS|STRUCTURE|TOPIC|DOMAIN|UNIT)\\b").matcher(lineText);
        if (!matcher.find()) {
            return fallback;
        }
        InterlisSymbolKind declarationKind = declarationKind(matcher.group(1));
        return declarationKind != null ? declarationKind : fallback;
    }

    private static InterlisSymbolKind declarationKind(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "CLASS" -> InterlisSymbolKind.CLASS;
            case "STRUCTURE" -> InterlisSymbolKind.STRUCTURE;
            case "TOPIC" -> InterlisSymbolKind.TOPIC;
            case "DOMAIN" -> InterlisSymbolKind.DOMAIN;
            case "UNIT" -> InterlisSymbolKind.UNIT;
            default -> null;
        };
    }

    private static boolean supportsDeclarationHeaderContext(InterlisSymbolKind scopeOwnerKind,
                                                            InterlisSymbolKind declarationKind) {
        if (scopeOwnerKind == null || declarationKind == null) {
            return false;
        }
        return switch (declarationKind) {
            case CLASS, STRUCTURE -> scopeOwnerKind == InterlisSymbolKind.TOPIC || scopeOwnerKind == declarationKind;
            case TOPIC -> scopeOwnerKind == InterlisSymbolKind.MODEL || scopeOwnerKind == InterlisSymbolKind.TOPIC;
            case DOMAIN -> scopeOwnerKind == InterlisSymbolKind.MODEL || scopeOwnerKind == InterlisSymbolKind.TOPIC;
            case UNIT -> scopeOwnerKind == InterlisSymbolKind.MODEL || scopeOwnerKind == InterlisSymbolKind.TOPIC;
            default -> false;
        };
    }

    private static boolean isAllowedHeaderModifier(InterlisSymbolKind declarationKind, String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return true;
        }
        return allowedHeaderModifiers(declarationKind).contains(modifier.toUpperCase(Locale.ROOT));
    }

    private static List<String> allowedHeaderModifiers(InterlisSymbolKind declarationKind) {
        if (declarationKind == null) {
            return List.of();
        }
        return switch (declarationKind) {
            case CLASS, STRUCTURE -> List.of("ABSTRACT", "EXTENDED", "FINAL");
            case TOPIC -> List.of("ABSTRACT", "FINAL");
            case DOMAIN -> List.of("ABSTRACT", "FINAL", "GENERIC");
            case UNIT -> List.of("ABSTRACT");
            default -> List.of();
        };
    }

    private static boolean hasWhitespaceBoundary(CompletionContext context) {
        return context != null && context.subject() != null && !context.subject().isEmpty()
                && Character.isWhitespace(context.subject().charAt(context.subject().length() - 1));
    }

    private static String headerModifierInsertText(CompletionContext context,
                                                   String modifierVariant,
                                                   boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            return modifierVariant + " ";
        }
        return hasWhitespaceBoundary(context) ? modifierVariant : " " + modifierVariant;
    }

    private static String headerModifierValueInsertText(CompletionContext context,
                                                        String modifier,
                                                        boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            return modifier;
        }
        return modifier;
    }

    private static String headerExtendsInsertText(CompletionContext context, boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            return "EXTENDS ";
        }
        return hasWhitespaceBoundary(context) ? "EXTENDS " : " EXTENDS ";
    }

    private static String unitHeaderAbbreviationInsertText(CompletionContext context, boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            return "[Name] ";
        }
        return hasWhitespaceBoundary(context) ? "[Name]" : " [Name]";
    }

    private static String headerModifierCloseInsertText(CompletionContext context, boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            return ") ";
        }
        return ")";
    }

    private static String headerEqualsInsertText(CompletionContext context, boolean fixedEqualsSuffix) {
        if (fixedEqualsSuffix) {
            return "";
        }
        return hasWhitespaceBoundary(context) ? "=" : " =";
    }

    private static boolean unitHeaderHasAbbreviation(String subject) {
        return subject != null && subject.contains("[") && subject.contains("]");
    }

    private CompletionItem keywordWithInsertText(String label,
                                                 String insertText,
                                                 Range range,
                                                 int priority,
                                                 String prefix) {
        return item(label, insertText, CompletionItemKind.Keyword, range, priority, prefix, label);
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

    private static int firstNonWhitespaceIndex(String value) {
        if (value == null) {
            return -1;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
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

    private static int lineEndOffset(String text, int offset) {
        int index = Math.min(Math.max(offset, 0), text.length());
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '\r') {
                break;
            }
            index++;
        }
        return index;
    }

    private static boolean isFixedEqualsSuffix(String lineSuffix) {
        return lineSuffix != null && lineSuffix.trim().matches("^=\\s*;?$");
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

    private List<String> domainRootKeywords(LiveParseResult live, CompletionContext context) {
        List<String> keywords = new ArrayList<>(DOMAIN_ROOT_BASE_KEYWORDS);
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
        int separatorIndex = context.ownerKind() == InterlisSymbolKind.DOMAIN
                ? segment.lastIndexOf('=')
                : segment.lastIndexOf(':');
        if (separatorIndex < 0) {
            return false;
        }
        return segment.substring(separatorIndex + 1).trim().toUpperCase(Locale.ROOT).startsWith("MANDATORY");
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
