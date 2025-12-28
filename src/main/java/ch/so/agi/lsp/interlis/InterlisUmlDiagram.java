package ch.so.agi.lsp.interlis;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.interlis.ili2c.metamodel.*;

/**
 * Shared diagram model and builder used by Mermaid and PlantUML renderers.
 */
final class InterlisUmlDiagram {
    private static final Logger LOG = LoggerFactory.getLogger(InterlisUmlDiagram.class);

    private InterlisUmlDiagram() {
    }

    static Diagram build(TransferDescription td) {
        java.util.Objects.requireNonNull(td, "TransferDescription is null");
        return new Ili2cAdapter().buildDiagram(td);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Domain-agnostic diagram model
    // ─────────────────────────────────────────────────────────────────────────────

    static final class Diagram {
        final Map<String, Namespace> namespaces = new LinkedHashMap<>(); // key: fully-qualified namespace label
        final Map<String, Node> nodes = new LinkedHashMap<>(); // key: fully-qualified node name
        final List<Inheritance> inheritances = new ArrayList<>();
        final List<Assoc> assocs = new ArrayList<>();

        Namespace getOrCreateNamespace(String label) {
            return namespaces.computeIfAbsent(label, Namespace::new);
        }
    }

    static final class Namespace {
        final String label; // e.g. "ModelA::TopicB" or just "ModelA" or "<root>"
        final List<String> nodeOrder = new ArrayList<>(); // store node fqns for deterministic ordering

        Namespace(String label) {
            this.label = label;
        }
    }

    static final class Node {
        final String fqn; // e.g. Model.Topic.Class or Model.Class
        final String displayName; // shown inside Mermaid/PlantUML block (without package)
        final Set<String> stereotypes; // e.g. Abstract, Structure, Enumeration, External
        final List<String> attributes; // lines like: name[1] : TypeName
        final List<String> methods;

        Node(String fqn, String displayName, Set<String> stereotypes) {
            this.fqn = fqn;
            this.displayName = displayName;
            this.stereotypes = stereotypes;
            this.attributes = new ArrayList<>();
            this.methods = new ArrayList<>();
        }
    }

    static final class Inheritance {
        final String subFqn; // child
        final String supFqn; // parent (may be external)

        Inheritance(String subFqn, String supFqn) {
            this.subFqn = subFqn;
            this.supFqn = supFqn;
        }
    }

    static final class Assoc {
        final String leftFqn;
        final String rightFqn;
        final String leftCard; // e.g. "1", "0..1", "1..*"
        final String rightCard; // e.g. "*"
        final String label; // optional text label (e.g., role names)

        Assoc(String leftFqn, String rightFqn, String leftCard, String rightCard, String label) {
            this.leftFqn = leftFqn;
            this.rightFqn = rightFqn;
            this.leftCard = leftCard;
            this.rightCard = rightCard;
            this.label = label;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Ili2c → Diagram adapter (Visitor-like traversal)
    // ─────────────────────────────────────────────────────────────────────────────

    private static final class Ili2cAdapter {
        Diagram buildDiagram(TransferDescription td) {
            Diagram d = new Diagram();

            // 1) Only models from the last file.
            Model[] lastModels = td.getModelsFromLastFile();
            if (lastModels == null)
                lastModels = new Model[0];

            // quick lookup for "is in last file" decisions
            Set<Model> lastModelSet = Arrays.stream(lastModels).collect(Collectors.toCollection(LinkedHashSet::new));

            // 2) Register namespaces for each model and topic (and root).
            Namespace root = d.getOrCreateNamespace("<root>");

            // 3) First pass: collect nodes (classes, structures, enumerations, externals when needed)
            for (Model m : sortByName(lastModels)) {
                // Topics
                for (Topic t : getElements(m, Topic.class)) {
                    String nsLabel = m.getName() + "::" + t.getName();
                    d.getOrCreateNamespace(nsLabel); // ensure it exists

                    // Classes/Structures/Associations inside topic
                    collectViewablesInContainer(d, lastModelSet, m, t);

                    // Enums/domains inside topic
                    collectDomains(d, lastModelSet, m, t);
                }

                // Model-level (outside any topic): classes/structures/assocs
                collectViewablesInContainer(d, lastModelSet, m, m);
                collectDomains(d, lastModelSet, m, m);
                collectFunctions(d, m, m);
            }

            // 4) Second pass: inheritance edges and external parents
            for (Node n : new ArrayList<>(d.nodes.values())) {
                Object def = lookupDefinition(td, n.fqn); // our fqn uses names only; we best-effort map back
                if (def instanceof Table tbl) {
                    Element extEl = tbl.getExtending();
                    Table base = (extEl instanceof Table) ? (Table) extEl : null;
                    if (base != null) {
                        String supFqn = fqnOf(base);
                        // ensure parent node exists; if parent is outside last-file models, mark External & place at root
                        if (!belongsToLastFile(base, lastModelSet)) {
                            Node ext = d.nodes.get(supFqn);
                            if (ext == null) {
                                Node extNode = new Node(supFqn, localName(supFqn), setOf("External"));
                                d.nodes.put(extNode.fqn, extNode);
                                root.nodeOrder.add(extNode.fqn);
                            } else {
                                ext.stereotypes.add("External");
                            }
                        }
                        d.inheritances.add(new Inheritance(n.fqn, supFqn));
                    }
                }
            }

            // 5) Associations (with cardinalities on both ends)
            for (Model m : sortByName(lastModels)) {
                // Topics first
                for (Topic t : getElements(m, Topic.class)) {
                    collectAssociations(d, lastModelSet, m, t);
                }
                // Model level
                collectAssociations(d, lastModelSet, m, m);
            }

            // Sort edges deterministically
            d.inheritances.sort(Comparator.comparing((Inheritance i) -> i.subFqn).thenComparing(i -> i.supFqn));
            d.assocs.sort(Comparator.comparing((Assoc a) -> a.leftFqn).thenComparing(a -> a.rightFqn)
                    .thenComparing(a -> a.label == null ? "" : a.label));

            return d;
        }

        private void collectFunctions(Diagram d, Model m, Container container) {
            String namespace = (container instanceof Topic) ? m.getName() + "::" + container.getName() : "<root>";
            Namespace ns = d.getOrCreateNamespace(namespace);
            for (ch.interlis.ili2c.metamodel.Function f : getElements(container,
                    ch.interlis.ili2c.metamodel.Function.class)) {
                String fqn = fqnOf(m, container, f);
                Node node = d.nodes.computeIfAbsent(fqn, k -> new Node(k, f.getName(), setOf("Function")));
                node.stereotypes.add("Function");
                ns.nodeOrder.add(fqn);
            }
        }

        private void collectViewablesInContainer(Diagram d, Set<Model> lastModelSet, Model m, Container container) {
            String namespace = (container instanceof Topic) ? m.getName() + "::" + container.getName() : "<root>";

            Namespace ns = d.getOrCreateNamespace(namespace);

            for (Viewable v : getElements(container, Viewable.class)) {
                if (v instanceof AssociationDef) {
                    // associations handled later to ensure endpoints/nodes exist first
                    continue;
                }
                if (v instanceof Viewable vw) {
                    String fqn = fqnOf(m, container, vw);
                    Set<String> stereos = new LinkedHashSet<>();
                    if (vw.isAbstract())
                        stereos.add("Abstract");
                    if (vw instanceof Table) {
                        Table t = (Table) vw;
                        if (!t.isIdentifiable())
                            stereos.add("Structure");
                    } else {
                        stereos.add("View");
                    }
                    Node node = d.nodes.computeIfAbsent(fqn, k -> new Node(k, vw.getName(), stereos));
                    node.stereotypes.addAll(stereos);

                    // attributes
                    for (AttributeDef a : getElements(vw, AttributeDef.class)) {
                        String card = formatCardinality(a.getCardinality());
                        String typeName = TypeNamer.nameOf(a);
                        if (!typeName.equalsIgnoreCase("ObjectType")) {
                            node.attributes.add(a.getName() + "[" + card + "] : " + typeName);
                        }
                    }

                    int ci = 1;
                    for (Constraint c : getElements(vw, Constraint.class)) {
                        String cname = (c.getName() != null && !c.getName().isEmpty()) ? c.getName()
                                : ("constraint" + ci++);
                        node.methods.add(cname + "()");
                    }
                    ns.nodeOrder.add(fqn);
                }
            }
        }

        private void collectDomains(Diagram d, Set<Model> lastModelSet, Model m, Container container) {
            String namespace = (container instanceof Topic) ? m.getName() + "::" + container.getName() : "<root>";
            Namespace ns = d.getOrCreateNamespace(namespace);

            for (Domain dom : getElements(container, Domain.class)) {
                Type t = dom.getType();
                //LOG.info("type: " + dom.getName() + " " + t);
                if (t instanceof EnumerationType || t instanceof EnumTreeValueType) {
                    String fqn = fqnOf(m, container, dom);
                    Node node = d.nodes.computeIfAbsent(fqn, k -> new Node(k, dom.getName(), setOf("Enumeration")));
                    node.stereotypes.add("Enumeration");
                    ns.nodeOrder.add(fqn);
                }
            }
        }

        private void collectAssociations(Diagram d, Set<Model> lastModelSet, Model m, Container container) {
            for (AssociationDef as : getElements(container, AssociationDef.class)) {
                List<RoleDef> roles = as.getRoles();
                if (roles == null || roles.size() != 2)
                    continue; // only binary associations rendered

                RoleDef a = roles.get(0);
                RoleDef b = roles.get(1);

                AbstractClassDef aEnd = a.getDestination();
                AbstractClassDef bEnd = b.getDestination();
                if (!(aEnd instanceof Table) || !(bEnd instanceof Table))
                    continue;

                Table aTbl = (Table) aEnd;
                Table bTbl = (Table) bEnd;

                String left = fqnOf(aTbl);
                String right = fqnOf(bTbl);

                // Ensure external placeholders for associations if endpoints not in last-file models
                if (!belongsToLastFile(aTbl, lastModelSet))
                    ensureExternalNode(d, aTbl);
                if (!belongsToLastFile(bTbl, lastModelSet))
                    ensureExternalNode(d, bTbl);

                String leftCard = formatCardinality(a.getCardinality());
                String rightCard = formatCardinality(b.getCardinality());

                String label = roleLabel(a) + "–" + roleLabel(b);

                d.assocs.add(new Assoc(left, right, leftCard, rightCard, label));
            }
        }

        private void ensureExternalNode(Diagram d, Table t) {
            String fqn = fqnOf(t);
            if (!d.nodes.containsKey(fqn)) {
                Node ext = new Node(fqn, t.getName(), setOf("External"));
                d.nodes.put(ext.fqn, ext);
                d.getOrCreateNamespace("<root>").nodeOrder.add(ext.fqn);
            } else {
                d.nodes.get(fqn).stereotypes.add("External");
            }
        }

        private static String roleLabel(RoleDef r) {
            String n = r.getName();
            return n != null && !n.isEmpty() ? n : "role";
        }

        private static boolean belongsToLastFile(Element e, Set<Model> lastModels) {
            return lastModels.contains(modelOf(e));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Type naming and cardinality formatting helpers
    // ─────────────────────────────────────────────────────────────────────────────

    static final class TypeNamer {
        static String nameOf(AttributeDef a) {
            Type t = a.getDomain();
            if (t == null)
                return "<Unknown>";
            if (t instanceof ObjectType) {
                return "ObjectType";
            } else if (t instanceof ReferenceType ref) {
                AbstractClassDef target = ref.getReferred();
                if (target != null)
                    return target.getName();
            } else if (t instanceof CompositionType comp) {
                AbstractClassDef target = comp.getComponentType();
                if (target != null)
                    return target.getName();
            } else if (t instanceof SurfaceType) {
                return "Surface";
            } else if (t instanceof MultiSurfaceType) {
                return "MultiSurface";
            } else if (t instanceof AreaType) {
                return "Area";
            } else if (t instanceof MultiAreaType) {
                return "MultiArea";
            } else if (t instanceof PolylineType) {
                return "Polyline";
            } else if (t instanceof MultiPolylineType) {
                return "MultiPolyline";
            } else if (t instanceof CoordType ct) {
                NumericalType[] nts = ct.getDimensions();
                return "Coord" + nts.length;
            } else if (t instanceof MultiCoordType mct) {
                NumericalType[] nts = mct.getDimensions();
                return "MultiCoord" + nts.length;
            } else if (t instanceof NumericType) {
                return "Numeric";
            } else if (t instanceof TextType) {
                return "String";
            } else if (t instanceof EnumerationType) {
                return a.isDomainBoolean() ? "Boolean" : a.getContainer().getName();
            } else if (t instanceof FormattedType ft) {
                if (isDateOrTime(ft)) {
                    return ft.getDefinedBaseDomain().getName();    
                } else {
                    if (ft.getDefinedBaseDomain().getName() != null) {
                        return ft.getDefinedBaseDomain().getName();
                    }
                    return "FormattedType";
                }
            } else if (t instanceof TextOIDType tt) {
                Type textOidType = tt.getOIDType();
                if (textOidType instanceof TypeAlias alias) {
                    return alias.getAliasing().getName();
                } else {
                    return "OID (Text)";
                }
            } else if (t instanceof TypeAlias ta) {
                return ta.getAliasing().getName();
            } 
            String n = t.getName();
            return (n != null && !n.isEmpty()) ? n : t.getClass().getSimpleName();
        }
    }

    private static boolean isDateOrTime(FormattedType formattedType) {
        Domain baseDomain = formattedType.getDefinedBaseDomain();
        return baseDomain == PredefinedModel.getInstance().XmlDate
                || baseDomain == PredefinedModel.getInstance().XmlDateTime
                || baseDomain == PredefinedModel.getInstance().XmlTime;
    }

    static String formatCardinality(Cardinality c) {
        if (c == null)
            return "1";
        long min = c.getMinimum();
        long max = c.getMaximum(); // convention: -1 == unbounded

        String left = String.valueOf(min);
        String right = (max == Long.MAX_VALUE) ? "*" : String.valueOf(max);

        // Compact: show single value when min==max and not unbounded
        if (max >= 0 && min == max)
            return String.valueOf(min);
        return left + ".." + right;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Lookup helpers (best-effort; used only for inheritance/external placement)
    // ─────────────────────────────────────────────────────────────────────────────

    static Object lookupDefinition(TransferDescription td, String fqn) {
        Map<String, Object> byFqn = new HashMap<>();
        for (Model m : Optional.ofNullable(td.getModelsFromLastFile()).orElse(new Model[0])) {
            for (Topic t : getElements(m, Topic.class)) {
                for (Table tbl : getElements(t, Table.class)) {
                    byFqn.put(fqnOf(m, t, tbl), tbl);
                }
            }
            for (Table tbl : getElements(m, Table.class)) {
                byFqn.put(fqnOf(m, m, tbl), tbl);
            }
        }
        return byFqn.get(fqn);
    }

    private static Model modelOf(Element e) {
        Element cur = e;
        while (cur != null && !(cur instanceof Model)) {
            cur = cur.getContainer();
        }
        return (Model) cur;
    }

    private static Container containerOf(Element e) {
        Element cur = e.getContainer();
        if (cur instanceof Container)
            return (Container) cur;
        return null;
    }

    private static String fqnOf(Model m, Container c, Element e) {
        if (c instanceof Topic)
            return m.getName() + "." + c.getName() + "." + e.getName();
        return m.getName() + "." + e.getName();
    }

    private static String fqnOf(Element e) {
        Model m = modelOf(e);
        Container c = containerOf(e);
        return fqnOf(m, c, e);
    }

    private static String localName(String fqn) {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    private static <T extends Element> List<T> getElements(Container c, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Iterator<?> it = c.iterator(); it.hasNext();) {
            Object e = it.next();
            if (type.isInstance(e)) {
                out.add(type.cast(e));
            }
        }
        out.sort(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    private static <T extends Element> List<T> sortByName(T[] arr) {
        if (arr == null)
            return List.of();

        return Arrays.stream(arr)
                .sorted(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private static Set<String> setOf(String... s) {
        return new LinkedHashSet<>(Arrays.asList(s));
    }
}
