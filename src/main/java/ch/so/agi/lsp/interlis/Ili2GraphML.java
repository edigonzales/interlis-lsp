package ch.so.agi.lsp.interlis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Assoc;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Diagram;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Inheritance;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Namespace;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Node;

/**
 * Renders the intermediate {@link InterlisUmlDiagram.Diagram} model as a yEd GraphML document.
 */
public final class Ili2GraphML {
    private Ili2GraphML() {
    }

    /** Returns a GraphML document representing the INTERLIS model. */
    public static String render(TransferDescription td) {
        Objects.requireNonNull(td, "TransferDescription is null");
        Diagram diagram = InterlisUmlDiagram.build(td);
        return new GraphMlRenderer().render(diagram);
    }

    static final class GraphMlRenderer {
        private final Map<String, String> nodeIds = new LinkedHashMap<>();
        private int edgeCounter = 0;

        String render(Diagram d) {
            StringBuilder sb = new StringBuilder(8_192);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" ")
                    .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                    .append("xmlns:y=\"http://www.yworks.com/xml/graphml\" ")
                    .append("xmlns:yed=\"http://www.yworks.com/xml/yed/3\" ")
                    .append("xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns ")
                    .append("http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd\">\n");
            sb.append("  <key id=\"d0\" for=\"node\" yfiles.type=\"nodegraphics\"/>\n");
            sb.append("  <key id=\"d1\" for=\"edge\" yfiles.type=\"edgegraphics\"/>\n");
            sb.append("  <graph id=\"G\" edgedefault=\"directed\">\n");

            d.namespaces.values().forEach(ns -> {
                if (ns.label.equals("<root>")) {
                    return;
                }
                for (String fqn : ns.nodeOrder) {
                    Node n = d.nodes.get(fqn);
                    printNode(sb, n, ns.label.equals("<root>") ? null : ns.label);
                }
            });

            Namespace root = d.namespaces.get("<root>");
            if (root != null) {
                for (String fqn : root.nodeOrder) {
                    Node n = d.nodes.get(fqn);
                    printNode(sb, n, null);
                }
            }

            for (Inheritance inheritance : d.inheritances) {
                printInheritance(sb, inheritance);
            }

            for (Assoc assoc : d.assocs) {
                printAssociation(sb, assoc);
            }

            sb.append("  </graph>\n");
            sb.append("</graphml>\n");
            return sb.toString();
        }

        private void printNode(StringBuilder sb, Node n, String namespaceLabel) {
            String id = nodeIds.computeIfAbsent(n.fqn, this::nodeId);
            sb.append("    <node id=\"").append(id).append("\">\n");
            sb.append("      <data key=\"d0\">\n");
            sb.append("        <y:ShapeNode>\n");
            sb.append("          <y:Geometry height=\"60.0\" width=\"160.0\"/>\n");
            sb.append("          <y:Fill color=\"#FFFFFF\" transparent=\"false\"/>\n");
            sb.append("          <y:BorderStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:NodeLabel alignment=\"center\" autoSizePolicy=\"content\" ")
                    .append("fontFamily=\"Courier New\" fontSize=\"12\" ")
                    .append("textColor=\"#000000\" xml:space=\"preserve\">")
                    .append(escape(labelFor(n, namespaceLabel)))
                    .append("</y:NodeLabel>\n");
            sb.append("          <y:Shape type=\"").append(shapeFor(n.stereotypes)).append("\"/>\n");
            sb.append("        </y:ShapeNode>\n");
            sb.append("      </data>\n");
            sb.append("    </node>\n");
        }

        private void printInheritance(StringBuilder sb, Inheritance inheritance) {
            String id = "e" + edgeCounter++;
            String source = nodeIds.computeIfAbsent(inheritance.subFqn, this::nodeId);
            String target = nodeIds.computeIfAbsent(inheritance.supFqn, this::nodeId);
            sb.append("    <edge id=\"").append(id).append("\" source=\"").append(source)
                    .append("\" target=\"").append(target).append("\">\n");
            sb.append("      <data key=\"d1\">\n");
            sb.append("        <y:PolyLineEdge>\n");
            sb.append("          <y:LineStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:Arrows source=\"none\" target=\"white_delta\"/>\n");
            sb.append("        </y:PolyLineEdge>\n");
            sb.append("      </data>\n");
            sb.append("    </edge>\n");
        }

        private void printAssociation(StringBuilder sb, Assoc assoc) {
            String id = "e" + edgeCounter++;
            String source = nodeIds.computeIfAbsent(assoc.leftFqn, this::nodeId);
            String target = nodeIds.computeIfAbsent(assoc.rightFqn, this::nodeId);
            sb.append("    <edge id=\"").append(id).append("\" source=\"").append(source)
                    .append("\" target=\"").append(target).append("\">\n");
            sb.append("      <data key=\"d1\">\n");
            sb.append("        <y:PolyLineEdge>\n");
            sb.append("          <y:LineStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:Arrows source=\"none\" target=\"none\"/>\n");
            String labelText = associationLabel(assoc);
            if (!labelText.isBlank()) {
                sb.append("          <y:EdgeLabel alignment=\"center\" distance=\"2.0\" ")
                        .append("preferredPlacement=\"center\" ")
                        .append("textColor=\"#000000\" visible=\"true\" xml:space=\"preserve\">")
                        .append(escape(labelText)).append("</y:EdgeLabel>\n");
            }
            sb.append("        </y:PolyLineEdge>\n");
            sb.append("      </data>\n");
            sb.append("    </edge>\n");
        }

        private String associationLabel(Assoc assoc) {
            StringBuilder sb = new StringBuilder();
            if (assoc.leftCard != null && !assoc.leftCard.isBlank()) {
                sb.append(assoc.leftCard.trim());
            }
            if (assoc.rightCard != null && !assoc.rightCard.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" -- ");
                }
                sb.append(assoc.rightCard.trim());
            }
            if (assoc.label != null && !assoc.label.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" : ");
                }
                sb.append(assoc.label.trim());
            }
            return sb.toString();
        }

        private String labelFor(Node node, String namespaceLabel) {
            List<String> lines = new ArrayList<>();
            if (namespaceLabel != null && !namespaceLabel.isBlank()) {
                lines.add(namespaceLabel);
            }
            String name = node.displayName;
            if (node.stereotypes.contains("Abstract")) {
                name = name + " <<abstract>>";
            }
            lines.add(name);

            if (node.stereotypes.contains("Structure")) {
                lines.add("<<Structure>>");
            }
            if (node.stereotypes.contains("Enumeration")) {
                lines.add("<<Enumeration>>");
            }

            List<String> extraStereos = new ArrayList<>();
            for (String stereo : node.stereotypes) {
                if ("Abstract".equalsIgnoreCase(stereo) || "Structure".equalsIgnoreCase(stereo)
                        || "Enumeration".equalsIgnoreCase(stereo)) {
                    continue;
                }
                extraStereos.add(stereo);
            }
            extraStereos.sort(String::compareToIgnoreCase);
            for (String stereo : extraStereos) {
                lines.add("<<" + stereo + ">>");
            }

            if (!node.attributes.isEmpty()) {
                lines.add("---");
                lines.addAll(node.attributes);
            }

            if (!node.methods.isEmpty()) {
                lines.add("---");
                lines.addAll(node.methods);
            }

            return String.join("\n", lines);
        }

        private String nodeId(String fqn) {
            String sanitized = fqn.replaceAll("[^A-Za-z0-9_]", "_");
            if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
                sanitized = "n_" + sanitized;
            }
            return sanitized;
        }

        private String shapeFor(Set<String> stereos) {
            if (stereos.contains("Enumeration")) {
                return "roundrectangle";
            }
            if (stereos.contains("Structure")) {
                return "rectangle";
            }
            return "rectangle";
        }

        private String escape(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&apos;");
        }
    }
}
