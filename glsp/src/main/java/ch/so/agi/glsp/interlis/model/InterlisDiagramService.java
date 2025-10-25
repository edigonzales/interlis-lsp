package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.Constraint;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumTreeValueType;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
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
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.graph.builder.impl.GCompartmentBuilder;
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

    private static final double NODE_WIDTH = 240;
    private static final double NODE_HEIGHT = 160;
    private static final double GRID_GAP_X = 80;
    private static final double GRID_GAP_Y = 56;
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

            List<ClassInfo> classes = collectClasses(td);
            return Optional.of(buildGraph(classes));
        } catch (IOException ex) {
            LOG.warn("Failed to compile INTERLIS model '{}': {}", sourceFile, ex.getMessage());
            LOG.debug("Compilation failure", ex);
            return Optional.empty();
        }
    }

    GGraph buildGraph(final List<ClassInfo> classes) {
        GGraphBuilder graphBuilder = new GGraphBuilder()
            .id(InterlisGlspTypes.GRAPH_ID)
            .type(InterlisGlspTypes.DIAGRAM_TYPE)
            .layoutOptions(Map.of("padding", "24"));

        if (classes.isEmpty()) {
            return graphBuilder.build();
        }

        int nodesPerColumn = (int) Math.ceil(Math.sqrt(classes.size()));
        nodesPerColumn = Math.max(nodesPerColumn, 1);

        List<ClassInfo> ordered = new ArrayList<>(classes);
        ordered.sort(Comparator.comparing(ClassInfo::label, String.CASE_INSENSITIVE_ORDER));

        for (int index = 0; index < ordered.size(); index++) {
            ClassInfo info = ordered.get(index);
            int column = index / nodesPerColumn;
            int row = index % nodesPerColumn;
            double x = GRID_START_X + column * (NODE_WIDTH + GRID_GAP_X);
            double y = GRID_START_Y + row * (NODE_HEIGHT + GRID_GAP_Y);

            graphBuilder.add(createNode(info, x, y));
        }

        return graphBuilder.build();
    }

    private GNode createNode(final ClassInfo info, final double x, final double y) {
        GNodeBuilder nodeBuilder = new GNodeBuilder()
            .id(info.id())
            .type(InterlisGlspTypes.NODE_TYPE_VIEWABLE)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_NODE)
            .position(x, y)
            .size(NODE_WIDTH, NODE_HEIGHT);

        cssClassForKind(info.kind()).ifPresent(nodeBuilder::addCssClass);
        if (info.stereotypes().stream().anyMatch(stereo -> "Abstract".equalsIgnoreCase(stereo))) {
            nodeBuilder.addCssClass(InterlisGlspTypes.CSS_CLASS_ABSTRACT);
        }

        GCompartmentBuilder header = new GCompartmentBuilder()
            .id(info.headerId())
            .type(InterlisGlspTypes.COMPARTMENT_TYPE_HEADER)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_HEADER);

        if (!info.stereotypes().isEmpty()) {
            header.add(new GLabelBuilder()
                .id(info.stereotypesLabelId())
                .type(InterlisGlspTypes.LABEL_TYPE_STEREOTYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_STEREOTYPE_LABEL)
                .text(formatStereotypes(info.stereotypes()))
                .build());
        }

        header.add(new GLabelBuilder()
            .id(info.nameLabelId())
            .type(InterlisGlspTypes.LABEL_TYPE_NAME)
            .addCssClass(InterlisGlspTypes.CSS_CLASS_NAME_LABEL)
            .text(info.label())
            .build());

        nodeBuilder.add(header.build());

        if (!info.attributes().isEmpty()) {
            GCompartmentBuilder attributes = new GCompartmentBuilder()
                .id(info.attributesCompartmentId())
                .type(InterlisGlspTypes.COMPARTMENT_TYPE_ATTRIBUTES)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_ATTRIBUTES);

            for (MemberInfo attribute : info.attributes()) {
                attributes.add(new GLabelBuilder()
                    .id(attribute.id())
                    .type(InterlisGlspTypes.LABEL_TYPE_ATTRIBUTE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_ATTRIBUTE_LABEL)
                    .text(attribute.text())
                    .build());
            }

            nodeBuilder.add(attributes.build());
        }

        if (!info.constraints().isEmpty()) {
            GCompartmentBuilder constraints = new GCompartmentBuilder()
                .id(info.constraintsCompartmentId())
                .type(InterlisGlspTypes.COMPARTMENT_TYPE_CONSTRAINTS)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_CONSTRAINTS);

            for (MemberInfo constraint : info.constraints()) {
                constraints.add(new GLabelBuilder()
                    .id(constraint.id())
                    .type(InterlisGlspTypes.LABEL_TYPE_CONSTRAINT)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_CONSTRAINT_LABEL)
                    .text(constraint.text())
                    .build());
            }

            nodeBuilder.add(constraints.build());
        }

        return nodeBuilder.build();
    }

    private Optional<String> cssClassForKind(final NodeKind kind) {
        return switch (kind) {
            case CLASS -> Optional.of(InterlisGlspTypes.CSS_CLASS_CLASS);
            case STRUCTURE -> Optional.of(InterlisGlspTypes.CSS_CLASS_STRUCTURE);
            case VIEW -> Optional.of(InterlisGlspTypes.CSS_CLASS_VIEW);
            case ENUMERATION -> Optional.of(InterlisGlspTypes.CSS_CLASS_ENUMERATION);
            case TOPIC -> Optional.of(InterlisGlspTypes.CSS_CLASS_TOPIC);
        };
    }

    private List<ClassInfo> collectClasses(final TransferDescription td) {
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) {
            return Collections.emptyList();
        }

        List<ClassInfo> classes = new ArrayList<>();
        List<Model> orderedModels = Arrays.stream(models)
            .sorted(Comparator.comparing(model -> caseInsensitive(model.getName())))
            .collect(Collectors.toList());

        for (Model model : orderedModels) {
            collectViewables(classes, model, model, null);
            collectDomains(classes, model, model, null);
            for (Topic topic : getElements(model, Topic.class)) {
                collectViewables(classes, model, topic, topic);
                collectDomains(classes, model, topic, topic);
            }
        }

        return classes;
    }

    private void collectViewables(final List<ClassInfo> classes, final Model model, final Container container,
        final Topic topic) {
        for (Viewable viewable : getElements(container, Viewable.class)) {
            if (viewable == null || viewable instanceof ch.interlis.ili2c.metamodel.AssociationDef) {
                continue;
            }

            String fqn = buildQualifiedName(model, topic, viewable);
            String label = buildQualifiedLabel(model, topic, viewable);
            String sanitized = sanitizeFqn(fqn);

            NodeKind kind;
            List<String> stereotypes = new ArrayList<>();
            if (viewable instanceof Table table) {
                kind = determineKind(table);
                stereotypes.addAll(collectStereotypes(table, kind));
            } else {
                kind = NodeKind.VIEW;
                stereotypes.add("View");
            }

            List<MemberInfo> attributes = collectAttributes(viewable, sanitized);
            List<MemberInfo> constraints = collectConstraints(viewable, sanitized);

            classes.add(new ClassInfo(fqn, sanitized, label, kind, stereotypes, attributes, constraints));
        }
    }

    private void collectDomains(final List<ClassInfo> classes, final Model model, final Container container,
        final Topic topic) {
        for (Domain domain : getElements(container, Domain.class)) {
            Type type = domain.getType();
            if (!(type instanceof EnumerationType) && !(type instanceof EnumTreeValueType)) {
                continue;
            }
            AbstractEnumerationType enumerationType = (AbstractEnumerationType) type;
            String fqn = buildQualifiedName(model, topic, domain);
            String label = buildQualifiedLabel(model, topic, domain);
            String sanitized = sanitizeFqn(fqn);

            List<String> stereotypes = List.of("Enumeration");
            List<MemberInfo> literals = collectEnumerationLiterals(enumerationType, sanitized);

            classes.add(new ClassInfo(fqn, sanitized, label, NodeKind.ENUMERATION, stereotypes, literals,
                Collections.emptyList()));
        }
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

    private static String buildQualifiedName(final Model model, final Topic topic, final Element element) {
        if (element == null) {
            return "";
        }
        if (topic != null) {
            return model.getName() + "::" + topic.getName() + "." + element.getName();
        }
        return model.getName() + "." + element.getName();
    }

    private static String buildQualifiedLabel(final Model model, final Topic topic, final Element element) {
        return buildQualifiedName(model, topic, element);
    }

    private static String caseInsensitive(final String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    private static String sanitizeFqn(final String fqn) {
        return fqn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static String formatStereotypes(final List<String> stereotypes) {
        return stereotypes.stream()
            .map(stereo -> "«" + stereo + "»")
            .collect(Collectors.joining("\n"));
    }

    private static NodeKind determineKind(final Table table) {
        if (table == null) {
            return NodeKind.CLASS;
        }
        if (!table.isIdentifiable()) {
            return NodeKind.STRUCTURE;
        }
        return NodeKind.CLASS;
    }

    private static List<String> collectStereotypes(final Table table, final NodeKind kind) {
        List<String> stereotypes = new ArrayList<>();
        if (table != null && table.isAbstract()) {
            stereotypes.add("Abstract");
        }
        if (kind == NodeKind.STRUCTURE) {
            stereotypes.add("Structure");
        }
        return stereotypes;
    }

    private List<MemberInfo> collectAttributes(final Viewable viewable, final String sanitizedFqn) {
        List<MemberInfo> attributes = new ArrayList<>();
        int index = 0;
        for (AttributeDef attribute : getElements(viewable, AttributeDef.class)) {
            String card = formatCardinality(attribute.getCardinality());
            String typeName = InterlisTypeNamer.nameOf(attribute);
            if (!typeName.equalsIgnoreCase("ObjectType")) {
                String text = attribute.getName() + "[" + card + "] : " + typeName;
                attributes.add(new MemberInfo(memberId(sanitizedFqn, "attr", index++), text));
            }
        }
        return attributes;
    }

    private List<MemberInfo> collectConstraints(final Viewable viewable, final String sanitizedFqn) {
        List<MemberInfo> constraints = new ArrayList<>();
        int index = 0;
        int ci = 1;
        for (Constraint constraint : getElements(viewable, Constraint.class)) {
            String cname = (constraint.getName() != null && !constraint.getName().isEmpty()) ? constraint.getName()
                : ("constraint" + ci++);
            constraints.add(new MemberInfo(memberId(sanitizedFqn, "constraint", index++), cname + "()"));
        }
        return constraints;
    }

    private List<MemberInfo> collectEnumerationLiterals(final AbstractEnumerationType enumerationType,
        final String sanitizedFqn) {
        List<MemberInfo> literals = new ArrayList<>();
        Enumeration enumeration = enumerationType.getConsolidatedEnumeration();
        boolean includeIntermediate = enumerationType instanceof EnumTreeValueType;
        List<String> values = new ArrayList<>();
        appendEnumerationValues(values, "", enumeration, includeIntermediate);
        for (int index = 0; index < values.size(); index++) {
            literals.add(new MemberInfo(memberId(sanitizedFqn, "literal", index), values.get(index)));
        }
        return literals;
    }

    private void appendEnumerationValues(final List<String> target, final String prefix, final Enumeration enumeration,
        final boolean includeIntermediate) {
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
            boolean hasSub = sub != null && sub.size() > 0;
            if (!hasSub || includeIntermediate) {
                target.add(value);
            }
            if (hasSub) {
                appendEnumerationValues(target, value, sub, includeIntermediate);
            }
        }
    }

    private static String memberId(final String sanitizedFqn, final String prefix, final int index) {
        return "interlis-" + prefix + "-" + sanitizedFqn + "-" + index;
    }

    private static String formatCardinality(final Cardinality cardinality) {
        if (cardinality == null) {
            return "1";
        }
        long min = cardinality.getMinimum();
        long max = cardinality.getMaximum();
        if (min == max) {
            return Long.toString(min);
        }
        if (max == Cardinality.UNBOUND) {
            return min + "..*";
        }
        return min + ".." + max;
    }

    static final class ClassInfo {
        private final String fqn;
        private final String sanitizedFqn;
        private final String label;
        private final NodeKind kind;
        private final List<String> stereotypes;
        private final List<MemberInfo> attributes;
        private final List<MemberInfo> constraints;

        ClassInfo(final String fqn, final String sanitizedFqn, final String label, final NodeKind kind,
            final List<String> stereotypes, final List<MemberInfo> attributes, final List<MemberInfo> constraints) {
            this.fqn = fqn;
            this.sanitizedFqn = sanitizedFqn;
            this.label = label;
            this.kind = kind;
            this.stereotypes = List.copyOf(stereotypes);
            this.attributes = List.copyOf(attributes);
            this.constraints = List.copyOf(constraints);
        }

        String id() {
            return "interlis-node-" + sanitizedFqn;
        }

        String headerId() {
            return "interlis-compartment-header-" + sanitizedFqn;
        }

        String attributesCompartmentId() {
            return "interlis-compartment-attributes-" + sanitizedFqn;
        }

        String constraintsCompartmentId() {
            return "interlis-compartment-constraints-" + sanitizedFqn;
        }

        String stereotypesLabelId() {
            return "interlis-label-stereotype-" + sanitizedFqn;
        }

        String nameLabelId() {
            return "interlis-label-name-" + sanitizedFqn;
        }

        String label() {
            return label;
        }

        NodeKind kind() {
            return kind;
        }

        List<String> stereotypes() {
            return stereotypes;
        }

        List<MemberInfo> attributes() {
            return attributes;
        }

        List<MemberInfo> constraints() {
            return constraints;
        }
    }

    static final class MemberInfo {
        private final String id;
        private final String text;

        MemberInfo(final String id, final String text) {
            this.id = id;
            this.text = text;
        }

        String id() {
            return id;
        }

        String text() {
            return text;
        }
    }

    enum NodeKind {
        TOPIC,
        CLASS,
        STRUCTURE,
        VIEW,
        ENUMERATION
    }
}
