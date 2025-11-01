package ch.so.agi.lsp.interlis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Assoc;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Diagram;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Inheritance;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Namespace;
import ch.so.agi.lsp.interlis.InterlisUmlDiagram.Node;

/** Renderer that exports the UML diagram into a yEd-compatible GraphML document. */
public final class Ili2GraphML {
    private Ili2GraphML() {
    }

    public static String render(TransferDescription td) {
        Objects.requireNonNull(td, "TransferDescription is null");
        Diagram diagram = InterlisUmlDiagram.build(td);
        return new GraphMLRenderer().render(diagram);
    }

    static final class GraphMLRenderer {
        private final Map<String, String> nodeIds = new LinkedHashMap<>();
        private int nodeCounter = 0;
        private int edgeCounter = 0;

        String render(Diagram d) {
            StringBuilder sb = new StringBuilder(8_192);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
            sb.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
            sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            sb.append("         xmlns:y=\"http://www.yworks.com/xml/graphml\"\n");
            sb.append("         xmlns:yed=\"http://www.yworks.com/xml/yed/3\"\n");
            sb.append("         xmlns:java=\"http://www.yworks.com/xml/yfiles-common/1.0/java\"\n");
            sb.append("         xmlns:sys=\"http://www.yworks.com/xml/yfiles-common/markup/primitives/2\"\n");
            sb.append("         xmlns:x=\"http://www.yworks.com/xml/yfiles-common/markup/2.0\">\n");
            sb.append("  <key id=\"d0\" for=\"node\" yfiles.type=\"nodegraphics\"/>\n");
            sb.append("  <key id=\"d1\" for=\"edge\" yfiles.type=\"edgegraphics\"/>\n");
            sb.append("  <graph edgedefault=\"directed\" id=\"G\">\n");

            // Nodes grouped by namespaces for deterministic ordering
            d.namespaces.values().forEach(ns -> {
                if ("<root>".equals(ns.label)) {
                    return;
                }
                appendTopicGroup(sb, d, ns);
            });

            Namespace root = d.namespaces.get("<root>");
            if (root != null) {
                if (!root.nodeOrder.isEmpty()) {
                    sb.append("    <!-- Namespace: <root> -->\n");
                }
                for (String fqn : root.nodeOrder) {
                    Node node = d.nodes.get(fqn);
                    if (node != null && !nodeIds.containsKey(node.fqn)) {
                        appendNode(sb, node, "    ");
                    }
                }
            }

            // Ensure all nodes are emitted even if not referenced in namespaces (safety net)
            d.nodes.values().forEach(node -> {
                if (!nodeIds.containsKey(node.fqn)) {
                    appendNode(sb, node, "    ");
                }
            });

            for (Inheritance inheritance : d.inheritances) {
                appendInheritance(sb, inheritance);
            }
            for (Assoc assoc : d.assocs) {
                appendAssociation(sb, assoc);
            }

            sb.append("  </graph>\n");
            sb.append("</graphml>\n");
            return sb.toString();
        }

        private void appendTopicGroup(StringBuilder sb, Diagram d, Namespace ns) {
            String groupId = nextNodeId();
            String topicLabel = topicName(ns.label);

            sb.append("    <node id=\"").append(groupId).append("\" yfiles.foldertype=\"group\">\n");
            sb.append("      <data key=\"d0\">\n");
            sb.append("        <y:GroupNode>\n");
            sb.append("          <y:Geometry height=\"400.0\" width=\"400.0\" x=\"0.0\" y=\"0.0\"/>\n");
            sb.append("          <y:Fill color=\"#FFFFFF\" transparent=\"false\"/>\n");
            sb.append("          <y:BorderStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:NodeLabel alignment=\"center\" autoSizePolicy=\"content\" fontFamily=\"Dialog\" fontSize=\"13\" fontStyle=\"bold\" hasBackgroundColor=\"false\" hasLineColor=\"false\" modelName=\"internal\" modelPosition=\"t\" visible=\"true\">")
                    .append(escapeXml(topicLabel)).append("</y:NodeLabel>\n");
            sb.append("          <y:State closed=\"false\"/>\n");
            sb.append("          <y:Insets bottom=\"15\" left=\"15\" right=\"15\" top=\"45\"/>\n");
            sb.append("        </y:GroupNode>\n");
            sb.append("      </data>\n");
            sb.append("      <graph edgedefault=\"directed\" id=\"").append(groupId).append(":\">\n");

            for (String fqn : ns.nodeOrder) {
                Node node = d.nodes.get(fqn);
                if (node != null && !nodeIds.containsKey(node.fqn)) {
                    appendNode(sb, node, "        ");
                }
            }

            sb.append("      </graph>\n");
            sb.append("    </node>\n");
        }

        private String nextNodeId() {
            return "n" + (nodeCounter++);
        }

        private static String topicName(String namespaceLabel) {
            int idx = namespaceLabel.indexOf("::");
            return idx >= 0 ? namespaceLabel.substring(idx + 2) : namespaceLabel;
        }

        private void appendNode(StringBuilder sb, Node node, String indent) {
            String id = nodeIds.computeIfAbsent(node.fqn, k -> "n" + (nodeCounter++));

            sb.append(indent).append("<node id=\"").append(id).append("\">\n");
            sb.append(indent).append("  <data key=\"d0\">\n");
            sb.append(indent).append("    <y:UMLClassNode>\n");
            sb.append(indent).append("      <y:Geometry height=\"120.0\" width=\"180.0\" x=\"0.0\" y=\"0.0\"/>\n");
            sb.append(indent).append("      <y:Fill color=\"").append(determineFillColor(node)).append("\" transparent=\"false\"/>\n");
            sb.append(indent).append("      <y:BorderStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append(indent).append("      <y:NodeLabel alignment=\"center\" autoSizePolicy=\"content\" fontFamily=\"Dialog\" fontSize=\"12\" fontStyle=\"plain\" hasBackgroundColor=\"false\" hasLineColor=\"false\" modelName=\"sandwich\" modelPosition=\"n\" visible=\"true\">")
                    .append(escapeXml(node.displayName)).append("</y:NodeLabel>\n");

            String stereotypeText = formatStereotypes(node.stereotypes);
            String constraintText = node.stereotypes.contains("Abstract") ? "abstract" : "";
            String attributesText = joinWithNewlines(node.attributes);
            String methodsText = joinWithNewlines(node.methods);

            sb.append(indent).append("      <y:UML clipContent=\"true\" constraint=\"")
                    .append(escapeXml(constraintText)).append("\" omitDetails=\"false\" stereotype=\"")
                    .append(escapeXml(stereotypeText)).append("\" use3DEffect=\"false\">\n");
            sb.append(indent).append("        <y:AttributeLabel>").append(escapeXml(attributesText)).append("</y:AttributeLabel>\n");
            sb.append(indent).append("        <y:MethodLabel>").append(escapeXml(methodsText)).append("</y:MethodLabel>\n");
            sb.append(indent).append("      </y:UML>\n");
            sb.append(indent).append("    </y:UMLClassNode>\n");
            sb.append(indent).append("  </data>\n");
            sb.append(indent).append("</node>\n");
        }

        private void appendInheritance(StringBuilder sb, Inheritance inheritance) {
            String source = nodeIds.computeIfAbsent(inheritance.subFqn, k -> "n" + (nodeCounter++));
            String target = nodeIds.computeIfAbsent(inheritance.supFqn, k -> "n" + (nodeCounter++));
            String edgeId = "e" + (edgeCounter++);

            sb.append("    <edge id=\"").append(edgeId).append("\" source=\"").append(source).append("\" target=\"")
                    .append(target).append("\">\n");
            sb.append("      <data key=\"d1\">\n");
            sb.append("        <y:PolyLineEdge>\n");
            sb.append("          <y:Path sx=\"0.0\" sy=\"0.0\" tx=\"0.0\" ty=\"0.0\"/>\n");
            sb.append("          <y:LineStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:Arrows source=\"none\" target=\"white_delta\"/>\n");
            sb.append("          <y:BendStyle smoothed=\"false\"/>\n");
            sb.append("        </y:PolyLineEdge>\n");
            sb.append("      </data>\n");
            sb.append("    </edge>\n");
        }

        private void appendAssociation(StringBuilder sb, Assoc assoc) {
            String source = nodeIds.computeIfAbsent(assoc.leftFqn, k -> "n" + (nodeCounter++));
            String target = nodeIds.computeIfAbsent(assoc.rightFqn, k -> "n" + (nodeCounter++));
            String edgeId = "e" + (edgeCounter++);

            sb.append("    <edge id=\"").append(edgeId).append("\" source=\"").append(source).append("\" target=\"")
                    .append(target).append("\">\n");
            sb.append("      <data key=\"d1\">\n");
            sb.append("        <y:PolyLineEdge>\n");
            sb.append("          <y:Path sx=\"0.0\" sy=\"0.0\" tx=\"0.0\" ty=\"0.0\"/>\n");
            sb.append("          <y:LineStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:Arrows source=\"none\" target=\"none\"/>\n");

            String labelText = assoc.leftCard + " ⟷ " + assoc.rightCard;
            if (assoc.label != null && !assoc.label.isBlank()) {
                labelText = labelText + " (" + assoc.label + ")";
            }
            sb.append("          <y:EdgeLabel alignment=\"center\" configuration=\"AutoFlippingLabel\" distance=\"2.0\" fontFamily=\"Dialog\" fontSize=\"11\" fontStyle=\"plain\" hasBackgroundColor=\"false\" hasLineColor=\"false\" modelName=\"two_pos\" preferredPlacement=\"center\" ratio=\"0.5\" textColor=\"#000000\" visible=\"true\">")
                    .append(escapeXml(labelText)).append("</y:EdgeLabel>\n");
            sb.append("          <y:BendStyle smoothed=\"false\"/>\n");
            sb.append("        </y:PolyLineEdge>\n");
            sb.append("      </data>\n");
            sb.append("    </edge>\n");
        }

        private static String determineFillColor(Node node) {
            if (node.stereotypes.contains("Enumeration")) {
                return "#ff9933";
            }
            if (node.stereotypes.contains("Structure")) {
                return "#ffcc00";
            }
            if (node.stereotypes.contains("Abstract")) {
                return "#99ccff";
            }
            return "#04b889";
        }

        private static String formatStereotypes(Set<String> stereotypes) {
            if (stereotypes == null || stereotypes.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String stereo : stereotypes) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(stereo);
            }
            return sb.toString();
        }

        private static String joinWithNewlines(Iterable<String> values) {
            StringBuilder sb = new StringBuilder();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(value);
            }
            return sb.toString();
        }

        private static String escapeXml(String input) {
            if (input == null || input.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(input.length());
            for (int i = 0; i < input.length(); i++) {
                char ch = input.charAt(i);
                switch (ch) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                case '\n':
                    sb.append("&#10;");
                    break;
                case '\r':
                    sb.append("&#13;");
                    break;
                case '\t':
                    sb.append("&#9;");
                    break;
                default:
                    sb.append(ch);
                    break;
                }
            }
            return sb.toString();
        }
    }
}

