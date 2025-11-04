package ch.so.agi.glsp.interlis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.model.GModelState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.ClientSettings;
import ch.so.agi.lsp.interlis.Ili2cUtil;
import ch.so.agi.lsp.interlis.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.InterlisTextDocumentService;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram;

final class InterlisGModelFactory implements GModelFactory {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGModelFactory.class);

    private final GModelState modelState;
    private final InterlisLanguageServer languageServer;

    @Inject
    InterlisGModelFactory(GModelState modelState, InterlisLanguageServer languageServer) {
        this.modelState = Objects.requireNonNull(modelState, "modelState");
        this.languageServer = Objects.requireNonNull(languageServer, "languageServer");
    }

    @Override
    public void createGModel() {
        String sourceUri = modelState.getProperty(InterlisGlspConstants.PROP_SOURCE_URI, String.class).orElse(null);
        Map<String, String> options = modelState.getClientOptions();
        if (options != null) {
            String candidate = options.get(InterlisGlspConstants.OPTION_SOURCE_URI);
            if (candidate != null && !candidate.isBlank()) {
                sourceUri = candidate;
                modelState.setProperty(InterlisGlspConstants.PROP_SOURCE_URI, candidate);
            }
        }

        if (sourceUri == null || sourceUri.isBlank()) {
            modelState.updateRoot(InterlisGlspModelBuilder.buildMessageGraph("Select an INTERLIS file to view the diagram."));
            return;
        }

        String path = InterlisTextDocumentService.toFilesystemPathIfPossible(sourceUri);
        if (path == null || path.isBlank()) {
            LOG.warn("Cannot resolve path for URI {}", sourceUri);
            modelState.updateRoot(InterlisGlspModelBuilder.buildMessageGraph("Unable to resolve model path."));
            return;
        }

        ClientSettings settings = languageServer.getClientSettings();
        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(settings, path);
        TransferDescription td = outcome != null ? outcome.getTransferDescription() : null;
        if (td == null) {
            LOG.warn("No transfer description returned for {}", path);
            modelState.updateRoot(InterlisGlspModelBuilder.buildMessageGraph("INTERLIS compilation failed."));
            return;
        }

        List<InterlisUmlDiagram.ClassEntry> classes = InterlisUmlDiagram.collectPrimaryClasses(td);
        if (classes.isEmpty()) {
            modelState.updateRoot(InterlisGlspModelBuilder.buildMessageGraph("No classes found in the INTERLIS model."));
            return;
        }

        GGraph root = InterlisGlspModelBuilder.buildClassDiagram(classes);
        modelState.updateRoot(root);
    }
}
