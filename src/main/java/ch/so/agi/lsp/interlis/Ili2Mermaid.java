package ch.so.agi.lsp.interlis;

import java.util.Objects;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Assoc;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Diagram;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Inheritance;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Namespace;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Node;

public final class Ili2Mermaid {
    private Ili2Mermaid() {
    }

    /** Entry point. */
    public static String render(TransferDescription td) {
        Objects.requireNonNull(td, "TransferDescription is null");
        Diagram diagram = InterlisUmlDiagram.build(td);
        return new MermaidRenderer().render(diagram);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Mermaid renderer
    // ─────────────────────────────────────────────────────────────────────────────
    static final class MermaidRenderer {
        String render(Diagram d) {
            StringBuilder sb = new StringBuilder(4_096);
            sb.append("classDiagram\n");

            // 1) Print namespaces (topics/models). We print classes as we encounter them.
            d.namespaces.values().forEach(ns -> {
                if (ns.label.equals("<root>"))
                    return; // print root nodes later
                sb.append("  namespace ").append(nsId(ns.label)).append(" {\n");
                for (String fqn : ns.nodeOrder) {
                    Node n = d.nodes.get(fqn);
                    printClassBlock(sb, n, "    ");
                }
                sb.append("  }\n");
            });

            // 2) Root-level nodes (classes outside topics and externals)
            Namespace root = d.namespaces.get("<root>");
            if (root != null) {
                for (String fqn : root.nodeOrder) {
                    Node n = d.nodes.get(fqn);
                    printClassBlock(sb, n, "  ");
                }
            }

            // 3) Inheritance edges
            for (Inheritance i : d.inheritances) {
                sb.append("  ").append(id(i.subFqn)).append(" --|> ").append(id(i.supFqn)).append("\n");
            }

            // 4) Associations with cardinalities on both ends
            for (Assoc a : d.assocs) {
                sb.append("  ").append(id(a.leftFqn)).append(" \"").append(a.leftCard).append("\" -- \"")
                        .append(a.rightCard).append("\" ").append(id(a.rightFqn));
                if (a.label != null && !a.label.isEmpty())
                    sb.append(" : ").append(escape(a.label));
                sb.append("\n");
            }

            return sb.toString();
        }

        private static String id(String s) {
            return s;
        }

        private static String nsId(String s) {
            return s.replaceAll("[^A-Za-z0-9_]", "_");
        }

        private void printClassBlock(StringBuilder sb, Node n, String indent) {
            sb.append(indent).append("class ").append(id(n.fqn)).append("[\"").append(escape(n.displayName))
                    .append("\"] {\n");
            for (String stereo : n.stereotypes) {
                sb.append(indent).append("  ").append("<<").append(stereo).append(">>\n");
            }
            for (String attr : n.attributes) {
                sb.append(indent).append("  ").append(escape(attr)).append("\n");
            }
            for (String m : n.methods) {
                sb.append(indent).append("  ").append(escape(m)).append("\n");
            }
            sb.append(indent).append("}\n");
        }

        private static String escape(String s) {
            return s.replace("\"", "\\\"");
        }
    }
}
