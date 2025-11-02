package ch.so.agi.glsp.interlis;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.model.GModelState;

import com.google.inject.Inject;

import ch.so.agi.glsp.interlis.model.InterlisDiagramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the graphical model that is sent to GLSP clients by delegating to the
 * {@link ch.so.agi.glsp.interlis.model.InterlisDiagramService}.
 */
public class InterlisGModelFactory implements GModelFactory {

    private static final Logger LOG = LoggerFactory.getLogger(InterlisGModelFactory.class);

    private final GModelState modelState;
    private final InterlisDiagramService diagramService;

    /**
     * @param modelState the shared model state containing the source model metadata
     */
    @Inject
    public InterlisGModelFactory(final GModelState modelState, final InterlisDiagramService diagramService) {
        this.modelState = modelState;
        this.diagramService = diagramService;
    }

    @Override
    public void createGModel() {
        Optional<String> sourceUri = modelState
            .getProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, String.class);

        sourceUri.ifPresent(source -> modelState.setProperty(InterlisSourceModelStorage.SOURCE_URI_PROPERTY, source));

        GGraph root = sourceUri.flatMap(this::loadGraphFromSource)
            .orElseGet(this::createEmptyGraph);

        modelState.updateRoot(root);
    }

    private Optional<GGraph> loadGraphFromSource(final String sourceUri) {
        try {
            URI uri = new URI(sourceUri);
            Path sourcePath;
            if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
                sourcePath = Paths.get(sourceUri);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                sourcePath = Path.of(uri);
            } else {
                LOG.warn("Unsupported URI scheme '{}' for INTERLIS source", uri.getScheme());
                return Optional.empty();
            }
            return diagramService.loadDiagram(sourcePath);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            LOG.warn("Could not resolve INTERLIS source URI '{}': {}", sourceUri, ex.getMessage());
            LOG.debug("Source URI resolution failure", ex);
            return Optional.empty();
        }
    }

    private GGraph createEmptyGraph() {
        return new GGraphBuilder()
            .id(InterlisGlspTypes.GRAPH_ID)
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .layoutOptions(Map.of("padding", "24"))
            .build();
    }
}
