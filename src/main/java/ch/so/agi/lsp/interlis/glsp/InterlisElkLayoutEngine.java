package ch.so.agi.lsp.interlis.glsp;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.graph.properties.IPropertyHolder;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.layout.ElkLayoutEngine;
import org.eclipse.glsp.layout.GLSPLayoutConfigurator;

import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;

/**
 * Configures ELK Layered for UML-like class diagrams with container nodes.
 */
public class InterlisElkLayoutEngine extends ElkLayoutEngine {
    public static final String DIRECTION_PROPERTY = "interlis.glsp.layout.direction";
    public static final String EDGE_ROUTING_PROPERTY = "interlis.glsp.layout.edgeRouting";

    private static final Direction DEFAULT_DIRECTION = Direction.RIGHT;
    private static final EdgeRouting DEFAULT_EDGE_ROUTING = EdgeRouting.ORTHOGONAL;
    private static final int DEFAULT_THOROUGHNESS = 10;
    private static final double ROOT_NODE_NODE_SPACING = 60d;
    private static final double ROOT_EDGE_NODE_SPACING = 30d;
    private static final double LAYER_NODE_NODE_SPACING = 100d;
    private static final double LAYER_EDGE_NODE_SPACING = 40d;
    private static final double LAYER_EDGE_EDGE_SPACING = 25d;
    private static final double CONTAINER_PADDING = 30d;

    @Override
    public void layout() {
        Object root = modelState.getRoot();
        if (!(root instanceof GGraph graph)) {
            return;
        }

        GLSPLayoutConfigurator configurator = createConfigurator(graph);
        layout(graph, configurator);
    }

    protected GLSPLayoutConfigurator createConfigurator(GGraph graph) {
        GLSPLayoutConfigurator configurator = new GLSPLayoutConfigurator();
        configureRoot(configurator.configureById(graph.getId()));
        configureEdges(configurator);
        resolveContainerIds().forEach(containerId -> configureContainer(configurator.configureById(containerId)));
        return configurator;
    }

    protected void configureRoot(IPropertyHolder rootOptions) {
        rootOptions.setProperty(CoreOptions.ALGORITHM, LayeredOptions.ALGORITHM_ID);
        rootOptions.setProperty(CoreOptions.EDGE_ROUTING, resolveEdgeRouting());
        rootOptions.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN);
        rootOptions.setProperty(CoreOptions.DIRECTION, resolveDirection());

        rootOptions.setProperty(LayeredOptions.MERGE_HIERARCHY_EDGES, true);
        rootOptions.setProperty(LayeredOptions.MERGE_EDGES, true);
        rootOptions.setProperty(LayeredOptions.CROSSING_MINIMIZATION_STRATEGY, CrossingMinimizationStrategy.LAYER_SWEEP);
        rootOptions.setProperty(LayeredOptions.THOROUGHNESS, DEFAULT_THOROUGHNESS);

        rootOptions.setProperty(CoreOptions.SPACING_NODE_NODE, ROOT_NODE_NODE_SPACING);
        rootOptions.setProperty(CoreOptions.SPACING_EDGE_NODE, ROOT_EDGE_NODE_SPACING);
        rootOptions.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, LAYER_NODE_NODE_SPACING);
        rootOptions.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, LAYER_EDGE_NODE_SPACING);
        rootOptions.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, LAYER_EDGE_EDGE_SPACING);
    }

    protected void configureContainer(IPropertyHolder containerOptions) {
        containerOptions.setProperty(CoreOptions.PADDING, new ElkPadding(CONTAINER_PADDING));
    }

    protected void configureEdges(GLSPLayoutConfigurator configurator) {
        InterlisDiagramModel.DiagramModel diagram = modelState
                .getProperty(InterlisGlspModelStateKeys.MODEL, InterlisDiagramModel.DiagramModel.class)
                .orElse(null);
        if (diagram == null) {
            return;
        }

        for (InterlisDiagramModel.EdgeModel edge : diagram.getEdges()) {
            if (edge == null || edge.getId() == null || edge.getId().isBlank()) {
                continue;
            }
            configureEdge(edge, configurator.configureById(edge.getId()));
        }
    }

    protected void configureEdge(InterlisDiagramModel.EdgeModel edge, IPropertyHolder edgeOptions) {
        // Hook for future edge-type-specific priorities or routing options.
    }

    protected Set<String> resolveContainerIds() {
        Set<String> ids = new LinkedHashSet<>();
        InterlisDiagramModel.DiagramModel diagram = modelState
                .getProperty(InterlisGlspModelStateKeys.MODEL, InterlisDiagramModel.DiagramModel.class)
                .orElse(null);
        if (diagram == null) {
            return ids;
        }

        for (InterlisDiagramModel.ContainerModel container : diagram.getContainers()) {
            if (container != null && container.getId() != null && !container.getId().isBlank()) {
                ids.add(container.getId());
            }
        }
        return ids;
    }

    protected Direction resolveDirection() {
        String configured = System.getProperty(DIRECTION_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DIRECTION;
        }

        try {
            return Direction.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DEFAULT_DIRECTION;
        }
    }

    protected EdgeRouting resolveEdgeRouting() {
        String configured = null;
        InterlisLanguageServer languageServer = InterlisGlspBridge.getLanguageServer();
        if (languageServer != null) {
            ClientSettings settings = languageServer.getClientSettings();
            if (settings != null) {
                configured = settings.getEdgeRouting();
            }
        }
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty(EDGE_ROUTING_PROPERTY);
        }

        EdgeRouting parsed = parseEdgeRouting(configured);
        return parsed != null ? parsed : DEFAULT_EDGE_ROUTING;
    }

    protected EdgeRouting parseEdgeRouting(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            return EdgeRouting.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
