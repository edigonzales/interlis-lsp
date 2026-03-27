package ch.so.agi.lsp.interlis.text;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

final class InterlisReferencesProvider {
    private final InterlisSymbolQueryEngine queryEngine;

    InterlisReferencesProvider(InterlisLanguageServer server,
                               DocumentTracker documents,
                               CompilationCache cache,
                               BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                               LiveAnalysisService liveAnalysis) {
        this.queryEngine = new InterlisSymbolQueryEngine(server, documents, cache, compiler, liveAnalysis);
    }

    List<? extends Location> references(ReferenceParams params) {
        InterlisSymbolQueryEngine.ResolvedTarget target = queryEngine.resolveTarget(params);
        if (target == null) {
            return Collections.emptyList();
        }
        boolean includeDeclaration = params.getContext() == null || params.getContext().isIncludeDeclaration();
        return queryEngine.toLocations(queryEngine.findOccurrences(target, includeDeclaration));
    }
}
