package ch.so.agi.lsp.interlis.live;

import ch.so.agi.lsp.interlis.antlr.InterlisLexer;
import ch.so.agi.lsp.interlis.antlr.InterlisParser;
import ch.so.agi.lsp.interlis.antlr.InterlisParserBaseListener;
import ch.so.agi.lsp.interlis.text.DocumentTracker;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InterlisLiveAnalyzer {
    private static final Set<InterlisSymbolKind> TOPIC_REFERENCE_KINDS = EnumSet.of(InterlisSymbolKind.TOPIC);
    private static final Set<InterlisSymbolKind> CLASS_REFERENCE_KINDS = EnumSet.of(
            InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE, InterlisSymbolKind.VIEW);
    private static final Set<InterlisSymbolKind> STRUCTURE_REFERENCE_KINDS = EnumSet.of(
            InterlisSymbolKind.STRUCTURE, InterlisSymbolKind.CLASS);
    private static final Set<InterlisSymbolKind> TYPE_REFERENCE_KINDS = EnumSet.of(
            InterlisSymbolKind.DOMAIN, InterlisSymbolKind.STRUCTURE, InterlisSymbolKind.CLASS);
    private static final Set<InterlisSymbolKind> COLLECTION_REFERENCE_KINDS_23 = EnumSet.of(
            InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> COLLECTION_REFERENCE_KINDS_24 = EnumSet.of(
            InterlisSymbolKind.DOMAIN, InterlisSymbolKind.STRUCTURE);
    private static final Set<InterlisSymbolKind> ASSOCIATION_REFERENCE_KINDS = EnumSet.of(InterlisSymbolKind.ASSOCIATION);
    private static final Set<InterlisSymbolKind> DOMAIN_REFERENCE_KINDS = EnumSet.of(InterlisSymbolKind.DOMAIN);
    private static final Set<InterlisSymbolKind> UNIT_REFERENCE_KINDS = EnumSet.of(InterlisSymbolKind.UNIT);
    private static final Set<InterlisSymbolKind> VIEWABLE_REFERENCE_KINDS = EnumSet.of(
            InterlisSymbolKind.CLASS, InterlisSymbolKind.STRUCTURE, InterlisSymbolKind.ASSOCIATION, InterlisSymbolKind.VIEW);
    private final CompletionSlotDetector completionSlotDetector = new CompletionSlotDetector();
    private final SyntaxDiagnosticMapper diagnosticMapper = new SyntaxDiagnosticMapper();
    private final SemanticDiagnosticAnalyzer semanticDiagnosticAnalyzer = new SemanticDiagnosticAnalyzer();

    public LiveParseResult analyze(DocumentSnapshot snapshot) {
        return analyze(snapshot, null);
    }

    public LiveParseResult analyze(DocumentSnapshot snapshot, TransferDescription authoritativeTd) {
        InterlisLanguageLevel languageLevel = InterlisLanguageLevel.detect(snapshot.text());
        InterlisLexer lexer = new InterlisLexer(CharStreams.fromString(snapshot.text()));
        List<RawSyntaxError> rawSyntaxErrors = new ArrayList<>();
        CollectingErrorListener errorListener = new CollectingErrorListener(rawSyntaxErrors);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        InterlisParser parser = new InterlisParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.setErrorHandler(new DefaultErrorStrategy());

        InterlisParser.Interlis2defContext root = parser.interlis2def();
        List<LiveToken> liveTokens = collectDefaultChannelTokens(snapshot.text(), tokens);
        List<ImportEntry> importEntries = collectImportEntries(liveTokens);
        Set<String> importedModelNames = collectImportedModelNames(importEntries);
        ScopeGraph scopeGraph = new ScopeGraph();
        GraphBuilder graphBuilder = new GraphBuilder(snapshot.uri(), snapshot.text(), tokens, scopeGraph, languageLevel);
        ParseTreeWalker.DEFAULT.walk(graphBuilder, root);
        List<CompletionContext> completionContexts = completionSlotDetector.detect(snapshot, scopeGraph, liveTokens, languageLevel);
        List<Diagnostic> syntaxDiagnostics = new ArrayList<>(diagnosticMapper.map(snapshot, scopeGraph, liveTokens, rawSyntaxErrors));
        for (InvalidAttributeValueHit hit : graphBuilder.invalidAttributeValueHits()) {
            if (hit == null || hit.range() == null || overlapsAny(hit.range(), syntaxDiagnostics)) {
                continue;
            }
            syntaxDiagnostics.add(new Diagnostic(
                    hit.range(),
                    "Missing type before value after ':' in attribute definition",
                    org.eclipse.lsp4j.DiagnosticSeverity.Error,
                    "live"));
        }
        List<Diagnostic> diagnostics = new ArrayList<>(syntaxDiagnostics);
        diagnostics.addAll(semanticDiagnosticAnalyzer.analyze(
                snapshot,
                scopeGraph,
                liveTokens,
                syntaxDiagnostics,
                authoritativeTd,
                importEntries,
                importedModelNames));
        return new LiveParseResult(
                snapshot,
                scopeGraph,
                languageLevel,
                liveTokens,
                rawSyntaxErrors,
                completionContexts,
                graphBuilder.formattedDomainIds(),
                importEntries,
                importedModelNames,
                authoritativeTd != null,
                diagnostics);
    }

    private static boolean overlapsAny(Range candidate, List<Diagnostic> diagnostics) {
        if (candidate == null || diagnostics == null || diagnostics.isEmpty()) {
            return false;
        }
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic == null || diagnostic.getRange() == null) {
                continue;
            }
            if (overlaps(candidate, diagnostic.getRange())) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(Range left, Range right) {
        return compare(left.getStart(), right.getEnd()) < 0 && compare(right.getStart(), left.getEnd()) < 0;
    }

    private static int compare(Position left, Position right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left.getLine() != right.getLine()) {
            return Integer.compare(left.getLine(), right.getLine());
        }
        return Integer.compare(left.getCharacter(), right.getCharacter());
    }

    private record InvalidAttributeValueHit(Range range) {
    }

    private static List<LiveToken> collectDefaultChannelTokens(String text, CommonTokenStream tokenStream) {
        List<LiveToken> liveTokens = new ArrayList<>();
        for (Token token : tokenStream.getTokens()) {
            if (token == null || token.getType() == Token.EOF || token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            Position start = new Position(Math.max(token.getLine() - 1, 0), Math.max(token.getCharPositionInLine(), 0));
            int startOffset = DocumentTracker.toOffset(text, start);
            int length = token.getText() != null ? token.getText().length() : 1;
            int endOffset = Math.min(startOffset + length, text.length());
            liveTokens.add(new LiveToken(
                    token.getTokenIndex(),
                    token.getType(),
                    token.getText(),
                    new Range(start, DocumentTracker.positionAt(text, endOffset)),
                    startOffset,
                    endOffset));
        }
        return List.copyOf(liveTokens);
    }

    private static List<ImportEntry> collectImportEntries(List<LiveToken> liveTokens) {
        List<ImportEntry> entries = new ArrayList<>();
        List<ImportEntry> pending = null;
        boolean nextUnqualified = false;
        for (LiveToken token : liveTokens) {
            if (token == null || token.text() == null || token.text().isBlank()) {
                continue;
            }
            String text = token.text();
            String upper = text.toUpperCase(Locale.ROOT);

            if (pending == null) {
                if ("IMPORTS".equals(upper)) {
                    pending = new ArrayList<>();
                    nextUnqualified = false;
                }
                continue;
            }

            if (";".equals(text)) {
                entries.addAll(markTerminated(pending));
                pending = null;
                nextUnqualified = false;
                continue;
            }
            if (",".equals(text)) {
                nextUnqualified = false;
                continue;
            }
            if ("UNQUALIFIED".equals(upper)) {
                nextUnqualified = true;
                continue;
            }
            if ("IMPORTS".equals(upper)) {
                if (!pending.isEmpty()) {
                    entries.addAll(pending);
                }
                pending = new ArrayList<>();
                nextUnqualified = false;
                continue;
            }
            if (isImportBoundary(upper)) {
                if (!pending.isEmpty()) {
                    entries.addAll(pending);
                }
                pending = null;
                nextUnqualified = false;
                continue;
            }
            if ("INTERLIS".equals(upper) || isModelNameToken(text)) {
                pending.add(new ImportEntry(text, token.range(), nextUnqualified, false));
                nextUnqualified = false;
                continue;
            }
            pending = null;
            nextUnqualified = false;
        }
        if (pending != null && !pending.isEmpty()) {
            entries.addAll(pending);
        }
        return List.copyOf(entries);
    }

    private static Set<String> collectImportedModelNames(List<ImportEntry> importEntries) {
        LinkedHashSet<String> importedNames = new LinkedHashSet<>();
        if (importEntries == null) {
            return Set.of();
        }
        for (ImportEntry entry : importEntries) {
            if (entry != null && entry.name() != null && !entry.name().isBlank()) {
                importedNames.add(entry.name());
            }
        }
        return Set.copyOf(importedNames);
    }

    private static List<ImportEntry> markTerminated(List<ImportEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<ImportEntry> terminated = new ArrayList<>(entries.size());
        for (ImportEntry entry : entries) {
            terminated.add(new ImportEntry(entry.name(), entry.range(), entry.unqualified(), true));
        }
        return terminated;
    }

    private static boolean isModelNameToken(String tokenText) {
        if (tokenText == null || tokenText.isBlank()) {
            return false;
        }
        if (!Character.isLetter(tokenText.charAt(0)) && tokenText.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < tokenText.length(); i++) {
            char ch = tokenText.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isImportBoundary(String tokenTextUpper) {
        return Set.of(
                "CONTRACT",
                "METAOBJECT",
                "UNIT",
                "FUNCTION",
                "LINE",
                "DOMAIN",
                "CONTEXT",
                "RUNTIME",
                "CLASS",
                "STRUCTURE",
                "TOPIC",
                "END").contains(tokenTextUpper);
    }

    private static final class CollectingErrorListener extends BaseErrorListener {
        private final List<RawSyntaxError> rawSyntaxErrors;

        private CollectingErrorListener(List<RawSyntaxError> rawSyntaxErrors) {
            this.rawSyntaxErrors = rawSyntaxErrors;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            Token token = offendingSymbol instanceof Token candidate ? candidate : null;
            rawSyntaxErrors.add(new RawSyntaxError(
                    new Position(Math.max(line - 1, 0), Math.max(charPositionInLine, 0)),
                    token != null ? token.getTokenIndex() : null,
                    token != null ? token.getType() : Token.INVALID_TYPE,
                    token != null ? token.getText() : null,
                    msg));
        }
    }

    private static final class GraphBuilder extends InterlisParserBaseListener {
        private final String uri;
        private final String text;
        private final TokenStream tokens;
        private final ScopeGraph scopeGraph;
        private final InterlisLanguageLevel languageLevel;
        private final Deque<LiveSymbol> containers = new ArrayDeque<>();
        private final Set<SymbolId> formattedDomainIds = new LinkedHashSet<>();
        private final List<InvalidAttributeValueHit> invalidAttributeValueHits = new ArrayList<>();

        private GraphBuilder(String uri,
                             String text,
                             TokenStream tokens,
                             ScopeGraph scopeGraph,
                             InterlisLanguageLevel languageLevel) {
            this.uri = uri;
            this.text = text;
            this.tokens = tokens;
            this.scopeGraph = scopeGraph;
            this.languageLevel = languageLevel != null ? languageLevel : InterlisLanguageLevel.UNKNOWN;
        }

        @Override
        public void enterModeldef(InterlisParser.ModeldefContext ctx) {
            TerminalNode name = firstName(ctx.Name());
            pushContainer(ctx, name, InterlisSymbolKind.MODEL, lastNameRange(ctx.Name()));
        }

        @Override
        public void exitModeldef(InterlisParser.ModeldefContext ctx) {
            popContainer();
        }

        @Override
        public void enterTopicDef(InterlisParser.TopicDefContext ctx) {
            pushContainer(ctx, firstName(ctx.Name()), InterlisSymbolKind.TOPIC, lastNameRange(ctx.Name()));
            for (InterlisParser.TopicRefContext reference : ctx.topicRef()) {
                addReference(reference, TOPIC_REFERENCE_KINDS);
            }
        }

        @Override
        public void exitTopicDef(InterlisParser.TopicDefContext ctx) {
            popContainer();
        }

        @Override
        public void enterClassDef(InterlisParser.ClassDefContext ctx) {
            pushContainer(ctx, firstName(ctx.Name()), InterlisSymbolKind.CLASS, lastNameRange(ctx.Name()));
            if (ctx.classOrStructureRef() != null) {
                addReference(ctx.classOrStructureRef(), CLASS_REFERENCE_KINDS);
            }
        }

        @Override
        public void exitClassDef(InterlisParser.ClassDefContext ctx) {
            popContainer();
        }

        @Override
        public void enterStructureDef(InterlisParser.StructureDefContext ctx) {
            pushContainer(ctx, firstName(ctx.Name()), InterlisSymbolKind.STRUCTURE, lastNameRange(ctx.Name()));
            if (ctx.structureRef() != null) {
                addReference(ctx.structureRef(), STRUCTURE_REFERENCE_KINDS);
            }
        }

        @Override
        public void exitStructureDef(InterlisParser.StructureDefContext ctx) {
            popContainer();
        }

        @Override
        public void enterAssociationDef(InterlisParser.AssociationDefContext ctx) {
            TerminalNode name = firstName(ctx.Name());
            pushContainer(ctx, name, InterlisSymbolKind.ASSOCIATION, lastNameRange(ctx.Name()));
            if (ctx.associationRef() != null) {
                addReference(ctx.associationRef(), ASSOCIATION_REFERENCE_KINDS);
            }
        }

        @Override
        public void exitAssociationDef(InterlisParser.AssociationDefContext ctx) {
            popContainer();
        }

        @Override
        public void enterViewDef(InterlisParser.ViewDefContext ctx) {
            pushContainer(ctx, firstName(ctx.Name()), InterlisSymbolKind.VIEW, lastNameRange(ctx.Name()));
            if (ctx.viewRef() != null) {
                addReference(ctx.viewRef(), VIEWABLE_REFERENCE_KINDS);
            }
        }

        @Override
        public void exitViewDef(InterlisParser.ViewDefContext ctx) {
            popContainer();
        }

        @Override
        public void enterGraphicDef(InterlisParser.GraphicDefContext ctx) {
            pushContainer(ctx, firstName(ctx.Name()), InterlisSymbolKind.GRAPHIC, lastNameRange(ctx.Name()));
            if (ctx.graphicRef() != null) {
                addReference(ctx.graphicRef(), VIEWABLE_REFERENCE_KINDS);
            }
            if (ctx.viewableRef() != null) {
                addReference(ctx.viewableRef(), VIEWABLE_REFERENCE_KINDS);
            }
        }

        @Override
        public void exitGraphicDef(InterlisParser.GraphicDefContext ctx) {
            popContainer();
        }

        @Override
        public void enterDomainDef(InterlisParser.DomainDefContext ctx) {
            Set<Integer> formattedDeclarationTokenIndexes = formattedDomainTokenIndexes(ctx);
            for (Token token : declarationTokens(ctx, Set.of(InterlisLexer.Name, InterlisLexer.UUIDOID))) {
                LiveSymbol symbol = addLeaf(ctx, tokenNode(token), InterlisSymbolKind.DOMAIN, null);
                if (symbol != null && formattedDeclarationTokenIndexes.contains(token.getTokenIndex())) {
                    formattedDomainIds.add(symbol.id());
                }
            }
        }

        @Override
        public void enterUnitDef(InterlisParser.UnitDefContext ctx) {
            addLeaf(ctx, firstName(ctx.Name()), InterlisSymbolKind.UNIT, null);
            for (InterlisParser.UnitRefContext reference : ctx.unitRef()) {
                addReference(reference, UNIT_REFERENCE_KINDS);
            }
        }

        @Override
        public void enterFunctionDecl(InterlisParser.FunctionDeclContext ctx) {
            addLeaf(ctx, firstName(ctx.Name()), InterlisSymbolKind.FUNCTION, null);
        }

        @Override
        public void enterAttributeDef(InterlisParser.AttributeDefContext ctx) {
            addLeaf(ctx, ctx.Name(), InterlisSymbolKind.ATTRIBUTE, null);
            collectInvalidAttributeValue(ctx);
        }

        @Override
        public void enterRoleDef(InterlisParser.RoleDefContext ctx) {
            if (ctx.Name() != null) {
                addLeaf(ctx, ctx.Name(), InterlisSymbolKind.ROLE, null);
            }
        }

        @Override
        public void enterTopicRef(InterlisParser.TopicRefContext ctx) {
            addReference(ctx, TOPIC_REFERENCE_KINDS);
        }

        @Override
        public void enterClassRef(InterlisParser.ClassRefContext ctx) {
            addReference(ctx, CLASS_REFERENCE_KINDS);
        }

        @Override
        public void enterStructureRef(InterlisParser.StructureRefContext ctx) {
            addReference(ctx, STRUCTURE_REFERENCE_KINDS);
        }

        @Override
        public void enterAssociationRef(InterlisParser.AssociationRefContext ctx) {
            addReference(ctx, ASSOCIATION_REFERENCE_KINDS);
        }

        @Override
        public void enterDomainRef(InterlisParser.DomainRefContext ctx) {
            addReference(ctx, DOMAIN_REFERENCE_KINDS);
        }

        @Override
        public void enterUnitRef(InterlisParser.UnitRefContext ctx) {
            addReference(ctx, UNIT_REFERENCE_KINDS);
        }

        @Override
        public void enterViewRef(InterlisParser.ViewRefContext ctx) {
            addReference(ctx, VIEWABLE_REFERENCE_KINDS);
        }

        @Override
        public void enterViewableRef(InterlisParser.ViewableRefContext ctx) {
            addReference(ctx, VIEWABLE_REFERENCE_KINDS);
        }

        private void pushContainer(ParserRuleContext ctx, TerminalNode nameNode, InterlisSymbolKind kind, Range endRange) {
            LiveSymbol symbol = addLeaf(ctx, nameNode, kind, endRange);
            if (symbol != null) {
                containers.push(symbol);
            }
        }

        private void popContainer() {
            if (!containers.isEmpty()) {
                containers.pop();
            }
        }

        private LiveSymbol addLeaf(ParserRuleContext ctx, TerminalNode nameNode, InterlisSymbolKind kind, Range endRange) {
            if (nameNode == null || nameNode.getSymbol() == null) {
                return null;
            }
            String name = nameNode.getText();
            if (name == null || name.isBlank()) {
                return null;
            }
            LiveSymbol parent = containers.peek();
            String qualifiedName = parent == null || parent.qualifiedName() == null || parent.qualifiedName().isBlank()
                    ? name
                    : parent.qualifiedName() + "." + name;
            Range nameRange = tokenRange(nameNode.getSymbol());
            Range fullRange = contextRange(ctx);
            SymbolId parentId = parent != null ? parent.id() : null;
            SymbolId id = new SymbolId(uri, kind, qualifiedName, tokenStartOffset(nameNode.getSymbol()));
            LiveSymbol symbol = new LiveSymbol(id, name, qualifiedName, uri, kind, nameRange, fullRange, endRange, parentId);
            scopeGraph.addSymbol(symbol);
            return symbol;
        }

        private Set<SymbolId> formattedDomainIds() {
            return Set.copyOf(formattedDomainIds);
        }

        private List<InvalidAttributeValueHit> invalidAttributeValueHits() {
            return List.copyOf(invalidAttributeValueHits);
        }

        private void collectInvalidAttributeValue(InterlisParser.AttributeDefContext ctx) {
            if (ctx == null || ctx.COLON() == null || ctx.attrTypeDef() != null || ctx.lineType() != null || ctx.factor().isEmpty()) {
                return;
            }
            ParserRuleContext factor = ctx.factor(0);
            if (factor == null || factor.getStart() == null) {
                return;
            }
            Range range = tokenRange(factor.getStart());
            if (range != null) {
                invalidAttributeValueHits.add(new InvalidAttributeValueHit(range));
            }
        }

        private void addReference(ParserRuleContext ctx, Set<InterlisSymbolKind> allowedKinds) {
            String rawText = visibleText(ctx);
            if (rawText == null || rawText.isBlank()) {
                return;
            }
            Set<InterlisSymbolKind> effectiveKinds = referenceKinds(ctx, allowedKinds);
            scopeGraph.addReference(new ReferenceHit(
                    uri,
                    contextRange(ctx),
                    rawText,
                    effectiveKinds,
                    containers.isEmpty() ? null : containers.peek().id()));
        }

        private Set<InterlisSymbolKind> referenceKinds(ParserRuleContext ctx, Set<InterlisSymbolKind> defaultKinds) {
            if (ctx == null || defaultKinds == null || defaultKinds.isEmpty()) {
                return defaultKinds;
            }

            InterlisParser.AttrTypeDefContext attrTypeDef = findAncestor(ctx, InterlisParser.AttrTypeDefContext.class);
            if (attrTypeDef == null) {
                return defaultKinds;
            }
            if (isCollectionAttrType(attrTypeDef)) {
                return languageLevel.supportsCollectionDomains()
                        ? COLLECTION_REFERENCE_KINDS_24
                        : COLLECTION_REFERENCE_KINDS_23;
            }
            if (isDirectAttributeTypeReference(ctx)) {
                return TYPE_REFERENCE_KINDS;
            }
            return defaultKinds;
        }

        private boolean isDirectAttributeTypeReference(ParserRuleContext ctx) {
            if (ctx == null || findAncestor(ctx, InterlisParser.AttrTypeContext.class) == null) {
                return false;
            }
            if (ctx instanceof InterlisParser.DomainRefContext) {
                return true;
            }
            if (!(ctx instanceof InterlisParser.StructureRefContext)) {
                return false;
            }
            InterlisParser.RestrictedStructureRefContext restrictedStructureRef =
                    findAncestor(ctx, InterlisParser.RestrictedStructureRefContext.class);
            return restrictedStructureRef != null && isPrimaryRestrictedStructureReference(ctx, restrictedStructureRef);
        }

        private boolean isPrimaryRestrictedStructureReference(
                ParserRuleContext ctx,
                InterlisParser.RestrictedStructureRefContext restrictedStructureRef) {
            if (ctx == null || restrictedStructureRef == null) {
                return false;
            }
            TerminalNode restriction = restrictedStructureRef.RESTRICTION();
            return restriction == null || ctx.getStart().getTokenIndex() < restriction.getSymbol().getTokenIndex();
        }

        private boolean isCollectionAttrType(InterlisParser.AttrTypeDefContext ctx) {
            boolean hasCollectionKeyword = false;
            boolean hasOf = false;
            for (Token token : contextTokens(ctx)) {
                if (token == null) {
                    continue;
                }
                if (token.getType() == InterlisLexer.BAG || token.getType() == InterlisLexer.LIST) {
                    hasCollectionKeyword = true;
                } else if (token.getType() == InterlisLexer.OF) {
                    hasOf = true;
                }
            }
            return hasCollectionKeyword && hasOf;
        }

        private List<Token> contextTokens(ParserRuleContext ctx) {
            List<Token> result = new ArrayList<>();
            if (ctx == null || ctx.getStart() == null) {
                return result;
            }
            int start = ctx.getStart().getTokenIndex();
            int stop = ctx.getStop() != null ? ctx.getStop().getTokenIndex() : start;
            for (int i = start; i <= stop; i++) {
                Token token = tokens.get(i);
                if (token == null || token.getChannel() != Token.DEFAULT_CHANNEL) {
                    continue;
                }
                result.add(token);
            }
            return result;
        }

        private <T extends ParserRuleContext> T findAncestor(ParserRuleContext ctx, Class<T> type) {
            ParserRuleContext current = ctx;
            while (current != null) {
                if (type.isInstance(current)) {
                    return type.cast(current);
                }
                current = current.getParent();
            }
            return null;
        }

        private String visibleText(ParserRuleContext ctx) {
            if (ctx == null || ctx.getStart() == null) {
                return "";
            }
            int start = ctx.getStart().getTokenIndex();
            int stop = ctx.getStop() != null ? ctx.getStop().getTokenIndex() : start;
            StringBuilder builder = new StringBuilder();
            for (int i = start; i <= stop; i++) {
                Token token = tokens.get(i);
                if (token == null || token.getType() == Token.EOF || token.getChannel() != Token.DEFAULT_CHANNEL) {
                    continue;
                }
                builder.append(token.getText());
            }
            return builder.toString();
        }

        private List<Token> declarationTokens(ParserRuleContext ctx, Set<Integer> allowedTypes) {
            List<Token> result = new ArrayList<>();
            if (ctx == null || ctx.getStart() == null) {
                return result;
            }
            int start = ctx.getStart().getTokenIndex();
            int stop = ctx.getStop() != null ? ctx.getStop().getTokenIndex() : start;
            boolean clauseStart = true;
            int parenDepth = 0;
            int braceDepth = 0;
            int bracketDepth = 0;
            for (int i = start; i <= stop; i++) {
                Token token = tokens.get(i);
                if (token == null || token.getChannel() != Token.DEFAULT_CHANNEL) {
                    continue;
                }
                switch (token.getType()) {
                    case InterlisLexer.LPAR -> parenDepth++;
                    case InterlisLexer.RPAR -> parenDepth = Math.max(parenDepth - 1, 0);
                    case InterlisLexer.LCBR -> braceDepth++;
                    case InterlisLexer.RCBR -> braceDepth = Math.max(braceDepth - 1, 0);
                    case InterlisLexer.LSBR -> bracketDepth++;
                    case InterlisLexer.RSBR -> bracketDepth = Math.max(bracketDepth - 1, 0);
                    case InterlisLexer.SEMI -> clauseStart = true;
                    case InterlisLexer.EQ -> clauseStart = false;
                    default -> {
                        if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && clauseStart
                                && allowedTypes.contains(token.getType())) {
                            result.add(token);
                            clauseStart = false;
                        }
                    }
                }
            }
            return result;
        }

        private Set<Integer> formattedDomainTokenIndexes(ParserRuleContext ctx) {
            LinkedHashSet<Integer> result = new LinkedHashSet<>();
            if (ctx == null || ctx.getStart() == null) {
                return Set.of();
            }
            int start = ctx.getStart().getTokenIndex();
            int stop = ctx.getStop() != null ? ctx.getStop().getTokenIndex() : start;
            boolean clauseStart = true;
            boolean inClause = false;
            boolean afterEq = false;
            boolean formatted = false;
            int currentDeclaration = -1;
            int parenDepth = 0;
            int braceDepth = 0;
            int bracketDepth = 0;
            for (int i = start; i <= stop; i++) {
                Token token = tokens.get(i);
                if (token == null || token.getChannel() != Token.DEFAULT_CHANNEL) {
                    continue;
                }
                switch (token.getType()) {
                    case InterlisLexer.LPAR -> parenDepth++;
                    case InterlisLexer.RPAR -> parenDepth = Math.max(parenDepth - 1, 0);
                    case InterlisLexer.LCBR -> braceDepth++;
                    case InterlisLexer.RCBR -> braceDepth = Math.max(braceDepth - 1, 0);
                    case InterlisLexer.LSBR -> bracketDepth++;
                    case InterlisLexer.RSBR -> bracketDepth = Math.max(bracketDepth - 1, 0);
                    case InterlisLexer.EQ -> afterEq = inClause;
                    case InterlisLexer.FORMAT -> {
                        if (afterEq && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                            formatted = true;
                        }
                    }
                    case InterlisLexer.SEMI -> {
                        if (currentDeclaration >= 0 && formatted) {
                            result.add(currentDeclaration);
                        }
                        clauseStart = true;
                        inClause = false;
                        afterEq = false;
                        formatted = false;
                        currentDeclaration = -1;
                    }
                    default -> {
                        if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && clauseStart
                                && (token.getType() == InterlisLexer.Name || token.getType() == InterlisLexer.UUIDOID)) {
                            currentDeclaration = token.getTokenIndex();
                            clauseStart = false;
                            inClause = true;
                            afterEq = false;
                            formatted = false;
                        }
                    }
                }
            }
            if (currentDeclaration >= 0 && formatted) {
                result.add(currentDeclaration);
            }
            return Set.copyOf(result);
        }

        private Range lastNameRange(List<TerminalNode> names) {
            if (names == null || names.isEmpty()) {
                return null;
            }
            return tokenRange(names.get(names.size() - 1).getSymbol());
        }

        private Range lastNameRange(TerminalNode node) {
            return node != null ? tokenRange(node.getSymbol()) : null;
        }

        private Range contextRange(ParserRuleContext ctx) {
            if (ctx == null || ctx.getStart() == null) {
                return new Range(new Position(0, 0), new Position(0, 0));
            }
            Token start = ctx.getStart();
            Token stop = ctx.getStop() != null ? ctx.getStop() : start;
            return new Range(
                    new Position(Math.max(start.getLine() - 1, 0), Math.max(start.getCharPositionInLine(), 0)),
                    new Position(Math.max(stop.getLine() - 1, 0), Math.max(stop.getCharPositionInLine() + safeLength(stop), 0)));
        }

        private Range tokenRange(Token token) {
            if (token == null) {
                return new Range(new Position(0, 0), new Position(0, 0));
            }
            Position start = new Position(Math.max(token.getLine() - 1, 0), Math.max(token.getCharPositionInLine(), 0));
            Position end = new Position(start.getLine(), start.getCharacter() + safeLength(token));
            return new Range(start, end);
        }

        private int tokenStartOffset(Token token) {
            if (token == null) {
                return 0;
            }
            return DocumentTracker.toOffset(text,
                    new Position(Math.max(token.getLine() - 1, 0), Math.max(token.getCharPositionInLine(), 0)));
        }

        private static int safeLength(Token token) {
            String tokenText = token.getText();
            return tokenText != null ? tokenText.length() : 1;
        }

        private TerminalNode firstName(List<TerminalNode> names) {
            if (names == null || names.isEmpty()) {
                return null;
            }
            return names.get(0);
        }

        private TerminalNode tokenNode(Token token) {
            return token != null ? new SyntheticTerminalNode(token) : null;
        }

        private TerminalNode tokenNode(TerminalNode node) {
            return node;
        }
    }

    private static final class SyntheticTerminalNode implements TerminalNode {
        private final Token token;

        private SyntheticTerminalNode(Token token) {
            this.token = token;
        }

        @Override
        public Token getSymbol() {
            return token;
        }

        @Override
        public org.antlr.v4.runtime.tree.ParseTree getParent() {
            return null;
        }

        @Override
        public org.antlr.v4.runtime.tree.ParseTree getChild(int i) {
            return null;
        }

        @Override
        public void setParent(org.antlr.v4.runtime.RuleContext parent) {
        }

        @Override
        public <T> T accept(org.antlr.v4.runtime.tree.ParseTreeVisitor<? extends T> visitor) {
            return visitor.visitTerminal(this);
        }

        @Override
        public String getText() {
            return token.getText();
        }

        @Override
        public String toStringTree() {
            return getText();
        }

        @Override
        public String toStringTree(org.antlr.v4.runtime.Parser parser) {
            return getText();
        }

        @Override
        public org.antlr.v4.runtime.misc.Interval getSourceInterval() {
            int index = token.getTokenIndex();
            return new org.antlr.v4.runtime.misc.Interval(index, index);
        }

        @Override
        public int getChildCount() {
            return 0;
        }

        @Override
        public Object getPayload() {
            return token;
        }

        @Override
        public String toString() {
            return getText();
        }
    }
}
