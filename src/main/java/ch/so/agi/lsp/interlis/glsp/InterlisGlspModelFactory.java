package ch.so.agi.lsp.interlis.glsp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.glsp.graph.DefaultTypes;
import org.eclipse.glsp.graph.GEdge;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GLabel;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.builder.impl.GCompartmentBuilder;
import org.eclipse.glsp.graph.builder.impl.GEdgeBuilder;
import org.eclipse.glsp.graph.builder.impl.GEdgePlacementBuilder;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;
import org.eclipse.glsp.server.features.core.model.GModelFactory;
import org.eclipse.glsp.server.layout.LayoutEngine;
import org.eclipse.glsp.server.model.GModelState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;

public class InterlisGlspModelFactory implements GModelFactory {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisGlspModelFactory.class);

    private static final double OUTER_PADDING = 24;
    private static final double MAX_ROW_WIDTH = 2300;
    private static final double CONTAINER_GAP = 28;
    private static final double CONTAINER_PADDING = 16;
    private static final double CONTAINER_HEADER_HEIGHT = 34;
    private static final double CONTAINER_MIN_WIDTH = 360;
    private static final double NODE_WIDTH = 290;
    private static final double NODE_MIN_HEIGHT = 82;
    private static final double NODE_GAP_X = 18;
    private static final double NODE_GAP_Y = 16;
    private static final double NODE_HEADER_BASELINE = 20;
    private static final double NODE_CONTENT_START = 38;
    private static final double NODE_LINE_HEIGHT = 14;
    private static final double SECTION_GAP = 6;
    private static final double EDGE_LABEL_CHAR_WIDTH = 7.2;
    private static final double EDGE_LABEL_PADDING_X = 10;
    private static final double EDGE_LABEL_HEIGHT = 16;
    private static final double EDGE_LABEL_MIN_WIDTH = 42;
    private static final double EDGE_LABEL_MAX_WIDTH = 320;
    private static final double ASSOCIATION_LABEL_FALLBACK_POSITION = 0.5;
    private static final double ASSOCIATION_LABEL_FALLBACK_OFFSET = 18;

    @Inject
    protected GModelState modelState;

    @Inject
    protected Optional<LayoutEngine> layoutEngine;

    @Override
    public void createGModel() {
        String error = modelState.getProperty(InterlisGlspModelStateKeys.ERROR, String.class).orElse(null);
        String sourceUri = modelState.getProperty(InterlisGlspModelStateKeys.SOURCE_URI, String.class).orElse("unknown");
        InterlisDiagramModel.DiagramModel diagramModel = modelState
                .getProperty(InterlisGlspModelStateKeys.MODEL, InterlisDiagramModel.DiagramModel.class)
                .orElse(null);

        GGraph graph = (error != null && !error.isBlank())
                ? buildErrorGraph(sourceUri, error)
                : buildDiagramGraph(diagramModel);

        modelState.updateRoot(graph);
        if (error == null || error.isBlank()) {
            layoutEngine.ifPresent(engine -> {
                try {
                    engine.layout();
                } catch (RuntimeException ex) {
                    LOG.warn("ELK layout failed. Falling back to static coordinates.", ex);
                }
            });
            applyAssociationLabelPlacementFallback(graph);
        }
    }

    private GGraph buildDiagramGraph(InterlisDiagramModel.DiagramModel diagramModel) {
        InterlisDiagramModel.DiagramModel diagram = diagramModel != null
                ? diagramModel
                : new InterlisDiagramModel.DiagramModel("1", List.of(), List.of(), List.of());

        Map<String, InterlisDiagramModel.NodeModel> nodesById = new LinkedHashMap<>();
        for (InterlisDiagramModel.NodeModel node : safeList(diagram.getNodes())) {
            if (node == null || isBlank(node.getId())) {
                continue;
            }
            nodesById.put(node.getId(), node);
        }

        Map<String, List<InterlisDiagramModel.NodeModel>> nodesByContainer = new LinkedHashMap<>();
        for (InterlisDiagramModel.NodeModel node : nodesById.values()) {
            String containerId = firstNonBlank(node.getContainerId(), "__interlis_root__");
            nodesByContainer.computeIfAbsent(containerId, key -> new ArrayList<>()).add(node);
        }

        List<InterlisDiagramModel.ContainerModel> containers = sortedContainers(diagram, nodesByContainer);
        DiagramLayout layout = computeLayout(containers, nodesById, nodesByContainer);

        GGraphBuilder graphBuilder = new GGraphBuilder(DefaultTypes.GRAPH)
                .id("interlis-graph")
                .canvasBounds(0, 0, layout.canvasWidth, layout.canvasHeight)
                .addCssClass("interlis-graph");

        for (ContainerLayout containerLayout : layout.containers) {
            InterlisDiagramModel.ContainerModel container = containerLayout.container;
            String containerId = firstNonBlank(container.getId(), "container-" + sanitizeCss(container.getLabel()));
            String kind = sanitizeCss(firstNonBlank(container.getKind(), "namespace"));

            GNodeBuilder containerBuilder = new GNodeBuilder(DefaultTypes.NODE)
                    .id(containerId)
                    .position(containerLayout.x, containerLayout.y)
                    .size(containerLayout.width, containerLayout.height)
                    .addCssClass("interlis-container")
                    .addCssClass("interlis-container-" + kind);

            containerBuilder.add(new GLabelBuilder(DefaultTypes.LABEL)
                    .id(containerId + ":title")
                    .text(firstNonBlank(container.getLabel(), container.getQualifiedName(), containerId))
                    .position(12, 22)
                    .addCssClass("interlis-container-title")
                    .build());

            for (NodeLayout nodeLayout : containerLayout.nodes) {
                containerBuilder.add(buildClassNode(nodeLayout));
            }

            graphBuilder.add(containerBuilder.build());
        }

        // Render edges after container/class nodes so they stay visible above topic backgrounds.
        Set<String> renderableNodeIds = new LinkedHashSet<>(nodesById.keySet());
        boolean showCardinalities = resolveClientSettings().isShowCardinalities();
        int edgeIndex = 0;
        for (InterlisDiagramModel.EdgeModel edge : safeList(diagram.getEdges())) {
            if (edge == null || isBlank(edge.getSourceId()) || isBlank(edge.getTargetId())) {
                continue;
            }
            if (!renderableNodeIds.contains(edge.getSourceId()) || !renderableNodeIds.contains(edge.getTargetId())) {
                continue;
            }

            String edgeId = firstNonBlank(edge.getId(), "edge-" + edgeIndex++);
            GEdgeBuilder edgeBuilder = new GEdgeBuilder(DefaultTypes.EDGE)
                    .id(edgeId)
                    .sourceId(edge.getSourceId())
                    .targetId(edge.getTargetId())
                    .addCssClass("interlis-edge");

            boolean inheritance = "inheritance".equalsIgnoreCase(edge.getType());
            edgeBuilder.addCssClass(inheritance ? "interlis-edge-inheritance" : "interlis-edge-association");

            if (!inheritance) {
                if (showCardinalities && !isBlank(edge.getSourceCardinality())) {
                    edgeBuilder.add(edgeLabel(edgeId + ":source-card", edge.getSourceCardinality(), 0.12, 8,
                            "interlis-edge-cardinality"));
                }
                if (showCardinalities && !isBlank(edge.getTargetCardinality())) {
                    edgeBuilder.add(edgeLabel(edgeId + ":target-card", edge.getTargetCardinality(), 0.88, 8,
                            "interlis-edge-cardinality"));
                }
                if (!isBlank(edge.getLabel())) {
                    edgeBuilder.add(associationLabel(edgeId + ":label", edge.getLabel()));
                }
            }

            graphBuilder.add(edgeBuilder.build());
        }

        return graphBuilder.build();
    }

    private GModelElement buildClassNode(NodeLayout nodeLayout) {
        InterlisDiagramModel.NodeModel node = nodeLayout.node;
        String nodeId = firstNonBlank(node.getId(), "node");

        GNodeBuilder nodeBuilder = new GNodeBuilder(DefaultTypes.NODE)
                .id(nodeId)
                .position(nodeLayout.x, nodeLayout.y)
                .size(nodeLayout.width, nodeLayout.height)
                .addCssClass("interlis-class");

        nodeBuilder.add(new GLabelBuilder(DefaultTypes.LABEL)
                .id(nodeId + ":name")
                .text(firstNonBlank(node.getLabel(), nodeId))
                .position(10, NODE_HEADER_BASELINE)
                .addCssClass("interlis-class-title")
                .build());

        double y = NODE_CONTENT_START;

        for (String stereotype : safeList(node.getStereotypes())) {
            if (isBlank(stereotype)) {
                continue;
            }
            nodeBuilder.add(classLine(nodeId, "stereotype", y, "<<" + stereotype + ">>", "interlis-class-stereotype"));
            y += NODE_LINE_HEIGHT;
        }
        if (!safeList(node.getStereotypes()).isEmpty()) {
            y += SECTION_GAP;
        }

        for (String attribute : safeList(node.getAttributes())) {
            if (isBlank(attribute)) {
                continue;
            }
            nodeBuilder.add(classLine(nodeId, "attribute", y, attribute, "interlis-class-attribute"));
            y += NODE_LINE_HEIGHT;
        }
        if (!safeList(node.getAttributes()).isEmpty() && !safeList(node.getMethods()).isEmpty()) {
            y += SECTION_GAP;
        }

        for (String method : safeList(node.getMethods())) {
            if (isBlank(method)) {
                continue;
            }
            nodeBuilder.add(classLine(nodeId, "method", y, method, "interlis-class-method"));
            y += NODE_LINE_HEIGHT;
        }

        // Keep a small (invisible) compartment to support default node internals in Sprotty.
        nodeBuilder.add(new GCompartmentBuilder(DefaultTypes.COMPARTMENT)
                .id(nodeId + ":body")
                .position(0, 0)
                .size(0, 0)
                .addCssClass("interlis-class-body")
                .build());

        return nodeBuilder.build();
    }

    private GModelElement classLine(String nodeId, String section, double y, String text, String cssClass) {
        return new GLabelBuilder(DefaultTypes.LABEL)
                .id(nodeId + ":" + section + ":" + Math.max(0, (int) y))
                .text(text)
                .position(10, y)
                .addCssClass(cssClass)
                .build();
    }

    private GModelElement edgeLabel(String id, String text, double position, double offset, String cssClass) {
        return new GLabelBuilder(DefaultTypes.LABEL)
                .id(id)
                .text(text)
                .edgePlacement(new GEdgePlacementBuilder()
                        .position(position)
                        .side("top")
                        .offset(offset)
                        .rotate(false)
                        .build())
                .addCssClass(cssClass)
                .build();
    }

    private GModelElement associationLabel(String id, String text) {
        LabelSize size = estimateLabelSize(text);
        return new GLabelBuilder(DefaultTypes.LABEL)
                .id(id)
                .text(text)
                .size(size.width, size.height)
                .addCssClass("interlis-edge-label")
                .build();
    }

    private void applyAssociationLabelPlacementFallback(GGraph graph) {
        if (graph == null) {
            return;
        }

        for (GModelElement element : safeList(graph.getChildren())) {
            if (!(element instanceof GEdge edge) || !hasCssClass(edge, "interlis-edge-association")) {
                continue;
            }

            for (GModelElement child : safeList(edge.getChildren())) {
                if (!(child instanceof GLabel label) || !isAssociationNameLabel(label)) {
                    continue;
                }
                if (hasUsableLabelPlacement(label)) {
                    continue;
                }

                label.setEdgePlacement(new GEdgePlacementBuilder()
                        .position(ASSOCIATION_LABEL_FALLBACK_POSITION)
                        .side("top")
                        .offset(ASSOCIATION_LABEL_FALLBACK_OFFSET)
                        .rotate(false)
                        .build());
            }
        }
    }

    private static boolean isAssociationNameLabel(GLabel label) {
        String id = label.getId();
        return id != null && id.endsWith(":label");
    }

    private static boolean hasUsableLabelPlacement(GLabel label) {
        if (label.getEdgePlacement() != null) {
            return true;
        }
        if (label.getPosition() == null) {
            return false;
        }

        double x = label.getPosition().getX();
        double y = label.getPosition().getY();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return false;
        }

        return Math.abs(x) >= 0.5 || Math.abs(y) >= 0.5;
    }

    private static LabelSize estimateLabelSize(String text) {
        int length = firstNonBlank(text, "").length();
        double width = Math.max(EDGE_LABEL_MIN_WIDTH, length * EDGE_LABEL_CHAR_WIDTH + EDGE_LABEL_PADDING_X * 2);
        width = Math.min(width, EDGE_LABEL_MAX_WIDTH);
        return new LabelSize(width, EDGE_LABEL_HEIGHT);
    }

    private static boolean hasCssClass(GModelElement element, String cssClass) {
        if (element == null || cssClass == null || cssClass.isBlank()) {
            return false;
        }
        return element.getCssClasses() != null && element.getCssClasses().contains(cssClass);
    }

    private static ClientSettings resolveClientSettings() {
        InterlisLanguageServer languageServer = InterlisGlspBridge.getLanguageServer();
        if (languageServer == null) {
            return new ClientSettings();
        }
        ClientSettings settings = languageServer.getClientSettings();
        return settings != null ? settings : new ClientSettings();
    }

    private GGraph buildErrorGraph(String sourceUri, String error) {
        String title = "Diagram model unavailable";
        String details = firstNonBlank(error, "Unknown diagram error");
        String source = "Source: " + firstNonBlank(sourceUri, "unknown");

        GGraphBuilder graphBuilder = new GGraphBuilder(DefaultTypes.GRAPH)
                .id("interlis-graph-error")
                .canvasBounds(0, 0, 980, 220)
                .addCssClass("interlis-graph");

        GNodeBuilder nodeBuilder = new GNodeBuilder(DefaultTypes.NODE)
                .id("interlis-error")
                .position(40, 40)
                .size(900, 140)
                .addCssClass("interlis-error");

        nodeBuilder.add(new GLabelBuilder(DefaultTypes.LABEL)
                .id("interlis-error:title")
                .text(title)
                .position(12, 24)
                .addCssClass("interlis-error-title")
                .build());
        nodeBuilder.add(new GLabelBuilder(DefaultTypes.LABEL)
                .id("interlis-error:message")
                .text(details)
                .position(12, 52)
                .addCssClass("interlis-error-message")
                .build());
        nodeBuilder.add(new GLabelBuilder(DefaultTypes.LABEL)
                .id("interlis-error:source")
                .text(source)
                .position(12, 78)
                .addCssClass("interlis-error-source")
                .build());

        graphBuilder.add(nodeBuilder.build());
        return graphBuilder.build();
    }

    private List<InterlisDiagramModel.ContainerModel> sortedContainers(
            InterlisDiagramModel.DiagramModel diagram,
            Map<String, List<InterlisDiagramModel.NodeModel>> nodesByContainer) {
        List<InterlisDiagramModel.ContainerModel> result = new ArrayList<>();
        Set<String> knownIds = new LinkedHashSet<>();

        for (InterlisDiagramModel.ContainerModel container : safeList(diagram.getContainers())) {
            if (container == null || isBlank(container.getId())) {
                continue;
            }
            result.add(container);
            knownIds.add(container.getId());
        }

        for (Map.Entry<String, List<InterlisDiagramModel.NodeModel>> entry : nodesByContainer.entrySet()) {
            String containerId = entry.getKey();
            if (knownIds.contains(containerId)) {
                continue;
            }
            List<String> nodeIds = entry.getValue().stream()
                    .map(InterlisDiagramModel.NodeModel::getId)
                    .filter(Objects::nonNull)
                    .toList();
            result.add(new InterlisDiagramModel.ContainerModel(
                    containerId,
                    containerId,
                    containerId,
                    "namespace",
                    new ArrayList<>(nodeIds)));
        }

        result.sort(
                Comparator.comparing((InterlisDiagramModel.ContainerModel c) -> isRootKind(c.getKind()) ? 1 : 0)
                        .thenComparing(c -> firstNonBlank(c.getLabel(), c.getId()), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private DiagramLayout computeLayout(
            List<InterlisDiagramModel.ContainerModel> containers,
            Map<String, InterlisDiagramModel.NodeModel> nodesById,
            Map<String, List<InterlisDiagramModel.NodeModel>> nodesByContainer) {
        List<ContainerLayout> containerLayouts = new ArrayList<>();
        double cursorX = OUTER_PADDING;
        double cursorY = OUTER_PADDING;
        double rowHeight = 0;
        double maxX = 0;
        double maxY = 0;

        for (InterlisDiagramModel.ContainerModel container : containers) {
            List<InterlisDiagramModel.NodeModel> nodes = nodesForContainer(container, nodesById, nodesByContainer);

            int columns = columnCount(Math.max(1, nodes.size()));
            int rows = (int) Math.ceil(Math.max(1, nodes.size()) / (double) columns);

            List<Double> rowHeights = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                double maxHeight = NODE_MIN_HEIGHT;
                for (int col = 0; col < columns; col++) {
                    int index = row * columns + col;
                    if (index >= nodes.size()) {
                        break;
                    }
                    maxHeight = Math.max(maxHeight, nodeHeight(nodes.get(index)));
                }
                rowHeights.add(maxHeight);
            }

            double contentWidth = columns * NODE_WIDTH + (columns - 1) * NODE_GAP_X;
            double width = Math.max(CONTAINER_MIN_WIDTH, contentWidth + CONTAINER_PADDING * 2);
            double contentHeight = rowHeights.stream().mapToDouble(Double::doubleValue).sum()
                    + Math.max(0, rowHeights.size() - 1) * NODE_GAP_Y;
            double height = CONTAINER_HEADER_HEIGHT + CONTAINER_PADDING * 2 + Math.max(contentHeight, NODE_MIN_HEIGHT);

            if (cursorX > OUTER_PADDING && cursorX + width + OUTER_PADDING > MAX_ROW_WIDTH) {
                cursorX = OUTER_PADDING;
                cursorY += rowHeight + CONTAINER_GAP;
                rowHeight = 0;
            }

            ContainerLayout containerLayout = new ContainerLayout(container, cursorX, cursorY, width, height);
            double localY = CONTAINER_HEADER_HEIGHT + CONTAINER_PADDING;
            for (int row = 0; row < rows; row++) {
                double currentRowHeight = rowHeights.get(row);
                for (int col = 0; col < columns; col++) {
                    int index = row * columns + col;
                    if (index >= nodes.size()) {
                        break;
                    }
                    InterlisDiagramModel.NodeModel node = nodes.get(index);
                    double nodeHeight = nodeHeight(node);
                    double localX = CONTAINER_PADDING + col * (NODE_WIDTH + NODE_GAP_X);
                    double y = localY + (currentRowHeight - nodeHeight) / 2d;
                    containerLayout.nodes.add(new NodeLayout(node, localX, y, NODE_WIDTH, nodeHeight));
                }
                localY += currentRowHeight + NODE_GAP_Y;
            }

            containerLayouts.add(containerLayout);

            cursorX += width + CONTAINER_GAP;
            rowHeight = Math.max(rowHeight, height);
            maxX = Math.max(maxX, containerLayout.x + containerLayout.width);
            maxY = Math.max(maxY, containerLayout.y + containerLayout.height);
        }

        double canvasWidth = Math.max(1100, maxX + OUTER_PADDING);
        double canvasHeight = Math.max(760, maxY + OUTER_PADDING);
        return new DiagramLayout(containerLayouts, canvasWidth, canvasHeight);
    }

    private List<InterlisDiagramModel.NodeModel> nodesForContainer(
            InterlisDiagramModel.ContainerModel container,
            Map<String, InterlisDiagramModel.NodeModel> nodesById,
            Map<String, List<InterlisDiagramModel.NodeModel>> nodesByContainer) {
        List<InterlisDiagramModel.NodeModel> result = new ArrayList<>();

        for (String nodeId : safeList(container.getNodeIds())) {
            InterlisDiagramModel.NodeModel node = nodesById.get(nodeId);
            if (node != null) {
                result.add(node);
            }
        }

        if (result.isEmpty()) {
            result.addAll(safeList(nodesByContainer.get(container.getId())));
        }

        if (result.isEmpty()) {
            return result;
        }

        // Preserve container-defined order if it exists, otherwise sort deterministically.
        if (safeList(container.getNodeIds()).isEmpty()) {
            result.sort(Comparator.comparing(
                    node -> firstNonBlank(node.getLabel(), node.getId()),
                    String.CASE_INSENSITIVE_ORDER));
        }
        return result;
    }

    private static int columnCount(int nodeCount) {
        if (nodeCount <= 4) {
            return 1;
        }
        if (nodeCount <= 10) {
            return 2;
        }
        return 3;
    }

    private static double nodeHeight(InterlisDiagramModel.NodeModel node) {
        int stereotypes = safeList(node.getStereotypes()).size();
        int attributes = safeList(node.getAttributes()).size();
        int methods = safeList(node.getMethods()).size();

        int lines = stereotypes + attributes + methods;
        double height = NODE_CONTENT_START + Math.max(lines, 1) * NODE_LINE_HEIGHT + 10;

        if (stereotypes > 0) {
            height += SECTION_GAP;
        }
        if (attributes > 0 && methods > 0) {
            height += SECTION_GAP;
        }

        return Math.max(NODE_MIN_HEIGHT, height);
    }

    private static <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String sanitizeCss(String value) {
        String input = firstNonBlank(value, "unknown");
        return input
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static boolean isRootKind(String kind) {
        return "root".equalsIgnoreCase(firstNonBlank(kind, ""));
    }

    private static final class LabelSize {
        final double width;
        final double height;

        LabelSize(double width, double height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class DiagramLayout {
        final List<ContainerLayout> containers;
        final double canvasWidth;
        final double canvasHeight;

        DiagramLayout(List<ContainerLayout> containers, double canvasWidth, double canvasHeight) {
            this.containers = containers;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
        }
    }

    private static final class ContainerLayout {
        final InterlisDiagramModel.ContainerModel container;
        final double x;
        final double y;
        final double width;
        final double height;
        final List<NodeLayout> nodes = new ArrayList<>();

        ContainerLayout(InterlisDiagramModel.ContainerModel container, double x, double y, double width, double height) {
            this.container = container;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final class NodeLayout {
        final InterlisDiagramModel.NodeModel node;
        final double x;
        final double y;
        final double width;
        final double height;

        NodeLayout(InterlisDiagramModel.NodeModel node, double x, double y, double width, double height) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
