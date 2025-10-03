package ch.so.agi.lsp.interlis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Assoc;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Diagram;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Inheritance;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Namespace;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Node;

public final class Ili2PlantUml {
    private Ili2PlantUml() {
    }

    /** Returns the PlantUML source for the given TransferDescription. */
    public static String renderSource(TransferDescription td) {
        Objects.requireNonNull(td, "TransferDescription is null");
        Diagram diagram = InterlisUmlDiagram.build(td);
        return new PlantRenderer().render(diagram);
    }

    static final class PlantRenderer {
        String render(Diagram d) {
            StringBuilder sb = new StringBuilder(4_096);
            sb.append("@startuml\n");
            sb.append("!pragma layout smetana\n");
            sb.append("skinparam packageStyle rectangle\n");
            sb.append("skinparam classAttributeIconSize 0\n");
            sb.append("skinparam monochrome false\n");
            sb.append("skinparam shadowing false\n\n");

            d.namespaces.values().forEach(ns -> {
                if (ns.label.equals("<root>")) {
                    return;
                }
                sb.append("package \"").append(escape(ns.label)).append("\" as ").append(nsId(ns.label)).append(" {\n");
                for (String fqn : ns.nodeOrder) {
                    Node n = d.nodes.get(fqn);
                    printNode(sb, n, "  ");
                }
                sb.append("}\n\n");
            });

            Namespace root = d.namespaces.get("<root>");
            if (root != null) {
                for (String fqn : root.nodeOrder) {
                    Node n = d.nodes.get(fqn);
                    printNode(sb, n, "");
                    sb.append("\n");
                }
            }

            for (Inheritance i : d.inheritances) {
                sb.append(id(i.subFqn)).append(" --|> ").append(id(i.supFqn)).append("\n");
            }
            if (!d.inheritances.isEmpty()) {
                sb.append("\n");
            }

            for (Assoc a : d.assocs) {
                sb.append(id(a.leftFqn)).append(" \"").append(escape(a.leftCard)).append("\" -- ")
                        .append("\"").append(escape(a.rightCard)).append("\" ").append(id(a.rightFqn));
                if (a.label != null && !a.label.isEmpty()) {
                    sb.append(" : ").append(escape(a.label));
                }
                sb.append("\n");
            }

            sb.append("@enduml\n");
            return sb.toString();
        }

        private void printNode(StringBuilder sb, Node n, String indent) {
            String keyword = keywordFor(n);
            String name = n.displayName;
            if (n.stereotypes.contains("Abstract")) {
                name = name + " <<abstract>>";
            }
            sb.append(indent).append(keyword).append(" \"").append(escape(name)).append("\" as ")
                    .append(id(n.fqn)).append(" {").append("\n");

            for (String stereo : stereotypesFor(n)) {
                sb.append(indent).append("  <<").append(escape(stereo)).append(">>\n");
            }
            for (String attr : n.attributes) {
                sb.append(indent).append("  ").append(escape(attr)).append("\n");
            }
            for (String method : n.methods) {
                sb.append(indent).append("  ").append(escape(method)).append("\n");
            }
            sb.append(indent).append("}\n");
        }

        private List<String> stereotypesFor(Node n) {
            List<String> filtered = new ArrayList<>();
            for (String stereo : n.stereotypes) {
                if ("Structure".equalsIgnoreCase(stereo) || "Enumeration".equalsIgnoreCase(stereo)
                        || "Abstract".equalsIgnoreCase(stereo)) {
                    continue; // handled via keyword
                }
                filtered.add(stereo);
            }
            return filtered;
        }

        private String keywordFor(Node n) {
            if (n.stereotypes.contains("Enumeration")) {
                return "enum";
            }
            if (n.stereotypes.contains("Structure")) {
                return "struct";
            }
            return "class";
        }

        private String id(String fqn) {
            return fqn.replaceAll("[^A-Za-z0-9_]", "_");
        }

        private String nsId(String label) {
            return label.replaceAll("[^A-Za-z0-9_]", "_");
        }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
