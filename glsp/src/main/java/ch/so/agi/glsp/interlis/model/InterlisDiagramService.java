package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Constraint;
import ch.interlis.ili2c.metamodel.CoordType;
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
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextOIDType;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.PolylineType;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.builder.impl.GCompartmentBuilder;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles INTERLIS source models and transforms the {@link TransferDescription}
 * into a GLSP graph that renders INTERLIS classifiers as styled nodes with
 * compartments.
 */
public class InterlisDiagramService {

    private static final Logger LOG = LoggerFactory.getLogger(InterlisDiagramService.class);

    private static final double NODE_WIDTH = 280;
    private static final double NODE_HEIGHT = 200;
    private static final double TOPIC_HEIGHT = 56;
    private static final double GRID_GAP_X = 96;
    private static final double GRID_GAP_Y = 48;
    private static final double GRID_START_X = 96;
    private static final double GRID_START_Y = 72;

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

            List<NamespaceInfo> namespaces = collectNamespaces(td);
            return Optional.of(buildGraph(namespaces));
        } catch (IOException ex) {
            LOG.warn("Failed to compile INTERLIS model '{}': {}", sourceFile, ex.getMessage());
            LOG.debug("Compilation failure", ex);
            return Optional.empty();
        }
    }

    private GGraph buildGraph(final List<NamespaceInfo> namespaces) {
        GGraphBuilder graphBuilder = new GGraphBuilder()
            .id(InterlisGlspTypes.GRAPH_ID)
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .layoutOptions(Map.of("padding", "24"));

        if (namespaces.isEmpty()) {
            return graphBuilder.build();
        }

        List<NamespaceInfo> orderedNamespaces = new ArrayList<>(namespaces);
        orderedNamespaces.sort(Comparator.comparing(NamespaceInfo::label, String.CASE_INSENSITIVE_ORDER));

        for (int column = 0; column < orderedNamespaces.size(); column++) {
            NamespaceInfo namespace = orderedNamespaces.get(column);
            double x = GRID_START_X + column * (NODE_WIDTH + GRID_GAP_X);
            double currentY = GRID_START_Y;

            graphBuilder.add(buildTopicNode(namespace, x, currentY));
            currentY += TOPIC_HEIGHT + GRID_GAP_Y;

            List<ClassifierInfo> classifiers = new ArrayList<>(namespace.classifiers());
            classifiers.sort(Comparator.comparing(ClassifierInfo::name, String.CASE_INSENSITIVE_ORDER));

            for (int row = 0; row < classifiers.size(); row++) {
                double y = currentY + row * (NODE_HEIGHT + GRID_GAP_Y);
                graphBuilder.add(buildClassifierNode(classifiers.get(row), x, y));
            }
        }

        return graphBuilder.build();
    }

    private List<NamespaceInfo> collectNamespaces(final TransferDescription td) {
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) {
            return Collections.emptyList();
        }

        List<Model> orderedModels = Arrays.stream(models)
            .sorted(Comparator.comparing(model -> caseInsensitive(model.getName())))
            .collect(Collectors.toList());

        List<NamespaceInfo> namespaces = new ArrayList<>();
        for (Model model : orderedModels) {
            namespaces.add(buildNamespace(model, model, null));
            for (Topic topic : getElements(model, Topic.class)) {
                namespaces.add(buildNamespace(model, topic, topic));
            }
        }

        return namespaces;
    }

    private NamespaceInfo buildNamespace(final Model model, final Container container, final Topic topic) {
        String label = namespaceLabel(model, topic);
        String namespaceId = namespaceId(label);
        List<ClassifierInfo> classifiers = collectClassifiers(model, container, topic);
        return new NamespaceInfo(namespaceId, namespaceLabelId(label), label, classifiers);
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

    private List<ClassifierInfo> collectClassifiers(final Model model, final Container container, final Topic topic) {
        List<ClassifierInfo> classifiers = new ArrayList<>();

        for (Table table : getElements(container, Table.class)) {
            ClassifierKind kind = determineKind(table);
            String fqn = buildFqn(model, topic, table);
            String nodeId = idFromFqn(fqn, kind);

            List<String> stereotypes = collectStereotypes(table, kind);
            List<AttributeInfo> attributes = collectAttributeInfos(table, nodeId);
            List<ConstraintInfo> constraints = collectConstraintInfos(table, nodeId);

            classifiers.add(new ClassifierInfo(nodeId, labelIdFromFqn(fqn), table.getName(), kind, stereotypes,
                attributes, constraints));
        }

        for (View view : getElements(container, View.class)) {
            String fqn = buildFqn(model, topic, view);
            String nodeId = idFromFqn(fqn, ClassifierKind.VIEW);

            List<String> stereotypes = new ArrayList<>();
            stereotypes.add("view");
            if (view.isAbstract()) {
                stereotypes.add("abstract");
            }
            List<AttributeInfo> attributes = collectAttributeInfos(view, nodeId);
            List<ConstraintInfo> constraints = collectConstraintInfos(view, nodeId);

            classifiers.add(new ClassifierInfo(nodeId, labelIdFromFqn(fqn), view.getName(), ClassifierKind.VIEW,
                stereotypes, attributes, constraints));
        }

        for (Domain domain : getElements(container, Domain.class)) {
            String fqn = buildFqn(model, topic, domain);
            String nodeId = idFromFqn(fqn, ClassifierKind.ENUMERATION);
            List<AttributeInfo> literals = collectEnumerationAttributeInfos(domain, nodeId);
            if (!literals.isEmpty()) {
                classifiers.add(new ClassifierInfo(nodeId, labelIdFromFqn(fqn), domain.getName(),
                    ClassifierKind.ENUMERATION, List.of("enumeration"), literals, List.of()));
            }
        }

        return classifiers;
    }

    private org.eclipse.glsp.graph.GNode buildTopicNode(final NamespaceInfo namespace, final double x, final double y) {
        return new GNodeBuilder()
            .id(namespace.id())
            .type(InterlisGlspTypes.TOPIC_NODE_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_TOPIC)
            .position(x, y)
            .size(NODE_WIDTH, TOPIC_HEIGHT)
            .add(new GLabelBuilder()
                .id(namespace.labelId())
                .type(InterlisGlspTypes.TOPIC_LABEL_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_TOPIC_LABEL)
                .text(namespace.label())
                .build())
            .build();
    }

    private org.eclipse.glsp.graph.GNode buildClassifierNode(final ClassifierInfo info, final double x, final double y) {
        GNodeBuilder builder = new GNodeBuilder()
            .id(info.id())
            .type(info.kind().nodeType())
            .addCssClass(info.kind().cssClass())
            .position(x, y)
            .size(NODE_WIDTH, NODE_HEIGHT);

        if (!info.stereotypes().isEmpty()) {
            builder.add(new GLabelBuilder()
                .id(info.id() + "-stereotype")
                .type(InterlisGlspTypes.STEREOTYPE_LABEL_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_STEREOTYPE)
                .text(formatStereotypes(info.stereotypes()))
                .build());
        }

        builder.add(new GLabelBuilder()
            .id(info.nameLabelId())
            .type(InterlisGlspTypes.NAME_LABEL_TYPE)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_NAME)
            .text(info.name())
            .build());

        if (!info.attributes().isEmpty()) {
            GCompartmentBuilder attrCompartment = new GCompartmentBuilder()
                .id(info.id() + "-attributes")
                .type(InterlisGlspTypes.ATTRIBUTE_COMPARTMENT_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_ATTRIBUTE_COMPARTMENT);
            for (AttributeInfo attribute : info.attributes()) {
                attrCompartment.add(new GLabelBuilder()
                    .id(attribute.id())
                    .type(InterlisGlspTypes.ATTRIBUTE_LABEL_TYPE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_ATTRIBUTE_LABEL)
                    .text(attribute.label())
                    .build());
            }
            builder.add(attrCompartment.build());
        }

        if (!info.constraints().isEmpty()) {
            GCompartmentBuilder constraintCompartment = new GCompartmentBuilder()
                .id(info.id() + "-constraints")
                .type(InterlisGlspTypes.CONSTRAINT_COMPARTMENT_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_CONSTRAINT_COMPARTMENT);
            for (ConstraintInfo constraint : info.constraints()) {
                constraintCompartment.add(new GLabelBuilder()
                    .id(constraint.id())
                    .type(InterlisGlspTypes.CONSTRAINT_LABEL_TYPE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_CONSTRAINT_LABEL)
                    .text(constraint.label())
                    .build());
            }
            builder.add(constraintCompartment.build());
        }

        return builder.build();
    }

    private static List<String> collectStereotypes(final Table table, final ClassifierKind kind) {
        List<String> stereotypes = new ArrayList<>();
        if (kind == ClassifierKind.STRUCTURE) {
            stereotypes.add("structure");
        }
        if (table.isAbstract()) {
            stereotypes.add("abstract");
        }
        return stereotypes;
    }

    private static List<AttributeInfo> collectAttributeInfos(final Viewable viewable, final String baseId) {
        List<AttributeInfo> attributes = new ArrayList<>();
        List<AttributeDef> attributeDefs = getElements(viewable, AttributeDef.class);
        for (int index = 0; index < attributeDefs.size(); index++) {
            AttributeDef attribute = attributeDefs.get(index);
            String cardinality = formatCardinality(attribute.getCardinality());
            String typeName = TypeNamer.nameOf(attribute);
            String label = attribute.getName() + " [" + cardinality + "] : " + typeName;
            attributes.add(new AttributeInfo(baseId + "-attribute-" + index, label));
        }
        return attributes;
    }

    private static List<ConstraintInfo> collectConstraintInfos(final Viewable viewable, final String baseId) {
        List<ConstraintInfo> constraints = new ArrayList<>();
        List<Constraint> constraintDefs = getElements(viewable, Constraint.class);
        for (int index = 0; index < constraintDefs.size(); index++) {
            Constraint constraint = constraintDefs.get(index);
            String name = constraint.getName();
            if (name == null || name.isBlank()) {
                name = "constraint" + (index + 1);
            }
            constraints.add(new ConstraintInfo(baseId + "-constraint-" + index, name + "()"));
        }
        return constraints;
    }

    private static List<AttributeInfo> collectEnumerationAttributeInfos(final Domain domain, final String baseId) {
        Type type = domain.getType();
        if (!(type instanceof AbstractEnumerationType enumType)) {
            return List.of();
        }
        Enumeration enumeration = enumType.getConsolidatedEnumeration();
        if (enumeration == null) {
            return List.of();
        }
        boolean includeIntermediateValues = enumType instanceof EnumTreeValueType;
        List<String> values = new ArrayList<>();
        appendEnumerationValues(values, "", enumeration, includeIntermediateValues);
        List<AttributeInfo> literals = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            literals.add(new AttributeInfo(baseId + "-literal-" + index, values.get(index)));
        }
        return literals;
    }

    private static void appendEnumerationValues(final List<String> target, final String prefix,
        final Enumeration enumeration, final boolean includeIntermediateValues) {
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
            Enumeration subEnumeration = element.getSubEnumeration();
            boolean hasSubElements = subEnumeration != null && subEnumeration.size() > 0;
            if (!hasSubElements || includeIntermediateValues) {
                target.add(value);
            }
            if (hasSubElements) {
                appendEnumerationValues(target, value, subEnumeration, includeIntermediateValues);
            }
        }
    }

    private static ClassifierKind determineKind(final Table table) {
        if (!table.isIdentifiable()) {
            return ClassifierKind.STRUCTURE;
        }
        return ClassifierKind.CLASS;
    }

    private static String buildFqn(final Model model, final Topic topic, final Element element) {
        if (element == null) {
            return "";
        }
        String name = element.getName();
        if (name == null || name.isEmpty()) {
            name = element.getClass().getSimpleName();
        }
        if (topic != null) {
            return model.getName() + "::" + topic.getName() + "." + name;
        }
        return model.getName() + "." + name;
    }

    private static String namespaceLabel(final Model model, final Topic topic) {
        if (topic == null) {
            return model.getName();
        }
        return model.getName() + "::" + topic.getName();
    }

    private static String namespaceId(final String namespaceLabel) {
        return "interlis-namespace-" + sanitize(namespaceLabel);
    }

    private static String namespaceLabelId(final String namespaceLabel) {
        return "interlis-namespace-label-" + sanitize(namespaceLabel);
    }

    private static String idFromFqn(final String fqn, final ClassifierKind kind) {
        return "interlis-" + kind.idSegment() + "-" + sanitize(fqn);
    }

    private static String labelIdFromFqn(final String fqn) {
        return "interlis-name-label-" + sanitize(fqn);
    }

    private static String sanitize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static String caseInsensitive(final String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    private static String formatStereotypes(final List<String> stereotypes) {
        return stereotypes.stream()
            .map(stereotype -> "\u00AB" + stereotype + "\u00BB")
            .collect(Collectors.joining(" "));
    }

    private static String formatCardinality(final Cardinality cardinality) {
        if (cardinality == null) {
            return "1";
        }
        long minimum = cardinality.getMinimum();
        long maximum = cardinality.getMaximum();
        String left = String.valueOf(minimum);
        String right = maximum < 0 || maximum == Long.MAX_VALUE ? "*" : String.valueOf(maximum);
        if (!"*".equals(right) && minimum == maximum) {
            return left;
        }
        return left + ".." + right;
    }

    private enum ClassifierKind {
        CLASS(InterlisGlspTypes.CLASS_NODE_TYPE, InterlisGlspTypes.CSS_CLASS_CLASS, "class"),
        STRUCTURE(InterlisGlspTypes.STRUCTURE_NODE_TYPE, InterlisGlspTypes.CSS_CLASS_STRUCTURE, "structure"),
        VIEW(InterlisGlspTypes.VIEW_NODE_TYPE, InterlisGlspTypes.CSS_CLASS_VIEW, "view"),
        ENUMERATION(InterlisGlspTypes.ENUMERATION_NODE_TYPE, InterlisGlspTypes.CSS_CLASS_ENUMERATION, "enumeration");

        private final String nodeType;
        private final String cssClass;
        private final String idSegment;

        ClassifierKind(final String nodeType, final String cssClass, final String idSegment) {
            this.nodeType = nodeType;
            this.cssClass = cssClass;
            this.idSegment = idSegment;
        }

        String nodeType() {
            return nodeType;
        }

        String cssClass() {
            return cssClass;
        }

        String idSegment() {
            return idSegment;
        }
    }

    private record NamespaceInfo(String id, String labelId, String label, List<ClassifierInfo> classifiers) {
    }

    private record ClassifierInfo(String id, String nameLabelId, String name, ClassifierKind kind,
            List<String> stereotypes, List<AttributeInfo> attributes, List<ConstraintInfo> constraints) {
    }

    private record AttributeInfo(String id, String label) {
    }

    private record ConstraintInfo(String id, String label) {
    }

    private static final class TypeNamer {
        private TypeNamer() {
        }

        static String nameOf(final AttributeDef attribute) {
            Type type = attribute.getDomain();
            if (type == null) {
                return "<unknown>";
            }
            if (type instanceof ObjectType) {
                return "Object";
            } else if (type instanceof ReferenceType reference) {
                Element referred = reference.getReferred();
                return referred != null ? referred.getName() : "Reference";
            } else if (type instanceof CompositionType composition) {
                Element target = composition.getComponentType();
                return target != null ? target.getName() : "Composition";
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
                return "Coord" + (dimensions == null ? "" : dimensions.length);
            } else if (type instanceof MultiCoordType multiCoordType) {
                NumericalType[] dimensions = multiCoordType.getDimensions();
                return "MultiCoord" + (dimensions == null ? "" : dimensions.length);
            } else if (type instanceof NumericalType) {
                return "Numeric";
            } else if (type instanceof TextType) {
                return "Text";
            } else if (type instanceof EnumerationType) {
                return attribute.isDomainBoolean() ? "Boolean" : "Enumeration";
            } else if (type instanceof FormattedType formattedType) {
                Domain baseDomain = formattedType.getDefinedBaseDomain();
                return baseDomain != null ? baseDomain.getName() : "Formatted";
            } else if (type instanceof TextOIDType textOidType) {
                Type oidType = textOidType.getOIDType();
                if (oidType instanceof TypeAlias alias) {
                    return alias.getAliasing().getName();
                }
                return "OID";
            } else if (type instanceof TypeAlias alias) {
                return alias.getAliasing().getName();
            }
            String typeName = type.getName();
            return typeName == null || typeName.isBlank() ? type.getClass().getSimpleName() : typeName;
        }
    }
}
