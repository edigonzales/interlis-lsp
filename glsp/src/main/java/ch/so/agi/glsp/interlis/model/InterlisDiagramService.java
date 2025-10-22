package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.glsp.interlis.InterlisDiagramModule;
import com.google.inject.Singleton;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GModelRoot;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central service that turns a saved INTERLIS document into the GLSP graph
 * consumed by the client. It keeps track of the current source URI, invokes the
 * ili2c compiler and maps the resulting transfer description to simple class
 * nodes that can be rendered in the diagram.
 */
@Singleton
public final class InterlisDiagramService {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisDiagramService.class);

    private volatile Path currentSource;
    private final InterlisIli2cCompiler compiler = new InterlisIli2cCompiler();

    public void updateFromClientOptions(Map<String, String> options) {
        String uri = options != null ? options.getOrDefault("sourceUri", options.get("uri")) : null;
        if (uri == null || uri.isBlank()) {
            return;
        }
        try {
            currentSource = Path.of(URI.create(uri));
        } catch (IllegalArgumentException ex) {
            LOG.warn("Failed to parse INTERLIS source URI '{}': {}", uri, ex.getMessage());
        }
    }

    public GModelRoot createModel(Map<String, String> options) {
        updateFromClientOptions(options);
        Path source = currentSource;
        if (source == null) {
            return placeholder("No INTERLIS document selected.");
        }

        try {
            LOG.info("Building INTERLIS UML diagram for {}", source);
            TransferDescription td = compiler.compile(source);
            if (td == null) {
                return placeholder("Failed to compile INTERLIS model.");
            }
            return buildDiagram(td, source);
        } catch (Exception ex) {
            LOG.warn("GLSP diagram generation failed for {}: {}", source, ex.getMessage(), ex);
            return placeholder("GLSP server error: " + ex.getMessage());
        }
    }

    private GModelRoot buildDiagram(TransferDescription td, Path source) {
        GGraphBuilder graph = new GGraphBuilder("graph")
                .id("interlis-uml-root")
                .addCssClass("interlis-uml-graph")
                .layoutOptions(Map.ofEntries(
                        Map.entry("algorithm", "org.eclipse.elk.layered"),
                        Map.entry("paddingTop", "40"),
                        Map.entry("paddingLeft", "40"),
                        Map.entry("hGap", "32"),
                        Map.entry("vGap", "24")
                ));
        List<ClassEntry> classEntries = collectMainModelClasses(td, source);
        List<GModelElement> classes = new ArrayList<>();
        if (!classEntries.isEmpty()) {
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(classEntries.size())));
            double columnSpacing = 260;
            double rowSpacing = 170;

            for (int index = 0; index < classEntries.size(); index++) {
                ClassEntry entry = classEntries.get(index);
                int column = index % columns;
                int row = index / columns;

                double x = column * columnSpacing;
                double y = row * rowSpacing;

                classes.add(new GNodeBuilder("interlis-class")
                        .id(entry.identifier())
                        .size(210, 110)
                        .position(x, y)
                        .layout("vbox")
                        .layoutOptions(Map.ofEntries(
                                Map.entry("paddingTop", "12"),
                                Map.entry("paddingBottom", "12"),
                                Map.entry("paddingLeft", "12"),
                                Map.entry("paddingRight", "12"),
                                Map.entry("hAlign", "center")
                        ))
                        .addCssClass("interlis-class-node")
                        .add(new GLabelBuilder()
                                .id(entry.identifier() + "_label")
                                .text(entry.displayName())
                                .build())
                        .build());
            }
        }

        if (classes.isEmpty()) {
            graph.add(new GNodeBuilder("interlis-placeholder")
                    .id("interlis-placeholder")
                    .size(220, 80)
                    .layout("vbox")
                    .addCssClass("interlis-placeholder-node")
                    .add(new GLabelBuilder()
                            .text("No top-level classes found. Check the GLSP output channel for details.")
                            .build())
                    .build());
        } else {
            classes.forEach(graph::add);
        }

        graph.addArgument("source", source.toString());
        graph.addArgument("diagramType", InterlisDiagramModule.DIAGRAM_TYPE);
        return graph.build();
    }

    private List<ClassEntry> collectMainModelClasses(TransferDescription td, Path source) {
        List<Model> models = determineModels(td);
        List<ClassEntry> result = new ArrayList<>();
        for (Model model : models) {
            if (model == null || model.getName() == null) {
                continue;
            }
            collectTables(result, model, model);
            for (Topic topic : elementsOf(model, Topic.class)) {
                collectTables(result, model, topic);
            }
        }
        result.sort(Comparator.comparing(ClassEntry::displayName));
        LOG.info("INTERLIS UML diagram for {} contains {} identifiable classes across {} model(s)", source,
                result.size(), models.size());
        return result;
    }

    private List<Model> determineModels(TransferDescription td) {
        Model[] lastFileModels = Optional.ofNullable(td.getModelsFromLastFile()).orElse(null);
        List<Model> result = new ArrayList<>();
        if (lastFileModels != null) {
            for (Model model : lastFileModels) {
                if (model != null) {
                    result.add(model);
                }
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        LOG.debug("No models reported for the last file; falling back to all models in the transfer description");
        for (var it = td.iterator(); it.hasNext();) {
            Model model = it.next();
            if (model != null) {
                result.add(model);
            }
        }
        return result;
    }

    private void collectTables(List<ClassEntry> sink, Model model, Container container) {
        for (Table table : elementsOf(container, Table.class)) {
            if (!table.isIdentifiable()) {
                continue;
            }
            String identifier = sanitizeIdentifier(model.getName(), container, table.getName());
            String label = buildLabel(model.getName(), container, table.getName());
            sink.add(new ClassEntry(identifier, label));
        }
    }

    private <T> List<T> elementsOf(Container container, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (var it = container.iterator(); it.hasNext(); ) {
            Object next = it.next();
            if (type.isInstance(next)) {
                result.add(type.cast(next));
            }
        }
        result.sort(Comparator.comparing(
                element -> nameOf(element, type),
                Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    private <T> String nameOf(T element, Class<T> type) {
        if (element instanceof Model model) {
            return model.getName();
        }
        if (element instanceof Topic topic) {
            return topic.getName();
        }
        if (element instanceof Table table) {
            return table.getName();
        }
        return type.getSimpleName();
    }

    private String sanitizeIdentifier(String modelName, Container container, String elementName) {
        List<String> parts = new ArrayList<>();
        parts.add(modelName);
        if (container instanceof Topic topic) {
            parts.add(topic.getName());
        }
        parts.add(elementName);
        return parts.stream()
                .filter(Objects::nonNull)
                .map(part -> part.replaceAll("[^A-Za-z0-9_]+", "-").toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("_"));
    }

    private String buildLabel(String modelName, Container container, String elementName) {
        StringBuilder label = new StringBuilder(modelName);
        if (container instanceof Topic topic) {
            label.append('.').append(topic.getName());
        }
        label.append('.').append(elementName);
        return label.toString();
    }

    private GModelRoot placeholder(String message) {
        return new GGraphBuilder("graph")
                .id("interlis-uml-root")
                .add(new GNodeBuilder("interlis-placeholder")
                        .id("interlis-placeholder")
                        .add(new GLabelBuilder().text(message).build())
                        .build())
                .build();
    }

    private record ClassEntry(String identifier, String displayName) {
    }
}
