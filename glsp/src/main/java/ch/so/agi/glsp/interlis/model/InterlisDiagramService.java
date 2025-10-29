package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Constraint;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiAreaType;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.ObjectType;
import ch.interlis.ili2c.metamodel.PredefinedModel;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextOIDType;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.so.agi.glsp.interlis.InterlisGlspTypes;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.glsp.graph.GCompartment;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.graph.builder.impl.GCompartmentBuilder;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;
import org.eclipse.glsp.graph.util.GConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles INTERLIS source models and transforms the {@link TransferDescription}
 * into a GLSP graph that renders INTERLIS elements as styled nodes.
 */
public class InterlisDiagramService {

    private static final Logger LOG = LoggerFactory.getLogger(InterlisDiagramService.class);

    private static final double TOPIC_NODE_WIDTH = 360;
    private static final double TOPIC_NODE_HEIGHT = 240;
    private static final double CLASS_NODE_WIDTH = 260;
    private static final double CLASS_NODE_HEIGHT = 140;
    private static final double GRID_GAP_X = 120;
    private static final double GRID_GAP_Y = 72;
    private static final double GRID_START_X = 96;
    private static final double GRID_START_Y = 96;

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

            List<TopicInfo> topics = collectTopics(td);
            return Optional.of(buildGraph(topics));
        } catch (IOException ex) {
            LOG.warn("Failed to compile INTERLIS model '{}': {}", sourceFile, ex.getMessage());
            LOG.debug("Compilation failure", ex);
            return Optional.empty();
        }
    }

    private GGraph buildGraph(final List<TopicInfo> topics) {
        GGraphBuilder graphBuilder = new GGraphBuilder()
            .id(InterlisGlspTypes.GRAPH_ID)
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .layoutOptions(Map.of("padding", "24"));

        if (topics.isEmpty()) {
            return graphBuilder.build();
        }

        int nodesPerColumn = (int) Math.ceil(Math.sqrt(topics.size()));
        nodesPerColumn = Math.max(nodesPerColumn, 1);

        for (int index = 0; index < topics.size(); index++) {
            TopicInfo topic = topics.get(index);
            int column = index / nodesPerColumn;
            int row = index % nodesPerColumn;
            double x = GRID_START_X + column * (TOPIC_NODE_WIDTH + GRID_GAP_X);
            double y = GRID_START_Y + row * (TOPIC_NODE_HEIGHT + GRID_GAP_Y);

            graphBuilder.add(buildTopicNode(topic, x, y));
        }

        return graphBuilder.build();
    }

    private GNode buildTopicNode(final TopicInfo topic, final double x, final double y) {
        GNodeBuilder builder = new GNodeBuilder()
            .id(topic.id())
            .type(InterlisGlspTypes.TOPIC_NODE_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_NODE_BASE)
            .addCssClass(InterlisGlspTypes.CSS_TOPIC_NODE)
            .layout(GConstants.Layout.VBOX)
            .position(x, y)
            .size(TOPIC_NODE_WIDTH, TOPIC_NODE_HEIGHT);

        GCompartmentBuilder header = new GCompartmentBuilder()
            .id(topic.headerId())
            .type(InterlisGlspTypes.TOPIC_HEADER_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_TOPIC_HEADER)
            .layout(GConstants.Layout.VBOX)
            .layoutOptions(Map.of(
                "paddingTop", "12",
                "paddingBottom", "8",
                "paddingLeft", "16",
                "paddingRight", "16",
                "hAlign", "left"));

        header.add(new GLabelBuilder()
            .id(topic.labelId())
            .type(InterlisGlspTypes.TOPIC_LABEL_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_TOPIC_LABEL)
            .text(topic.label())
            .build());
        builder.add(header.build());

        GCompartmentBuilder content = new GCompartmentBuilder()
            .id(topic.contentId())
            .type(InterlisGlspTypes.TOPIC_CONTENT_COMPARTMENT_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_TOPIC_CONTENT)
            .layout(GConstants.Layout.VBOX)
            .layoutOptions(Map.of(
                "paddingTop", "12",
                "paddingBottom", "16",
                "paddingLeft", "16",
                "paddingRight", "16",
                "spacing", "16",
                "hAlign", "left",
                "vAlign", "top",
                "resizeContainer", "true"));

        for (NodeInfo node : topic.nodes()) {
            content.add(buildNode(node));
        }

        builder.add(content.build());
        return builder.build();
    }

    private GNode buildNode(final NodeInfo info) {
        GNodeBuilder builder = new GNodeBuilder()
            .id(info.id())
            .type(info.kind().nodeType())
            .addCssClass(InterlisGlspTypes.CSS_NODE_BASE)
            .addCssClass(info.kind().cssClass())
            .layout(GConstants.Layout.VBOX)
            .size(CLASS_NODE_WIDTH, CLASS_NODE_HEIGHT);

        GCompartmentBuilder header = new GCompartmentBuilder()
            .id(info.headerId())
            .type(InterlisGlspTypes.HEADER_COMPARTMENT_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_HEADER_COMPARTMENT)
            .layout(GConstants.Layout.VBOX)
            .layoutOptions(Map.of(
                "paddingTop", "12",
                "paddingBottom", "8",
                "paddingLeft", "16",
                "paddingRight", "16",
                "hAlign", "left"));

        if (!info.stereotypes().isEmpty()) {
            header.add(new GLabelBuilder()
                .id(info.stereotypeLabelId())
                .type(InterlisGlspTypes.STEREOTYPE_LABEL_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_STEREOTYPE_LABEL)
                .text(formatStereotypes(info.stereotypes()))
                .build());
        }

        header.add(new GLabelBuilder()
            .id(info.nameLabelId())
            .type(InterlisGlspTypes.NAME_LABEL_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_NAME_LABEL)
            .text(info.name())
            .build());
        builder.add(header.build());

        if (!info.attributes().isEmpty()) {
            builder.add(buildCompartment(info.attributes(), info::attributeLabelId,
                info.attributesCompartmentId(), InterlisGlspTypes.ATTRIBUTE_COMPARTMENT_TYPE,
                InterlisGlspTypes.CSS_ATTRIBUTES_COMPARTMENT, InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE,
                InterlisGlspTypes.CSS_ATTRIBUTE_LABEL));
        }

        if (!info.enumerationLiterals().isEmpty()) {
            builder.add(buildCompartment(info.enumerationLiterals(), info::enumerationLiteralLabelId,
                info.enumerationCompartmentId(), InterlisGlspTypes.ENUMERATION_LITERAL_COMPARTMENT_TYPE,
                InterlisGlspTypes.CSS_ENUMERATION_LITERALS_COMPARTMENT,
                InterlisGlspTypes.ENUMERATION_LITERAL_LABEL_TYPE,
                InterlisGlspTypes.CSS_ENUMERATION_LITERAL_LABEL));
        }

        if (!info.constraints().isEmpty()) {
            builder.add(buildCompartment(info.constraints(), info::constraintLabelId,
                info.constraintsCompartmentId(), InterlisGlspTypes.CONSTRAINT_COMPARTMENT_TYPE,
                InterlisGlspTypes.CSS_CONSTRAINTS_COMPARTMENT, InterlisGlspTypes.CONSTRAINT_LABEL_TYPE,
                InterlisGlspTypes.CSS_CONSTRAINT_LABEL));
        }

        return builder.build();
    }

    private GCompartment buildCompartment(final List<String> entries,
        final Function<Integer, String> idProvider, final String compartmentId, final String compartmentType,
        final String compartmentCss, final String labelType, final String labelCss) {
        GCompartmentBuilder compartment = new GCompartmentBuilder()
            .id(compartmentId)
            .type(compartmentType)
            .addCssClass(InterlisGlspTypes.CSS_BODY_COMPARTMENT)
            .addCssClass(compartmentCss)
            .layout(GConstants.Layout.VBOX)
            .layoutOptions(Map.of(
                "paddingTop", "8",
                "paddingBottom", "12",
                "paddingLeft", "16",
                "paddingRight", "16",
                "spacing", "6",
                "hAlign", "left"));

        for (int index = 0; index < entries.size(); index++) {
            compartment.add(new GLabelBuilder()
                .id(idProvider.apply(index))
                .type(labelType)
                .addCssClass(labelCss)
                .text(entries.get(index))
                .build());
        }
        return compartment.build();
    }

    private List<TopicInfo> collectTopics(final TransferDescription td) {
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) {
            return Collections.emptyList();
        }

        Map<String, TopicAccumulator> accumulators = new LinkedHashMap<>();
        List<Model> orderedModels = Arrays.stream(models)
            .sorted(Comparator.comparing(model -> caseInsensitive(model.getName())))
            .collect(Collectors.toList());

        for (Model model : orderedModels) {
            TopicAccumulator modelAccumulator = accumulators.computeIfAbsent(model.getName(), label ->
                new TopicAccumulator(topicIdFromLabel(label), label));
            collectViewables(modelAccumulator, model, model);
            collectDomains(modelAccumulator, model, model);

            for (Topic topic : getElements(model, Topic.class)) {
                String label = model.getName() + "::" + topic.getName();
                TopicAccumulator topicAccumulator = accumulators.computeIfAbsent(label, value ->
                    new TopicAccumulator(topicIdFromLabel(value), value));
                collectViewables(topicAccumulator, model, topic);
                collectDomains(topicAccumulator, model, topic);
            }
        }

        return accumulators.values().stream()
            .map(TopicAccumulator::toTopicInfo)
            .filter(topic -> !topic.nodes().isEmpty())
            .collect(Collectors.toList());
    }

    private void collectViewables(final TopicAccumulator accumulator, final Model model, final Container container) {
        Topic topic = container instanceof Topic ? (Topic) container : null;
        for (Viewable viewable : getElements(container, Viewable.class)) {
            if (viewable instanceof AssociationDef) {
                continue;
            }

            InterlisNodeKind kind = determineKind(viewable);
            List<String> stereotypes = new ArrayList<>();
            if (viewable.isAbstract()) {
                stereotypes.add("Abstract");
            }
            if (kind == InterlisNodeKind.STRUCTURE) {
                stereotypes.add("Structure");
            } else if (kind == InterlisNodeKind.VIEW) {
                stereotypes.add("View");
            }

            List<String> attributes = new ArrayList<>();
            for (AttributeDef attribute : getElements(viewable, AttributeDef.class)) {
                String typeName = TypeNamer.nameOf(attribute);
                if ("ObjectType".equalsIgnoreCase(typeName)) {
                    continue;
                }
                String card = formatCardinality(attribute.getCardinality());
                attributes.add(attribute.getName() + "[" + card + "] : " + typeName);
            }

            List<String> constraints = new ArrayList<>();
            int counter = 1;
            for (Constraint constraint : getElements(viewable, Constraint.class)) {
                String name = constraint.getName();
                if (name == null || name.isBlank()) {
                    name = "constraint" + counter++;
                }
                constraints.add(name + "()");
            }

            String fqn = buildFqn(model, topic, viewable);
            accumulator.addNode(new NodeInfo(
                idFromFqn(fqn),
                viewable.getName(),
                kind,
                List.copyOf(stereotypes),
                List.copyOf(attributes),
                List.copyOf(constraints),
                List.of()));
        }
    }

    private void collectDomains(final TopicAccumulator accumulator, final Model model, final Container container) {
        Topic topic = container instanceof Topic ? (Topic) container : null;
        for (Domain domain : getElements(container, Domain.class)) {
            List<String> literals = extractEnumerationLiterals(domain.getType());
            if (literals.isEmpty()) {
                continue;
            }

            String fqn = buildFqn(model, topic, domain);
            accumulator.addNode(new NodeInfo(
                idFromFqn(fqn),
                domain.getName(),
                InterlisNodeKind.ENUMERATION,
                List.of("Enumeration"),
                List.of(),
                List.of(),
                List.copyOf(literals)));
        }
    }

    private static InterlisNodeKind determineKind(final Viewable viewable) {
        if (viewable instanceof Table table) {
            return table.isIdentifiable() ? InterlisNodeKind.CLASS : InterlisNodeKind.STRUCTURE;
        }
        return InterlisNodeKind.VIEW;
    }

    private static List<String> extractEnumerationLiterals(final Type type) {
        if (type instanceof AbstractEnumerationType enumerationType) {
            return flattenEnumeration(enumerationType.getConsolidatedEnumeration());
        }
        return List.of();
    }

    private static List<String> flattenEnumeration(final Enumeration enumeration) {
        if (enumeration == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Iterator<Enumeration.Element> elements = enumeration.getElements();
        while (elements.hasNext()) {
            Enumeration.Element element = elements.next();
            values.add(element.getName());
            values.addAll(flattenEnumeration(element.getSubEnumeration()));
        }
        return values;
    }

    private static String buildFqn(final Model model, final Topic topic, final Element element) {
        String elementName = element != null ? element.getName() : "";
        if (topic != null) {
            return model.getName() + "::" + topic.getName() + "." + elementName;
        }
        return model.getName() + "." + elementName;
    }

    private static <T extends Element> List<T> getElements(final Container container, final Class<T> type) {
        if (container == null) {
            return Collections.emptyList();
        }
        List<T> elements = new ArrayList<>();
        Iterator<?> iterator = container.iterator();
        while (iterator.hasNext()) {
            Object child = iterator.next();
            if (type.isInstance(child)) {
                elements.add(type.cast(child));
            }
        }
        elements.sort(Comparator.comparing(element -> caseInsensitive(element.getName())));
        return elements;
    }

    private static String idFromFqn(final String fqn) {
        String sanitized = fqn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "interlis-node-" + sanitized;
    }

    private static String topicIdFromLabel(final String label) {
        String sanitized = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "interlis-topic-" + sanitized;
    }

    private static String caseInsensitive(final String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    private static String formatStereotypes(final List<String> stereotypes) {
        if (stereotypes.isEmpty()) {
            return "";
        }
        return "«" + String.join(", ", stereotypes) + "»";
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
            return left;
        }
        return left + ".." + right;
    }

    private static boolean isDateOrTime(final FormattedType formattedType) {
        Domain baseDomain = formattedType.getDefinedBaseDomain();
        return baseDomain == PredefinedModel.getInstance().XmlDate
            || baseDomain == PredefinedModel.getInstance().XmlDateTime
            || baseDomain == PredefinedModel.getInstance().XmlTime;
    }

    private static final class TopicAccumulator {
        private final String id;
        private final String label;
        private final List<NodeInfo> nodes = new ArrayList<>();

        TopicAccumulator(final String id, final String label) {
            this.id = id;
            this.label = label;
        }

        void addNode(final NodeInfo node) {
            nodes.add(node);
        }

        TopicInfo toTopicInfo() {
            List<NodeInfo> ordered = new ArrayList<>(nodes);
            ordered.sort(Comparator.comparing(NodeInfo::name, String.CASE_INSENSITIVE_ORDER));
            return new TopicInfo(id, label, List.copyOf(ordered));
        }
    }

    private record TopicInfo(String id, String label, List<NodeInfo> nodes) {
        String headerId() {
            return id + "-header";
        }

        String labelId() {
            return id + "-label";
        }

        String contentId() {
            return id + "-content";
        }
    }

    private record NodeInfo(String id, String name, InterlisNodeKind kind, List<String> stereotypes,
        List<String> attributes, List<String> constraints, List<String> enumerationLiterals) {
        String headerId() {
            return id + "-header";
        }

        String nameLabelId() {
            return id + "-name";
        }

        String stereotypeLabelId() {
            return id + "-stereotype";
        }

        String attributesCompartmentId() {
            return id + "-attributes";
        }

        String attributeLabelId(final int index) {
            return id + "-attribute-" + index;
        }

        String constraintsCompartmentId() {
            return id + "-constraints";
        }

        String constraintLabelId(final int index) {
            return id + "-constraint-" + index;
        }

        String enumerationCompartmentId() {
            return id + "-enumeration";
        }

        String enumerationLiteralLabelId(final int index) {
            return id + "-enumeration-" + index;
        }
    }

    private enum InterlisNodeKind {
        CLASS(InterlisGlspTypes.CLASS_NODE_TYPE, InterlisGlspTypes.CSS_CLASS_NODE),
        STRUCTURE(InterlisGlspTypes.STRUCTURE_NODE_TYPE, InterlisGlspTypes.CSS_STRUCTURE_NODE),
        VIEW(InterlisGlspTypes.VIEW_NODE_TYPE, InterlisGlspTypes.CSS_VIEW_NODE),
        ENUMERATION(InterlisGlspTypes.ENUMERATION_NODE_TYPE, InterlisGlspTypes.CSS_ENUMERATION_NODE);

        private final String nodeType;
        private final String cssClass;

        InterlisNodeKind(final String nodeType, final String cssClass) {
            this.nodeType = nodeType;
            this.cssClass = cssClass;
        }

        String nodeType() {
            return nodeType;
        }

        String cssClass() {
            return cssClass;
        }
    }

    private static final class TypeNamer {
        private TypeNamer() {
        }

        static String nameOf(final AttributeDef attribute) {
            Type type = attribute.getDomain();
            if (type == null) {
                return "<Unknown>";
            }
            if (type instanceof ObjectType) {
                return "ObjectType";
            } else if (type instanceof ReferenceType referenceType) {
                Element destination = referenceType.getReferred();
                if (destination != null) {
                    return destination.getName();
                }
            } else if (type instanceof CompositionType compositionType) {
                Element component = compositionType.getComponentType();
                if (component != null) {
                    return component.getName();
                }
            } else if (type instanceof SurfaceType) {
                return "Surface";
            } else if (type instanceof MultiSurfaceType) {
                return "MultiSurface";
            } else if (type instanceof AreaType) {
                return "Area";
            } else if (type instanceof MultiAreaType) {
                return "MultiArea";
            } else if (type instanceof PolylineType) {
                return "Polyline";
            } else if (type instanceof MultiPolylineType) {
                return "MultiPolyline";
            } else if (type instanceof CoordType coordType) {
                NumericalType[] dimensions = coordType.getDimensions();
                return "Coord" + dimensions.length;
            } else if (type instanceof MultiCoordType multiCoordType) {
                NumericalType[] dimensions = multiCoordType.getDimensions();
                return "MultiCoord" + dimensions.length;
            } else if (type instanceof NumericType) {
                return "Numeric";
            } else if (type instanceof TextType) {
                return "String";
            } else if (type instanceof EnumerationType) {
                return attribute.isDomainBoolean() ? "Boolean" : attribute.getContainer().getName();
            } else if (type instanceof FormattedType formattedType) {
                if (isDateOrTime(formattedType)) {
                    return formattedType.getDefinedBaseDomain().getName();
                }
                if (formattedType.getDefinedBaseDomain().getName() != null) {
                    return formattedType.getDefinedBaseDomain().getName();
                }
                return "FormattedType";
            } else if (type instanceof TextOIDType textOidType) {
                Type oidType = textOidType.getOIDType();
                if (oidType instanceof TypeAlias alias) {
                    return alias.getAliasing().getName();
                }
                return "OID (Text)";
            } else if (type instanceof TypeAlias typeAlias) {
                return typeAlias.getAliasing().getName();
            }
            String name = type.getName();
            return (name != null && !name.isEmpty()) ? name : type.getClass().getSimpleName();
        }
    }
}
