package ch.so.agi.lsp.interlis.text;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public final class InterlisDefinitionFinder {
    private final InterlisSymbolQueryEngine queryEngine;

    public InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents) {
        this(server, documents, null, Ili2cUtil::compile, new LiveAnalysisService());
    }

    public InterlisDefinitionFinder(InterlisLanguageServer server, DocumentTracker documents, CompilationCache cache) {
        this(server, documents, cache, Ili2cUtil::compile, new LiveAnalysisService());
    }

    public InterlisDefinitionFinder(InterlisLanguageServer server,
                                    DocumentTracker documents,
                                    CompilationCache cache,
                                    BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler) {
        this(server, documents, cache, compiler, new LiveAnalysisService());
    }

    public InterlisDefinitionFinder(InterlisLanguageServer server,
                                    DocumentTracker documents,
                                    CompilationCache cache,
                                    BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                                    LiveAnalysisService liveAnalysis) {
        this.queryEngine = new InterlisSymbolQueryEngine(server, documents, cache, compiler, liveAnalysis);
    }

    public Either<List<? extends Location>, List<? extends LocationLink>> findDefinition(TextDocumentPositionParams params) {
        InterlisSymbolQueryEngine.ResolvedTarget target = queryEngine.resolveTarget(params);
        if (target == null || target.symbol() == null || target.symbol().nameRange() == null) {
            return Either.forLeft(Collections.emptyList());
        }
        Location location = new Location(target.symbol().uri(), target.symbol().nameRange());
        return Either.forLeft(List.of(location));
    }
}
