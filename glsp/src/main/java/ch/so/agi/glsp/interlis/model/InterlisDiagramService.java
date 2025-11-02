package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Constraint;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumTreeValueType;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiAreaType;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.ObjectType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.PredefinedModel;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextOIDType;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.so.agi.glsp.interlis.InterlisGlspTypes;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.glsp.graph.GEdge;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.graph.builder.impl.GCompartmentBuilder;
import org.eclipse.glsp.graph.builder.impl.GEdgeBuilder;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles INTERLIS source models and transforms the {@link TransferDescription}
 * into a GLSP graph that renders INTERLIS classes as nodes.
 */
public class InterlisDiagramService {

    private static final Logger LOG = LoggerFactory.getLogger(InterlisDiagramService.class);

    private static final double NODE_WIDTH = 260;
    private static final double NODE_HEIGHT = 168;
    private static final double COLUMN_GAP = 180;
    private static final double ROW_GAP = 64;
    private static final double GRID_START_X = 80;
    private static final double GRID_START_Y = 80;

    private final InterlisIli2cCompiler compiler;

    /**
     * Creates a diagram service that uses the default ili2c compiler wrapper.
     */
    @Inject
    public InterlisDiagramService() {
        this(new InterlisIli2cCompiler());
    }

    /**
     * Creates a service that uses the provided compiler helper. Visible for
     * testing.
     *
     * @param compiler the compiler helper used to turn source files into
     *        {@link TransferDescription}s
     */
    public InterlisDiagramService(final InterlisIli2cCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    /**
     * Attempts to build a GLSP graph for the given INTERLIS source file.
     *
     * @param sourceFile the *.ili file that should be rendered
     * @return a populated graph or {@link Optional#empty()} if the model could
     *         not be compiled
     */
    public Optional<GGraph> loadDiagram(final Path sourceFile) {
        if (sourceFile == null) {
            return Optional.empty();
        }

        try {
            TransferDescription td = compiler.compile(sourceFile);
            if (td == null) {
                return Optional.empty();
            }

            DiagramData diagram = extractDiagram(td);
            return Optional.of(buildGraph(diagram));
        } catch (IOException ex) {
            LOG.warn("Failed to compile INTERLIS model '{}': {}", sourceFile, ex.getMessage());
            LOG.debug("Compilation failure", ex);
            return Optional.empty();
        }
    }

    private GGraph buildGraph(final DiagramData data) {
        GGraphBuilder graphBuilder = new GGraphBuilder()
            .id(InterlisGlspTypes.GRAPH_ID)
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .layoutOptions(Map.of("padding", "24"));

        List<NamespaceInfo> namespaces = new ArrayList<>(data.namespaces().values());
        namespaces.sort(Comparator.comparing(NamespaceInfo::label, String.CASE_INSENSITIVE_ORDER));

        int columnIndex = 0;
        for (NamespaceInfo namespace : namespaces) {
            List<NodeInfo> nodes = namespace.nodeOrder().stream()
                .map(data.nodes()::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            if (nodes.isEmpty()) {
                continue;
            }

            for (int rowIndex = 0; rowIndex < nodes.size(); rowIndex++) {
                NodeInfo info = nodes.get(rowIndex);
                double x = GRID_START_X + columnIndex * (NODE_WIDTH + COLUMN_GAP);
                double y = GRID_START_Y + rowIndex * (NODE_HEIGHT + ROW_GAP);
                graphBuilder.add(buildNode(info, x, y));
            }
            columnIndex++;
        }

        data.inheritances().forEach(inheritance -> graphBuilder.add(buildInheritanceEdge(inheritance)));
        data.associations().forEach(association -> graphBuilder.add(buildAssociationEdge(association)));

        return graphBuilder.build();
    }

    private GNode buildNode(final NodeInfo info, final double x, final double y) {
        GNodeBuilder builder = new GNodeBuilder()
            .id(info.id())
            .type(info.type())
            .position(x, y)
            .size(NODE_WIDTH, NODE_HEIGHT)
            .layout("vbox")
            .addCssClass(InterlisGlspTypes.CSS_CLASS_NODE);

        builder.addCssClass(switch (info.kind()) {
            case CLASS -> InterlisGlspTypes.CSS_CLASS_CLASS;
            case STRUCTURE -> InterlisGlspTypes.CSS_CLASS_STRUCTURE;
            case VIEW -> InterlisGlspTypes.CSS_CLASS_VIEW;
            case ENUMERATION -> InterlisGlspTypes.CSS_CLASS_ENUMERATION;
        });

        GCompartmentBuilder header = new GCompartmentBuilder()
            .id(info.id() + "-header")
            .type(InterlisGlspTypes.HEADER_COMPARTMENT_TYPE)
            .layout("vbox")
            .addCssClass(InterlisGlspTypes.CSS_CLASS_COMPARTMENT)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_HEADER);

        if (!info.namespaceLabel().isBlank()) {
            header.add(new GLabelBuilder()
                .id(info.id() + "-namespace")
                .type(InterlisGlspTypes.NAMESPACE_LABEL_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_NAMESPACE)
                .text(info.namespaceLabel())
                .build());
        }

        for (int i = 0; i < info.stereotypes().size(); i++) {
            String stereotype = info.stereotypes().get(i);
            header.add(new GLabelBuilder()
                .id(info.id() + "-stereotype-" + i)
                .type(InterlisGlspTypes.STEREOTYPE_LABEL_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_STEREOTYPE)
                .text("<<" + stereotype + ">>")
                .build());
        }

        header.add(new GLabelBuilder()
            .id(info.id() + "-name")
            .type(InterlisGlspTypes.NAME_LABEL_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_NAME)
            .text(info.displayName())
            .build());

        builder.add(header.build());

        if (!info.attributes().isEmpty()) {
            GCompartmentBuilder attributes = new GCompartmentBuilder()
                .id(info.id() + "-attributes")
                .type(InterlisGlspTypes.ATTRIBUTE_COMPARTMENT_TYPE)
                .layout("vbox")
                .addCssClass(InterlisGlspTypes.CSS_CLASS_COMPARTMENT)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_ATTRIBUTE_COMPARTMENT);

            for (int i = 0; i < info.attributes().size(); i++) {
                AttributeInfo attribute = info.attributes().get(i);
                GLabelBuilder attributeLabel = new GLabelBuilder()
                    .id(info.id() + "-attribute-" + i)
                    .type(InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_ATTRIBUTE)
                    .text(attribute.text());
                if (attribute.kind() == AttributeKind.ENUM_LITERAL) {
                    attributeLabel.addCssClass(InterlisGlspTypes.CSS_CLASS_ENUM_LITERAL);
                }
                attributes.add(attributeLabel.build());
            }
            builder.add(attributes.build());
        }

        if (!info.constraints().isEmpty()) {
            GCompartmentBuilder constraints = new GCompartmentBuilder()
                .id(info.id() + "-constraints")
                .type(InterlisGlspTypes.CONSTRAINT_COMPARTMENT_TYPE)
                .layout("vbox")
                .addCssClass(InterlisGlspTypes.CSS_CLASS_COMPARTMENT)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_CONSTRAINT_COMPARTMENT);

            for (int i = 0; i < info.constraints().size(); i++) {
                constraints.add(new GLabelBuilder()
                    .id(info.id() + "-constraint-" + i)
                    .type(InterlisGlspTypes.CONSTRAINT_LABEL_TYPE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_CONSTRAINT)
                    .text(info.constraints().get(i))
                    .build());
            }
            builder.add(constraints.build());
        }

        return builder.build();
    }

    private GEdge buildInheritanceEdge(final InheritanceInfo info) {
        return new GEdgeBuilder()
            .id(info.id())
            .type(InterlisGlspTypes.INHERITANCE_EDGE_TYPE)
            .sourceId(info.subId())
            .targetId(info.supId())
            .addCssClass(InterlisGlspTypes.CSS_CLASS_INHERITANCE_EDGE)
            .build();
    }

    private GEdge buildAssociationEdge(final AssociationInfo info) {
        GEdgeBuilder builder = new GEdgeBuilder()
            .id(info.id())
            .type(InterlisGlspTypes.ASSOCIATION_EDGE_TYPE)
            .sourceId(info.leftId())
            .targetId(info.rightId())
            .addCssClass(InterlisGlspTypes.CSS_CLASS_ASSOCIATION_EDGE);

        if (!info.label().isBlank()) {
            builder.add(new GLabelBuilder()
                .id(info.id() + "-label")
                .type(InterlisGlspTypes.ASSOCIATION_LABEL_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_ASSOCIATION_LABEL)
                .text(info.label())
                .build());
        }

        return builder.build();
    }

    private DiagramData extractDiagram(final TransferDescription td) {
        Map<String, NamespaceInfo> namespaces = new LinkedHashMap<>();
        Map<String, NodeInfo> nodes = new LinkedHashMap<>();
        Map<String, Viewable> viewablesByFqn = new LinkedHashMap<>();

        List<InheritanceInfo> inheritances = new ArrayList<>();
        List<AssociationInfo> associations = new ArrayList<>();

        Model[] models = Optional.ofNullable(td.getModelsFromLastFile()).orElse(new Model[0]);
        List<Model> orderedModels = Arrays.stream(models)
            .sorted(Comparator.comparing(model -> caseInsensitive(model.getName())))
            .collect(Collectors.toList());

        for (Model model : orderedModels) {
            collectViewables(namespaces, nodes, viewablesByFqn, model, model);
            collectDomains(namespaces, nodes, model, model);
            for (Topic topic : getElements(model, Topic.class)) {
                collectViewables(namespaces, nodes, viewablesByFqn, model, topic);
                collectDomains(namespaces, nodes, model, topic);
            }
        }

        for (Map.Entry<String, Viewable> entry : viewablesByFqn.entrySet()) {
            String childFqn = entry.getKey();
            Viewable viewable = entry.getValue();
            if (viewable instanceof Table table) {
                Element base = table.getExtending();
                if (base instanceof Table parent) {
                    String parentFqn = fqnOf(parent);
                    NodeInfo child = nodes.get(childFqn);
                    NodeInfo sup = nodes.get(parentFqn);
                    if (child != null && sup != null) {
                        inheritances.add(new InheritanceInfo(edgeId("inheritance", child.id(), sup.id()), child.id(),
                            sup.id()));
                    }
                }
            }
        }

        for (Model model : orderedModels) {
            collectAssociations(associations, nodes, model, model);
            for (Topic topic : getElements(model, Topic.class)) {
                collectAssociations(associations, nodes, model, topic);
            }
        }

        inheritances.sort(Comparator.comparing(InheritanceInfo::subId).thenComparing(InheritanceInfo::supId));
        associations.sort(Comparator.comparing(AssociationInfo::leftId).thenComparing(AssociationInfo::rightId));

        return new DiagramData(namespaces, nodes, inheritances, associations);
    }

    private void collectViewables(final Map<String, NamespaceInfo> namespaces, final Map<String, NodeInfo> nodes,
        final Map<String, Viewable> viewablesByFqn, final Model model, final Container container) {

        String namespaceLabel = namespaceLabel(model, container);
        NamespaceInfo namespace = namespaces.computeIfAbsent(namespaceLabel, NamespaceInfo::new);

        for (Viewable viewable : getElements(container, Viewable.class)) {
            if (viewable instanceof AssociationDef) {
                continue;
            }

            String fqn = fqnOf(model, container, viewable);
            NodeKind kind = determineKind(viewable);

            List<String> stereotypes = new ArrayList<>();
            if (viewable.isAbstract()) {
                stereotypes.add("Abstract");
            }
            if (kind == NodeKind.STRUCTURE) {
                stereotypes.add("Structure");
            } else if (kind == NodeKind.VIEW) {
                stereotypes.add("View");
            }

            List<AttributeInfo> attributes = new ArrayList<>();
            for (AttributeDef attribute : getElements(viewable, AttributeDef.class)) {
                String card = formatCardinality(attribute.getCardinality());
                String typeName = TypeNamer.nameOf(attribute);
                if (!"ObjectType".equalsIgnoreCase(typeName)) {
                    String text = attribute.getName() + " [" + card + "] : " + typeName;
                    attributes.add(new AttributeInfo(text, AttributeKind.FIELD));
                }
            }

            List<String> constraints = new ArrayList<>();
            int constraintIndex = 1;
            for (Constraint constraint : getElements(viewable, Constraint.class)) {
                String name = constraint.getName();
                if (name == null || name.isEmpty()) {
                    name = "constraint" + constraintIndex++;
                }
                constraints.add(name + "()");
            }

            NodeInfo node = new NodeInfo(fqn, idFromFqn(fqn), kind, namespaceLabel, viewable.getName(), stereotypes,
                attributes, constraints);
            nodes.put(fqn, node);
            namespace.nodeOrder().add(fqn);
            viewablesByFqn.put(fqn, viewable);
        }
    }

    private void collectDomains(final Map<String, NamespaceInfo> namespaces, final Map<String, NodeInfo> nodes,
        final Model model, final Container container) {

        String namespaceLabel = namespaceLabel(model, container);
        NamespaceInfo namespace = namespaces.computeIfAbsent(namespaceLabel, NamespaceInfo::new);

        for (Domain domain : getElements(container, Domain.class)) {
            Type type = domain.getType();
            if (type instanceof EnumerationType || type instanceof EnumTreeValueType) {
                String fqn = fqnOf(model, container, domain);
                List<AttributeInfo> literals = collectEnumerationValues((AbstractEnumerationType) type).stream()
                    .map(value -> new AttributeInfo(value, AttributeKind.ENUM_LITERAL))
                    .collect(Collectors.toList());

                List<String> stereotypes = List.of("Enumeration");
                NodeInfo node = new NodeInfo(fqn, idFromFqn(fqn), NodeKind.ENUMERATION, namespaceLabel,
                    domain.getName(), stereotypes, literals, List.of());
                nodes.put(fqn, node);
                namespace.nodeOrder().add(fqn);
            }
        }
    }

    private void collectAssociations(final List<AssociationInfo> associations, final Map<String, NodeInfo> nodes,
        final Model model, final Container container) {

        for (AssociationDef association : getElements(container, AssociationDef.class)) {
            List<RoleDef> roles = association.getRoles();
            if (roles == null || roles.size() != 2) {
                continue;
            }

            RoleDef leftRole = roles.get(0);
            RoleDef rightRole = roles.get(1);

            AbstractClassDef leftClass = leftRole.getDestination();
            AbstractClassDef rightClass = rightRole.getDestination();
            if (!(leftClass instanceof Table) || !(rightClass instanceof Table)) {
                continue;
            }

            String leftFqn = fqnOf((Table) leftClass);
            String rightFqn = fqnOf((Table) rightClass);

            NodeInfo leftNode = nodes.get(leftFqn);
            NodeInfo rightNode = nodes.get(rightFqn);
            if (leftNode == null || rightNode == null) {
                continue;
            }

            String leftCard = formatCardinality(leftRole.getCardinality());
            String rightCard = formatCardinality(rightRole.getCardinality());

            String label = roleLabel(leftRole) + " [" + leftCard + "] \u2194 " + roleLabel(rightRole) + " ["
                + rightCard + "]";

            associations.add(new AssociationInfo(edgeId("association", leftNode.id(), rightNode.id()), leftNode.id(),
                rightNode.id(), label));
        }
    }

    private static NodeKind determineKind(final Viewable viewable) {
        if (viewable instanceof Table table) {
            return table.isIdentifiable() ? NodeKind.CLASS : NodeKind.STRUCTURE;
        }
        return NodeKind.VIEW;
    }

    private static String namespaceLabel(final Model model, final Container container) {
        if (container instanceof Topic topic) {
            return model.getName() + "::" + topic.getName();
        }
        return model.getName();
    }

    private static <T extends Element> List<T> getElements(final Container container, final Class<T> type) {
        if (container == null) {
            return List.of();
        }
        List<T> elements = new ArrayList<>();
        Iterator<?> iterator = container.iterator();
        while (iterator.hasNext()) {
            Object child = iterator.next();
            if (type.isInstance(child)) {
                elements.add(type.cast(child));
            }
        }
        elements.sort(Comparator.comparing(element -> caseInsensitive(((Element) element).getName())));
        return elements;
    }

    private static String fqnOf(final Table table) {
        return fqnOf(modelOf(table), containerOf(table), table);
    }

    private static String fqnOf(final Model model, final Container container, final Element element) {
        if (model == null || element == null) {
            return "";
        }
        if (container instanceof Topic topic) {
            return model.getName() + "::" + topic.getName() + "." + element.getName();
        }
        return model.getName() + "." + element.getName();
    }

    private static Model modelOf(final Element element) {
        Element current = element;
        while (current != null && !(current instanceof Model)) {
            current = current.getContainer();
        }
        return (Model) current;
    }

    private static Container containerOf(final Element element) {
        Element container = element.getContainer();
        if (container instanceof Container) {
            return (Container) container;
        }
        return null;
    }

    private static String roleLabel(final RoleDef role) {
        String name = role.getName();
        return (name == null || name.isEmpty()) ? "role" : name;
    }

    private static String edgeId(final String prefix, final String sourceId, final String targetId) {
        return prefix + "-" + sanitize(sourceId + "-" + targetId);
    }

    private static String idFromFqn(final String fqn) {
        return "interlis-node-" + sanitize(fqn);
    }

    private static String sanitize(final String value) {
        return Optional.ofNullable(value)
            .map(v -> v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"))
            .orElse("id");
    }

    private static String caseInsensitive(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String formatCardinality(final Cardinality cardinality) {
        if (cardinality == null) {
            return "1";
        }
        long min = cardinality.getMinimum();
        long max = cardinality.getMaximum();
        String left = String.valueOf(min);
        String right = (max == Long.MAX_VALUE) ? "*" : String.valueOf(max);
        if (max >= 0 && min == max) {
            return String.valueOf(min);
        }
        return left + ".." + right;
    }

    private static List<String> collectEnumerationValues(final AbstractEnumerationType enumerationType) {
        List<String> values = new ArrayList<>();
        if (enumerationType == null) {
            return values;
        }
        Enumeration enumeration = enumerationType.getConsolidatedEnumeration();
        if (enumeration != null) {
            boolean includeIntermediate = enumerationType instanceof EnumTreeValueType;
            appendEnumerationValues(values, "", enumeration, includeIntermediate);
        }
        return values;
    }

    private static void appendEnumerationValues(final List<String> target, final String prefix,
        final Enumeration enumeration, final boolean includeIntermediate) {
        if (enumeration == null) {
            return;
        }
        for (Iterator<Enumeration.Element> it = enumeration.getElements(); it != null && it.hasNext();) {
            Enumeration.Element element = it.next();
            if (element == null) {
                continue;
            }
            String name = element.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String value = prefix.isEmpty() ? name : prefix + "." + name;
            Enumeration sub = element.getSubEnumeration();
            boolean hasChildren = sub != null && sub.size() > 0;
            if (!hasChildren || includeIntermediate) {
                target.add(value);
            }
            if (hasChildren) {
                appendEnumerationValues(target, value, sub, includeIntermediate);
            }
        }
    }

    private static final class DiagramData {
        private final Map<String, NamespaceInfo> namespaces;
        private final Map<String, NodeInfo> nodes;
        private final List<InheritanceInfo> inheritances;
        private final List<AssociationInfo> associations;

        DiagramData(final Map<String, NamespaceInfo> namespaces, final Map<String, NodeInfo> nodes,
            final List<InheritanceInfo> inheritances, final List<AssociationInfo> associations) {
            this.namespaces = namespaces;
            this.nodes = nodes;
            this.inheritances = inheritances;
            this.associations = associations;
        }

        Map<String, NamespaceInfo> namespaces() {
            return namespaces;
        }

        Map<String, NodeInfo> nodes() {
            return nodes;
        }

        List<InheritanceInfo> inheritances() {
            return inheritances;
        }

        List<AssociationInfo> associations() {
            return associations;
        }
    }

    private static final class NamespaceInfo {
        private final String label;
        private final List<String> nodeOrder = new ArrayList<>();

        NamespaceInfo(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        List<String> nodeOrder() {
            return nodeOrder;
        }
    }

    private static final class NodeInfo {
        private final String fqn;
        private final String id;
        private final NodeKind kind;
        private final String namespaceLabel;
        private final String displayName;
        private final List<String> stereotypes;
        private final List<AttributeInfo> attributes;
        private final List<String> constraints;

        NodeInfo(final String fqn, final String id, final NodeKind kind, final String namespaceLabel,
            final String displayName, final List<String> stereotypes, final List<AttributeInfo> attributes,
            final List<String> constraints) {
            this.fqn = fqn;
            this.id = id;
            this.kind = kind;
            this.namespaceLabel = namespaceLabel;
            this.displayName = displayName;
            this.stereotypes = List.copyOf(stereotypes);
            this.attributes = List.copyOf(attributes);
            this.constraints = List.copyOf(constraints);
        }

        String fqn() {
            return fqn;
        }

        String id() {
            return id;
        }

        NodeKind kind() {
            return kind;
        }

        String namespaceLabel() {
            return namespaceLabel;
        }

        String displayName() {
            return displayName;
        }

        List<String> stereotypes() {
            return stereotypes;
        }

        List<AttributeInfo> attributes() {
            return attributes;
        }

        List<String> constraints() {
            return constraints;
        }

        String type() {
            return switch (kind) {
                case CLASS -> InterlisGlspTypes.CLASS_NODE_TYPE;
                case STRUCTURE -> InterlisGlspTypes.STRUCTURE_NODE_TYPE;
                case VIEW -> InterlisGlspTypes.VIEW_NODE_TYPE;
                case ENUMERATION -> InterlisGlspTypes.ENUMERATION_NODE_TYPE;
            };
        }
    }

    private static final class AttributeInfo {
        private final String text;
        private final AttributeKind kind;

        AttributeInfo(final String text, final AttributeKind kind) {
            this.text = text;
            this.kind = kind;
        }

        String text() {
            return text;
        }

        AttributeKind kind() {
            return kind;
        }
    }

    private static final class InheritanceInfo {
        private final String id;
        private final String subId;
        private final String supId;

        InheritanceInfo(final String id, final String subId, final String supId) {
            this.id = id;
            this.subId = subId;
            this.supId = supId;
        }

        String id() {
            return id;
        }

        String subId() {
            return subId;
        }

        String supId() {
            return supId;
        }
    }

    private static final class AssociationInfo {
        private final String id;
        private final String leftId;
        private final String rightId;
        private final String label;

        AssociationInfo(final String id, final String leftId, final String rightId, final String label) {
            this.id = id;
            this.leftId = leftId;
            this.rightId = rightId;
            this.label = label;
        }

        String id() {
            return id;
        }

        String leftId() {
            return leftId;
        }

        String rightId() {
            return rightId;
        }

        String label() {
            return label;
        }
    }

    private enum AttributeKind {
        FIELD,
        ENUM_LITERAL
    }

    private enum NodeKind {
        CLASS,
        STRUCTURE,
        VIEW,
        ENUMERATION
    }

    private static final class TypeNamer {
        static String nameOf(final AttributeDef attribute) {
            Type type = attribute.getDomain();
            if (type == null) {
                return "<Unknown>";
            }
            if (type instanceof ObjectType) {
                return "ObjectType";
            } else if (type instanceof ReferenceType reference) {
                AbstractClassDef target = reference.getReferred();
                if (target != null) {
                    return target.getName();
                }
            } else if (type instanceof ch.interlis.ili2c.metamodel.CompositionType composition) {
                AbstractClassDef target = composition.getComponentType();
                if (target != null) {
                    return target.getName();
                }
            } else if (type instanceof SurfaceType) {
                return "Surface";
            } else if (type instanceof MultiSurfaceType) {
                return "MultiSurface";
            } else if (type instanceof ch.interlis.ili2c.metamodel.AreaType) {
                return "Area";
            } else if (type instanceof MultiAreaType) {
                return "MultiArea";
            } else if (type instanceof PolylineType) {
                return "Polyline";
            } else if (type instanceof MultiPolylineType) {
                return "MultiPolyline";
            } else if (type instanceof ch.interlis.ili2c.metamodel.CoordType coord) {
                NumericalType[] dimensions = coord.getDimensions();
                return "Coord" + dimensions.length;
            } else if (type instanceof MultiCoordType multiCoord) {
                NumericalType[] dimensions = multiCoord.getDimensions();
                return "MultiCoord" + dimensions.length;
            } else if (type instanceof NumericalType) {
                return "Numeric";
            } else if (type instanceof TextType) {
                return "String";
            } else if (type instanceof EnumerationType enumerationType) {
                return attribute.isDomainBoolean() ? "Boolean" : attribute.getContainer().getName();
            } else if (type instanceof FormattedType formattedType) {
                if (isDateOrTime(formattedType)) {
                    return formattedType.getDefinedBaseDomain().getName();
                } else if (formattedType.getDefinedBaseDomain() != null
                    && formattedType.getDefinedBaseDomain().getName() != null) {
                    return formattedType.getDefinedBaseDomain().getName();
                }
                return "FormattedType";
            } else if (type instanceof TextOIDType textOidType) {
                Type oidType = textOidType.getOIDType();
                if (oidType instanceof TypeAlias alias) {
                    return alias.getAliasing().getName();
                }
                return "OID (Text)";
            } else if (type instanceof TypeAlias alias) {
                Domain aliased = alias.getAliasing();
                if (aliased != null && aliased.getName() != null) {
                    return aliased.getName();
                }
            }

            String name = type.getName();
            return (name == null || name.isEmpty()) ? type.getClass().getSimpleName() : name;
        }

        private static boolean isDateOrTime(final FormattedType type) {
            Domain baseDomain = type.getDefinedBaseDomain();
            return baseDomain == PredefinedModel.getInstance().XmlDate
                || baseDomain == PredefinedModel.getInstance().XmlDateTime
                || baseDomain == PredefinedModel.getInstance().XmlTime;
        }
    }
}
