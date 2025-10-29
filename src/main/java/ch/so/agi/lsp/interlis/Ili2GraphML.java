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
                sb.append("    <!-- Namespace: ").append(escapeXml(ns.label)).append(" -->\n");
                for (String fqn : ns.nodeOrder) {
                    Node node = d.nodes.get(fqn);
                    if (node != null && !nodeIds.containsKey(node.fqn)) {
                        appendNode(sb, node, ns.label);
                    }
                }
            });

            Namespace root = d.namespaces.get("<root>");
            if (root != null) {
                if (!root.nodeOrder.isEmpty()) {
                    sb.append("    <!-- Namespace: <root> -->\n");
                }
                for (String fqn : root.nodeOrder) {
                    Node node = d.nodes.get(fqn);
                    if (node != null && !nodeIds.containsKey(node.fqn)) {
                        appendNode(sb, node, "<root>");
                    }
                }
            }

            // Ensure all nodes are emitted even if not referenced in namespaces (safety net)
            d.nodes.values().forEach(node -> {
                if (!nodeIds.containsKey(node.fqn)) {
                    appendNode(sb, node, "<root>");
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

        private void appendNode(StringBuilder sb, Node node, String namespaceLabel) {
            String id = nodeIds.computeIfAbsent(node.fqn, k -> "n" + (nodeCounter++));

            sb.append("    <node id=\"").append(id).append("\">\n");
            sb.append("      <data key=\"d0\">\n");
            sb.append("        <y:UMLClassNode>\n");
            sb.append("          <y:Geometry height=\"120.0\" width=\"180.0\" x=\"0.0\" y=\"0.0\"/>\n");
            sb.append("          <y:Fill color=\"#FFFFFF\" transparent=\"false\"/>\n");
            sb.append("          <y:BorderStyle color=\"#000000\" type=\"line\" width=\"1.0\"/>\n");
            sb.append("          <y:NodeLabel alignment=\"center\" autoSizePolicy=\"content\" fontFamily=\"Dialog\" fontSize=\"12\" fontStyle=\"plain\" hasBackgroundColor=\"false\" hasLineColor=\"false\" modelName=\"sandwich\" modelPosition=\"n\" visible=\"true\">")
                    .append(escapeXml(node.displayName)).append("</y:NodeLabel>\n");

            String stereotypeText = formatStereotypes(node.stereotypes);
            String constraintText = "<root>".equals(namespaceLabel) ? node.fqn : namespaceLabel.replace("::", " → ");
            String attributesText = joinWithNewlines(node.attributes);
            String methodsText = joinWithNewlines(node.methods);

            sb.append("          <y:UML clipContent=\"true\" constraint=\"")
                    .append(escapeXml(constraintText)).append("\" omitDetails=\"false\" stereotype=\"")
                    .append(escapeXml(stereotypeText)).append("\" use3DEffect=\"false\">\n");
            sb.append("            <y:AttributeLabel>").append(escapeXml(attributesText)).append("</y:AttributeLabel>\n");
            sb.append("            <y:MethodLabel>").append(escapeXml(methodsText)).append("</y:MethodLabel>\n");
            sb.append("          </y:UML>\n");
            sb.append("        </y:UMLClassNode>\n");
            sb.append("      </data>\n");
            sb.append("    </node>\n");
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

        private static String formatStereotypes(Set<String> stereotypes) {
            if (stereotypes == null || stereotypes.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String stereo : stereotypes) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append("<<").append(stereo).append(">>");
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

