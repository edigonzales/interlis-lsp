package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
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
    private static final double NODE_HEIGHT = 140;
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

    private GGraph buildGraph(final List<ClassInfo> classes) {
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

            graphBuilder.add(new GNodeBuilder()
                .id(info.id())
                .type(InterlisGlspTypes.CLASS_NODE_TYPE)
                .addCssClass(InterlisGlspTypes.CSS_CLASS_NODE)
                .position(x, y)
                .size(NODE_WIDTH, NODE_HEIGHT)
                .add(new GLabelBuilder()
                    .id(info.labelId())
                    .type(InterlisGlspTypes.CLASS_LABEL_TYPE)
                    .addCssClass(InterlisGlspTypes.CSS_CLASS_LABEL)
                    .text(info.label())
                    .build())
                .build());
        }

        return graphBuilder.build();
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
            collectTables(classes, model, model, null);
            for (Topic topic : getElements(model, Topic.class)) {
                collectTables(classes, model, topic, topic);
            }
        }

        return classes;
    }

    private void collectTables(final List<ClassInfo> classes, final Model model, final Container container,
        final Topic topic) {
        for (Table table : getElements(container, Table.class)) {
            String fqn = buildFqn(model, topic, table);
            String label = buildLabel(model, topic, table);
            classes.add(new ClassInfo(idFromFqn(fqn), labelIdFromFqn(fqn), label));
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

    private static String buildFqn(final Model model, final Topic topic, final Table table) {
        if (topic != null) {
            return model.getName() + "::" + topic.getName() + "." + table.getName();
        }
        return model.getName() + "." + table.getName();
    }

    private static String buildLabel(final Model model, final Topic topic, final Table table) {
        if (topic != null) {
            return model.getName() + "::" + topic.getName() + "." + table.getName();
        }
        return model.getName() + "." + table.getName();
    }

    private static String idFromFqn(final String fqn) {
        String sanitized = fqn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "interlis-class-" + sanitized;
    }

    private static String labelIdFromFqn(final String fqn) {
        String sanitized = fqn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "interlis-class-label-" + sanitized;
    }

    private static String caseInsensitive(final String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    private record ClassInfo(String id, String labelId, String label) {
    }
}
