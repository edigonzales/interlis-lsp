package ch.so.agi.lsp.interlis.text;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.server.RuntimeDiagnostics;

import java.util.function.BiFunction;

final class InteractiveCompilationResolver {
    private InteractiveCompilationResolver() {
    }

    static Ili2cUtil.CompilationOutcome resolveOutcomeForInteractiveFeature(InterlisLanguageServer server,
                                                                            DocumentTracker documents,
                                                                            CompilationCache compilationCache,
                                                                            BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                                                                            String documentUri,
                                                                            String pathOrUri,
                                                                            String compileSource) {
        if (documentUri == null || documentUri.isBlank() || pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }

        boolean tracked = documents != null && documents.isTracked(documentUri);
        boolean dirty = tracked && documents.isDirty(documentUri);

        if (dirty) {
            return compilationCache.getSuccessful(pathOrUri);
        }

        Ili2cUtil.CompilationOutcome outcome = firstOutcome(
                compilationCache.getSavedAttempt(pathOrUri),
                compilationCache.getSuccessful(pathOrUri));
        if (outcome != null || tracked) {
            return outcome;
        }

        Ili2cUtil.CompilationOutcome compiled = RuntimeDiagnostics.compile(
                server,
                compiler,
                server.getClientSettings(),
                pathOrUri,
                compileSource);
        if (compiled != null) {
            compilationCache.putSavedAttempt(pathOrUri, compiled);
            compilationCache.putSuccessful(pathOrUri, compiled);
        }
        return compiled;
    }

    static TransferDescription resolveTransferDescriptionForInteractiveFeature(InterlisLanguageServer server,
                                                                               DocumentTracker documents,
                                                                               CompilationCache compilationCache,
                                                                               BiFunction<ClientSettings, String, Ili2cUtil.CompilationOutcome> compiler,
                                                                               String documentUri,
                                                                               String pathOrUri,
                                                                               String compileSource) {
        Ili2cUtil.CompilationOutcome outcome = resolveOutcomeForInteractiveFeature(
                server,
                documents,
                compilationCache,
                compiler,
                documentUri,
                pathOrUri,
                compileSource);
        return outcome != null ? outcome.getTransferDescription() : null;
    }

    private static Ili2cUtil.CompilationOutcome firstOutcome(Ili2cUtil.CompilationOutcome primary,
                                                             Ili2cUtil.CompilationOutcome fallback) {
        return primary != null ? primary : fallback;
    }
}
