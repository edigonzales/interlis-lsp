package ch.so.agi.glsp.interlis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.glsp.graph.GGraph;
import org.eclipse.glsp.graph.builder.impl.GGraphBuilder;
import org.eclipse.glsp.graph.builder.impl.GLabelBuilder;
import org.eclipse.glsp.graph.builder.impl.GNodeBuilder;

import ch.so.agi.lsp.interlis.InterlisUmlDiagram;

final class InterlisGlspModelBuilder {
    private InterlisGlspModelBuilder() {
    }

    static GGraph buildClassDiagram(List<InterlisUmlDiagram.ClassEntry> classes) {
        Objects.requireNonNull(classes, "classes");

        GGraphBuilder graph = new GGraphBuilder(InterlisGlspConstants.GRAPH_TYPE)
                .id("interlis-class-diagram")
                .layoutOptions(Map.of("direction", "RIGHT"));

        for (InterlisUmlDiagram.ClassEntry entry : classes) {
            if (entry == null || entry.fqn() == null) {
                continue;
            }
            GNodeBuilder classNode = new GNodeBuilder(InterlisGlspConstants.CLASS_NODE_TYPE)
                    .id(entry.fqn())
                    .layout("vbox");

            classNode.add(new GLabelBuilder(InterlisGlspConstants.CLASS_LABEL_TYPE)
                    .id(entry.fqn() + "#name")
                    .text(entry.displayName() != null ? entry.displayName() : fallbackName(entry.fqn()))
                    .build());

            if (entry.namespace() != null && !entry.namespace().isBlank()) {
                classNode.add(new GLabelBuilder(InterlisGlspConstants.NAMESPACE_LABEL_TYPE)
                        .id(entry.fqn() + "#namespace")
                        .text(entry.namespace())
                        .build());
            }

            graph.add(classNode.build());
        }

        return graph.build();
    }

    static GGraph buildMessageGraph(String message) {
        String text = (message == null || message.isBlank()) ? "No INTERLIS model available" : message;
        return new GGraphBuilder(InterlisGlspConstants.GRAPH_TYPE)
                .id("interlis-class-diagram-empty")
                .add(new GLabelBuilder(InterlisGlspConstants.CLASS_LABEL_TYPE)
                        .id("message")
                        .text(text)
                        .build())
                .build();
    }

    private static String fallbackName(String fqn) {
        if (fqn == null || fqn.isBlank()) {
            return "Unnamed";
        }
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 && idx + 1 < fqn.length() ? fqn.substring(idx + 1) : fqn;
    }
}
