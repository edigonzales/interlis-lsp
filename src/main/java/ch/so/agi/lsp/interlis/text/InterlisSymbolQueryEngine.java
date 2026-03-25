package ch.so.agi.lsp.interlis.text;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Function;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Unit;
import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.live.DocumentSnapshot;
import ch.so.agi.lsp.interlis.live.InterlisSymbolKind;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.live.LiveParseResult;
import ch.so.agi.lsp.interlis.live.LiveSymbol;
import ch.so.agi.lsp.interlis.live.LiveSymbolResolver;
import ch.so.agi.lsp.interlis.live.ReferenceHit;
import ch.so.agi.lsp.interlis.live.ResolvedSymbol;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.server.RuntimeDiagnostics;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

final class InterlisSymbolQueryEngine {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisSymbolQueryEngine.class);

    private final InterlisLanguageServer server;
    private final DocumentTracker documents;
    private final CompilationCache compilationCache;
    private final BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler;
    private final LiveAnalysisService liveAnalysis;

    InterlisSymbolQueryEngine(InterlisLanguageServer server,
                              DocumentTracker documents,
                              CompilationCache compilationCache,
                              BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                              LiveAnalysisService liveAnalysis) {
        this.server = server;
        this.documents = documents;
        this.compilationCache = compilationCache != null ? compilationCache : new CompilationCache();
        this.compiler = compiler != null ? compiler : Ili2cUtil::compile;
        this.liveAnalysis = liveAnalysis != null ? liveAnalysis : new LiveAnalysisService();
    }

    ResolvedTarget resolveTarget(TextDocumentPositionParams params) {
        if (params == null || params.getTextDocument() == null || params.getPosition() == null) {
            return null;
        }
        String uri = params.getTextDocument().getUri();
        if (uri == null || uri.isBlank()) {
            return null;
        }

        DocumentSnapshot snapshot = snapshot(uri);
        if (snapshot == null) {
            return null;
        }

        LiveParseResult live = liveAnalysis.analyze(snapshot);
        ResolvedSymbol local = LiveSymbolResolver.resolveAtPosition(live, params.getPosition());

        String pathOrUri = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        Ili2cUtil.CompilationOutcome outcome = getOrCompile(pathOrUri, server.getClientSettings());
        TransferDescription td = outcome != null ? outcome.getTransferDescription() : null;

        if (local != null) {
            String authoritativeQualifiedName = resolveAuthoritativeQualifiedName(td, snapshot.text(), uri, params.getPosition(), local);
            ResolvedSymbol enriched = new ResolvedSymbol(
                    local.symbol(),
                    authoritativeQualifiedName,
                    ResolvedSymbol.collectSpellings(
                            authoritativeQualifiedName != null ? authoritativeQualifiedName : local.symbol().qualifiedName(),
                            local.symbol().name()));
            return new ResolvedTarget(uri, enriched, td);
        }

        if (td == null) {
            return null;
        }

        String token = tokenAt(snapshot.text(), params.getPosition());
        if (token == null || token.isBlank()) {
            return null;
        }

        Element element = InterlisNameResolver.resolveElement(td, token);
        if (element == null) {
            element = InterlisRenameProvider.resolveElementAtPosition(td, uri, params.getPosition(), InterlisNameResolver.lastSegment(token));
        }
        if (element == null) {
            return null;
        }

        String qualifiedName = safeScopedName(element);
        String targetUri = elementUri(element);
        LiveSymbol targetSymbol = findOrCreateSymbol(targetUri, qualifiedName, element);
        if (targetSymbol == null) {
            return null;
        }

        ResolvedSymbol resolved = new ResolvedSymbol(
                targetSymbol,
                qualifiedName,
                ResolvedSymbol.collectSpellings(qualifiedName, targetSymbol.name()));
        return new ResolvedTarget(uri, resolved, td);
    }

    List<SymbolOccurrence> findOccurrences(ResolvedTarget target, boolean includeDeclaration) {
        if (target == null || target.symbol() == null) {
            return Collections.emptyList();
        }

        LinkedHashMap<String, SymbolOccurrence> occurrences = new LinkedHashMap<>();
        if (includeDeclaration) {
            addOccurrence(occurrences, target.symbol().uri(), target.symbol().nameRange(),
                    target.symbol().name(), SymbolOccurrenceKind.DECLARATION);
            addOccurrence(occurrences, target.symbol().uri(), target.symbol().endRange(),
                    target.symbol().name(), SymbolOccurrenceKind.END_NAME);
        }

        LinkedHashSet<String> candidateUris = target.transferDescription() != null
                ? InterlisRenameProvider.collectCandidateUris(target.sourceUri(), target.transferDescription())
                : new LinkedHashSet<>(List.of(target.symbol().uri()));
        candidateUris.add(target.symbol().uri());

        for (String candidateUri : candidateUris) {
            LiveParseResult result = analyze(candidateUri);
            if (result == null) {
                continue;
            }

            if (includeDeclaration) {
                LiveSymbol declaration = findMatchingSymbol(result, target.symbol().symbol());
                if (declaration != null) {
                    addOccurrence(occurrences, candidateUri, declaration.nameRange(),
                            declaration.name(), SymbolOccurrenceKind.DECLARATION);
                    addOccurrence(occurrences, candidateUri, declaration.endRange(),
                            declaration.name(), SymbolOccurrenceKind.END_NAME);
                }
            }

            for (ReferenceHit reference : result.scopeGraph().references()) {
                if (LiveSymbolResolver.isReferenceTo(result, reference, target.symbol())) {
                    addOccurrence(occurrences, candidateUri, reference.range(),
                            reference.rawText(), SymbolOccurrenceKind.REFERENCE);
                }
            }
        }

        return new ArrayList<>(occurrences.values());
    }

    List<Location> toLocations(List<SymbolOccurrence> occurrences) {
        if (occurrences == null || occurrences.isEmpty()) {
            return Collections.emptyList();
        }
        List<Location> locations = new ArrayList<>(occurrences.size());
        for (SymbolOccurrence occurrence : occurrences) {
            if (occurrence == null || occurrence.uri() == null || occurrence.range() == null) {
                continue;
            }
            locations.add(new Location(occurrence.uri(), occurrence.range()));
        }
        return locations;
    }

    LiveParseResult analyze(String uri) {
        DocumentSnapshot snapshot = snapshot(uri);
        return snapshot != null ? liveAnalysis.analyze(snapshot) : null;
    }

    private void addOccurrence(Map<String, SymbolOccurrence> occurrences,
                               String uri,
                               Range range,
                               String existingText,
                               SymbolOccurrenceKind kind) {
        if (uri == null || uri.isBlank() || range == null) {
            return;
        }
        String key = uri + ":" + range.getStart().getLine() + ":" + range.getStart().getCharacter()
                + ":" + range.getEnd().getLine() + ":" + range.getEnd().getCharacter();
        occurrences.putIfAbsent(key, new SymbolOccurrence(uri, range, existingText, kind));
    }

    private String resolveAuthoritativeQualifiedName(TransferDescription td,
                                                     String text,
                                                     String uri,
                                                     Position position,
                                                     ResolvedSymbol local) {
        if (td == null || local == null || local.symbol() == null) {
            return null;
        }
        Element element = td.getElement(local.symbol().qualifiedName());
        if (element == null) {
            element = InterlisNameResolver.resolveElement(td, tokenAt(text, position));
        }
        if (element == null) {
            element = InterlisRenameProvider.resolveElementAtPosition(td, uri, position, local.symbol().name());
        }
        return safeScopedName(element);
    }

    private LiveSymbol findMatchingSymbol(LiveParseResult result, LiveSymbol target) {
        if (result == null || target == null) {
            return null;
        }
        for (LiveSymbol symbol : result.scopeGraph().symbols()) {
            if (LiveSymbolResolver.sameQualifiedName(symbol.qualifiedName(), target.qualifiedName())) {
                return symbol;
            }
        }
        for (LiveSymbol symbol : result.scopeGraph().symbols()) {
            if (symbol.name().equals(target.name())) {
                return symbol;
            }
        }
        return null;
    }

    private LiveSymbol findOrCreateSymbol(String uri, String qualifiedName, Element element) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        LiveParseResult live = analyze(uri);
        if (live != null) {
            for (LiveSymbol symbol : live.scopeGraph().symbols()) {
                if (qualifiedName != null && LiveSymbolResolver.sameQualifiedName(symbol.qualifiedName(), qualifiedName)) {
                    return symbol;
                }
            }
            String simpleName = element != null ? element.getName() : null;
            int sourceLine = element != null ? element.getSourceLine() : 0;
            for (LiveSymbol symbol : live.scopeGraph().symbols()) {
                if (simpleName != null && simpleName.equals(symbol.name())
                        && symbol.nameRange() != null
                        && symbol.nameRange().getStart().getLine() == Math.max(sourceLine - 1, 0)) {
                    return symbol;
                }
            }
        }
        return syntheticSymbol(uri, qualifiedName, element);
    }

    private LiveSymbol syntheticSymbol(String uri, String qualifiedName, Element element) {
        if (uri == null || uri.isBlank() || element == null) {
            return null;
        }
        String text = readText(uri);
        if (text == null) {
            return null;
        }
        String name = element.getName();
        if (name == null || name.isBlank()) {
            return null;
        }

        int sourceLine = Math.max(element.getSourceLine() - 1, 0);
        int startOffset = -1;
        if (sourceLine >= 0) {
            int lineStart = DocumentTracker.lineStartOffset(text, sourceLine);
            int lineEnd = DocumentTracker.lineStartOffset(text, sourceLine + 1);
            String lineText = text.substring(lineStart, Math.min(lineEnd, text.length()));
            int relative = lineText.indexOf(name);
            if (relative >= 0) {
                startOffset = lineStart + relative;
            }
        }
        if (startOffset < 0) {
            startOffset = text.indexOf(name);
        }
        if (startOffset < 0) {
            return null;
        }

        Range range = new Range(
                DocumentTracker.positionAt(text, startOffset),
                DocumentTracker.positionAt(text, startOffset + name.length()));
        InterlisSymbolKind kind = toKind(element);
        String effectiveQualifiedName = qualifiedName != null && !qualifiedName.isBlank() ? qualifiedName : name;
        return new LiveSymbol(
                new ch.so.agi.lsp.interlis.live.SymbolId(uri, kind, effectiveQualifiedName, startOffset),
                name,
                effectiveQualifiedName,
                uri,
                kind,
                range,
                range,
                null,
                null);
    }

    private InterlisSymbolKind toKind(Element element) {
        if (element instanceof Model) {
            return InterlisSymbolKind.MODEL;
        }
        if (element instanceof Topic) {
            return InterlisSymbolKind.TOPIC;
        }
        if (element instanceof Table) {
            return InterlisSymbolKind.CLASS;
        }
        if (element instanceof AssociationDef) {
            return InterlisSymbolKind.ASSOCIATION;
        }
        if (element instanceof Domain) {
            return InterlisSymbolKind.DOMAIN;
        }
        if (element instanceof Unit) {
            return InterlisSymbolKind.UNIT;
        }
        if (element instanceof Function) {
            return InterlisSymbolKind.FUNCTION;
        }
        if (element instanceof RoleDef) {
            return InterlisSymbolKind.ROLE;
        }
        return InterlisSymbolKind.CLASS;
    }

    private String elementUri(Element element) {
        if (element == null) {
            return null;
        }
        Model model = InterlisNameResolver.findEnclosingModel(element);
        if (model == null || model.getFileName() == null || model.getFileName().isBlank()) {
            return null;
        }
        String normalized = InterlisTextDocumentService.toFilesystemPathIfPossible(model.getFileName());
        try {
            return Paths.get(normalized).toUri().toString();
        } catch (Exception ex) {
            return model.getFileName();
        }
    }

    private Ili2cUtil.CompilationOutcome getOrCompile(String pathOrUri, ClientSettings cfg) {
        String documentUri = InterlisTextDocumentService.toDocumentUriIfPossible(pathOrUri);
        boolean tracked = documents != null && documents.isTracked(documentUri);
        boolean dirty = tracked && documents.isDirty(documentUri);

        Ili2cUtil.CompilationOutcome cached = dirty
                ? compilationCache.getSuccessful(pathOrUri)
                : firstOutcome(compilationCache.getSavedAttempt(pathOrUri), compilationCache.getSuccessful(pathOrUri));
        if (cached != null || tracked) {
            return cached;
        }

        Ili2cUtil.CompilationOutcome outcome = RuntimeDiagnostics.compile(server, compiler, cfg, pathOrUri, "live-symbol-fallback");
        compilationCache.putSavedAttempt(pathOrUri, outcome);
        compilationCache.putSuccessful(pathOrUri, outcome);
        return outcome;
    }

    private static Ili2cUtil.CompilationOutcome firstOutcome(Ili2cUtil.CompilationOutcome primary,
                                                             Ili2cUtil.CompilationOutcome fallback) {
        return primary != null ? primary : fallback;
    }

    private DocumentSnapshot snapshot(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String text = readText(uri);
        if (text == null) {
            return null;
        }
        String path = InterlisTextDocumentService.toFilesystemPathIfPossible(uri);
        Integer version = documents != null ? documents.getVersion(uri) : null;
        return new DocumentSnapshot(uri, path, text, version);
    }

    private String readText(String uri) {
        String tracked = documents != null ? documents.getText(uri) : null;
        if (tracked != null) {
            return tracked;
        }
        try {
            return InterlisTextDocumentService.readDocument(uri);
        } catch (Exception ex) {
            if (CancellationUtil.isCancellation(ex)) {
                throw CancellationUtil.propagateCancellation(ex);
            }
            LOG.warn("Failed to read {}", uri, ex);
            return null;
        }
    }

    private static String tokenAt(String text, Position position) {
        if (text == null || text.isEmpty() || position == null) {
            return "";
        }
        int offset = DocumentTracker.toOffset(text, position);
        int start = offset;
        while (start > 0 && InterlisNameResolver.isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && InterlisNameResolver.isIdentifierPart(text.charAt(end))) {
            end++;
        }
        return start < end ? text.substring(start, end) : "";
    }

    private static String safeScopedName(Element element) {
        if (element == null) {
            return null;
        }
        try {
            return element.getScopedName();
        } catch (Exception ex) {
            return element.getName();
        }
    }

    record ResolvedTarget(String sourceUri, ResolvedSymbol symbol, TransferDescription transferDescription) {
    }

    record SymbolOccurrence(String uri, Range range, String existingText, SymbolOccurrenceKind kind) {
    }

    enum SymbolOccurrenceKind {
        DECLARATION,
        END_NAME,
        REFERENCE
    }
}
