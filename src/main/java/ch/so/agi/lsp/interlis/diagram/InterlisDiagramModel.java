package ch.so.agi.lsp.interlis.diagram;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Assoc;
import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Diagram;
import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Inheritance;
import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Namespace;
import ch.so.agi.lsp.interlis.diagram.InterlisUmlDiagram.Node;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Export a UI-agnostic diagram model for read-only diagram clients.
 */
public final class InterlisDiagramModel {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisDiagramModel.class);
    private static final String ROOT_NAMESPACE = "<root>";
    private static final String SCHEMA_VERSION = "1";
    private static final String DEBUG_FILE_PROPERTY = "interlis.glsp.debugFile";
    private static final Gson DEBUG_JSON = new GsonBuilder().setPrettyPrinting().create();

    private InterlisDiagramModel() {
    }

    public static DiagramModel render(TransferDescription transferDescription) {
        Objects.requireNonNull(transferDescription, "TransferDescription is null");
        Diagram source = InterlisUmlDiagram.build(transferDescription);

        Map<String, ContainerModel> containersByNamespace = new LinkedHashMap<>();
        for (Namespace namespace : source.namespaces.values()) {
            String namespaceLabel = namespace.label != null ? namespace.label : ROOT_NAMESPACE;
            String containerId = containerIdForNamespace(namespaceLabel);
            String kind = kindForNamespace(namespaceLabel);
            String label = labelForNamespace(namespaceLabel);
            containersByNamespace.put(namespaceLabel,
                    new ContainerModel(containerId, label, namespaceLabel, kind, new ArrayList<>()));
        }

        // Ensure root container always exists.
        containersByNamespace.computeIfAbsent(ROOT_NAMESPACE, ns -> new ContainerModel(
                containerIdForNamespace(ROOT_NAMESPACE),
                "Model Scope",
                ROOT_NAMESPACE,
                "root",
                new ArrayList<>()));

        Map<String, String> nodeToContainer = new LinkedHashMap<>();
        for (Namespace namespace : source.namespaces.values()) {
            String namespaceLabel = namespace.label != null ? namespace.label : ROOT_NAMESPACE;
            ContainerModel container = containersByNamespace.get(namespaceLabel);
            if (container == null) {
                continue;
            }
            for (String nodeId : namespace.nodeOrder) {
                if (!source.nodes.containsKey(nodeId)) {
                    continue;
                }
                if (nodeToContainer.putIfAbsent(nodeId, container.getId()) == null) {
                    container.getNodeIds().add(nodeId);
                }
            }
        }

        ContainerModel rootContainer = containersByNamespace.get(ROOT_NAMESPACE);
        for (Node node : source.nodes.values()) {
            if (node == null || node.fqn == null) {
                continue;
            }
            if (!nodeToContainer.containsKey(node.fqn)) {
                nodeToContainer.put(node.fqn, rootContainer.getId());
                rootContainer.getNodeIds().add(node.fqn);
            }
        }

        List<NodeModel> nodes = new ArrayList<>(source.nodes.size());
        for (Node node : source.nodes.values()) {
            if (node == null || node.fqn == null) {
                continue;
            }
            String containerId = nodeToContainer.get(node.fqn);
            if (containerId == null) {
                containerId = rootContainer.getId();
            }

            List<String> stereotypes = new ArrayList<>(node.stereotypes);
            List<String> attributes = new ArrayList<>(node.attributes);
            List<String> methods = new ArrayList<>(node.methods);

            nodes.add(new NodeModel(node.fqn, node.displayName, containerId, stereotypes, attributes, methods));
        }

        List<EdgeModel> edges = new ArrayList<>(source.inheritances.size() + source.assocs.size());
        for (Inheritance inheritance : source.inheritances) {
            if (inheritance == null || inheritance.subFqn == null || inheritance.supFqn == null) {
                continue;
            }
            edges.add(new EdgeModel(
                    edgeId("inheritance", inheritance.subFqn + "->" + inheritance.supFqn),
                    "inheritance",
                    inheritance.subFqn,
                    inheritance.supFqn,
                    null,
                    null,
                    null));
        }

        int assocIndex = 0;
        for (Assoc assoc : source.assocs) {
            if (assoc == null || assoc.leftFqn == null || assoc.rightFqn == null) {
                continue;
            }
            String assocKey = assoc.leftFqn + "->" + assoc.rightFqn + "#" + assocIndex++;
            edges.add(new EdgeModel(
                    edgeId("association", assocKey),
                    "association",
                    assoc.leftFqn,
                    assoc.rightFqn,
                    assoc.leftCard,
                    assoc.rightCard,
                    assoc.label));
        }

        List<ContainerModel> containers = new ArrayList<>(containersByNamespace.values());
        DiagramModel result = new DiagramModel(SCHEMA_VERSION, containers, nodes, edges);
        writeDebugDumpIfEnabled(source, result);
        return result;
    }

    private static String containerIdForNamespace(String namespaceLabel) {
        return edgeId("container", namespaceLabel);
    }

    private static String edgeId(String prefix, String value) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return prefix + ":" + encoded;
    }

    private static String kindForNamespace(String namespaceLabel) {
        if (ROOT_NAMESPACE.equals(namespaceLabel)) {
            return "root";
        }
        if (namespaceLabel.contains("::")) {
            return "topic";
        }
        return "namespace";
    }

    private static String labelForNamespace(String namespaceLabel) {
        if (ROOT_NAMESPACE.equals(namespaceLabel)) {
            return "Model Scope";
        }
        int idx = namespaceLabel.indexOf("::");
        if (idx < 0) {
            return namespaceLabel;
        }
        String model = namespaceLabel.substring(0, idx);
        String topic = namespaceLabel.substring(idx + 2);
        if (model.isBlank()) {
            return topic;
        }
        if (topic.isBlank()) {
            return model;
        }
        return topic + " (" + model + ")";
    }

    private static void writeDebugDumpIfEnabled(Diagram source, DiagramModel result) {
        String debugFile = System.getProperty(DEBUG_FILE_PROPERTY);
        if (debugFile == null || debugFile.isBlank()) {
            return;
        }

        try {
            Path target = Path.of(debugFile);
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("generatedAt", Instant.now().toString());

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sourceNamespaces", source != null ? source.namespaces.size() : 0);
            summary.put("sourceNodes", source != null ? source.nodes.size() : 0);
            summary.put("sourceInheritances", source != null ? source.inheritances.size() : 0);
            summary.put("sourceAssociations", source != null ? source.assocs.size() : 0);
            summary.put("containers", result != null ? result.getContainers().size() : 0);
            summary.put("nodes", result != null ? result.getNodes().size() : 0);
            summary.put("edges", result != null ? result.getEdges().size() : 0);
            payload.put("summary", summary);

            payload.put("source", source);
            payload.put("diagramModel", result);

            Files.writeString(
                    target,
                    DEBUG_JSON.toJson(payload),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            LOG.info("Wrote GLSP diagram debug dump to {}", target);
        } catch (Exception ex) {
            LOG.warn("Failed to write GLSP diagram debug dump.", ex);
        }
    }

    public static final class DiagramModel {
        private final String schemaVersion;
        private final List<ContainerModel> containers;
        private final List<NodeModel> nodes;
        private final List<EdgeModel> edges;

        public DiagramModel(String schemaVersion, List<ContainerModel> containers, List<NodeModel> nodes,
                List<EdgeModel> edges) {
            this.schemaVersion = schemaVersion;
            this.containers = containers != null ? containers : List.of();
            this.nodes = nodes != null ? nodes : List.of();
            this.edges = edges != null ? edges : List.of();
        }

        public String getSchemaVersion() {
            return schemaVersion;
        }

        public List<ContainerModel> getContainers() {
            return containers;
        }

        public List<NodeModel> getNodes() {
            return nodes;
        }

        public List<EdgeModel> getEdges() {
            return edges;
        }
    }

    public static final class ContainerModel {
        private final String id;
        private final String label;
        private final String qualifiedName;
        private final String kind;
        private final List<String> nodeIds;

        public ContainerModel(String id, String label, String qualifiedName, String kind, List<String> nodeIds) {
            this.id = id;
            this.label = label;
            this.qualifiedName = qualifiedName;
            this.kind = kind;
            this.nodeIds = nodeIds != null ? nodeIds : List.of();
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getQualifiedName() {
            return qualifiedName;
        }

        public String getKind() {
            return kind;
        }

        public List<String> getNodeIds() {
            return nodeIds;
        }
    }

    public static final class NodeModel {
        private final String id;
        private final String label;
        private final String containerId;
        private final List<String> stereotypes;
        private final List<String> attributes;
        private final List<String> methods;

        public NodeModel(String id, String label, String containerId, List<String> stereotypes, List<String> attributes,
                List<String> methods) {
            this.id = id;
            this.label = label;
            this.containerId = containerId;
            this.stereotypes = stereotypes != null ? stereotypes : List.of();
            this.attributes = attributes != null ? attributes : List.of();
            this.methods = methods != null ? methods : List.of();
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getContainerId() {
            return containerId;
        }

        public List<String> getStereotypes() {
            return stereotypes;
        }

        public List<String> getAttributes() {
            return attributes;
        }

        public List<String> getMethods() {
            return methods;
        }
    }

    public static final class EdgeModel {
        private final String id;
        private final String type;
        private final String sourceId;
        private final String targetId;
        private final String sourceCardinality;
        private final String targetCardinality;
        private final String label;

        public EdgeModel(String id, String type, String sourceId, String targetId, String sourceCardinality,
                String targetCardinality, String label) {
            this.id = id;
            this.type = type;
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.sourceCardinality = sourceCardinality;
            this.targetCardinality = targetCardinality;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getTargetId() {
            return targetId;
        }

        public String getSourceCardinality() {
            return sourceCardinality;
        }

        public String getTargetCardinality() {
            return targetCardinality;
        }

        public String getLabel() {
            return label;
        }
    }
}
