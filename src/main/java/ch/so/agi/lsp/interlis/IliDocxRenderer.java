package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiAreaType;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.ObjectType;
import ch.interlis.ili2c.metamodel.PredefinedModel;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextOIDType;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.xmlbeans.XmlBeans;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLevelSuffix;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/**
 * Renders INTERLIS model metadata into a DOCX document.
 */
public final class IliDocxRenderer {
    private IliDocxRenderer() {}

    public static void renderTransferDescription(XWPFDocument doc, TransferDescription td) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(td, "TransferDescription is null");
        ensureAllStyles(doc);

        Model[] lastModels = td.getModelsFromLastFile();
        if (lastModels == null) {
            lastModels = new Model[0];
        }

        for (Model model : sortByName(lastModels)) {
            writeHeading(doc, model.getName(), 0);

            List<Table> rootClasses = getElements(model, Table.class);
            if (!rootClasses.isEmpty()) {
                XWPFParagraph p = doc.createParagraph();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rootClasses.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(rootClasses.get(i).getName());
                }
                p.createRun().setText(sb.toString());

                for (Table cls : rootClasses) {
                    writeClassHeading(doc, cls, 1);
                    writeAttributeTable(doc, collectRowsForClass(model, model, cls));
                }
            }

            for (Topic topic : getElements(model, Topic.class)) {
                writeHeading(doc, topic.getName(), 0);
                for (Table cls : getElements(topic, Table.class)) {
                    writeClassHeading(doc, cls, 1);
                    writeAttributeTable(doc, collectRowsForClass(model, topic, cls));
                }
            }
        }
    }

    static void ensureAllStyles(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();

        if (!styles.styleExist("Title")) {
            styles.addStyle(buildParagraphStyle("Title", "Title", null, true, 28));
        } else {
            XWPFStyle style = styles.getStyle("Title");
            if (style != null) {
                CTRPr rpr = style.getCTStyle().isSetRPr() ? style.getCTStyle().getRPr() : style.getCTStyle().addNewRPr();
                BigInteger halfPts = BigInteger.valueOf(28L * 2L);
                CTHpsMeasure sz = rpr.sizeOfSzArray() > 0 ? rpr.getSzArray(0) : rpr.addNewSz();
                sz.setVal(halfPts);
                CTHpsMeasure szCs = rpr.sizeOfSzCsArray() > 0 ? rpr.getSzCsArray(0) : rpr.addNewSzCs();
                szCs.setVal(halfPts);
                if (rpr.sizeOfBArray() == 0) {
                    rpr.addNewB();
                }
            }
        }

        if (!styles.styleExist("Heading1")) {
            styles.addStyle(buildParagraphStyle("Heading1", "Heading 1", BigInteger.ZERO, true, null));
        }
        if (!styles.styleExist("Heading2")) {
            styles.addStyle(buildParagraphStyle("Heading2", "Heading 2", BigInteger.ONE, true, null));
        }
    }

    private static XWPFStyle buildParagraphStyle(String styleId, String name, BigInteger outlineLevel,
            boolean bold, Integer sizePt) {
        CTStyle ctStyle = (CTStyle) XmlBeans.getContextTypeLoader().newInstance(CTStyle.type, null);
        ctStyle.setStyleId(styleId);
        ctStyle.setType(STStyleType.PARAGRAPH);
        ctStyle.addNewName().setVal(name);

        CTPPrGeneral ppr = ctStyle.isSetPPr() ? ctStyle.getPPr() : ctStyle.addNewPPr();
        if (outlineLevel != null) {
            CTDecimalNumber lvl = ppr.isSetOutlineLvl() ? ppr.getOutlineLvl() : ppr.addNewOutlineLvl();
            lvl.setVal(outlineLevel);
        }

        CTRPr rpr = ctStyle.isSetRPr() ? ctStyle.getRPr() : ctStyle.addNewRPr();
        if (bold && rpr.sizeOfBArray() == 0) {
            rpr.addNewB();
        }
        if (sizePt != null) {
            BigInteger halfPts = BigInteger.valueOf(sizePt.longValue() * 2L);
            CTHpsMeasure sz = rpr.sizeOfSzArray() > 0 ? rpr.getSzArray(0) : rpr.addNewSz();
            sz.setVal(halfPts);
            CTHpsMeasure szCs = rpr.sizeOfSzCsArray() > 0 ? rpr.getSzCsArray(0) : rpr.addNewSzCs();
            szCs.setVal(halfPts);
        }

        ctStyle.addNewQFormat();
        return new XWPFStyle(ctStyle);
    }

    private static int ensureHeadingNumbering(XWPFDocument doc) {
        XWPFNumbering numbering = doc.createNumbering();

        try {
            for (XWPFNum num : numbering.getNums()) {
                if (num != null && num.getCTNum() != null && num.getCTNum().getNumId() != null) {
                    return num.getCTNum().getNumId().intValue();
                }
            }
        } catch (Throwable ignore) {
            // fall back to creating numbering
        }

        CTAbstractNum ctAbstract = (CTAbstractNum) XmlBeans.getContextTypeLoader()
                .newInstance(CTAbstractNum.type, null);
        ctAbstract.setAbstractNumId(BigInteger.ONE);

        CTLvl lvl0 = ctAbstract.addNewLvl();
        lvl0.setIlvl(BigInteger.ZERO);
        lvl0.addNewStart().setVal(BigInteger.ONE);
        lvl0.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl0.addNewLvlText().setVal("%1 ");
        lvl0.addNewSuff().setVal(STLevelSuffix.SPACE);

        CTLvl lvl1 = ctAbstract.addNewLvl();
        lvl1.setIlvl(BigInteger.ONE);
        lvl1.addNewStart().setVal(BigInteger.ONE);
        lvl1.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl1.addNewLvlText().setVal("%1.%2 ");
        lvl1.addNewSuff().setVal(STLevelSuffix.SPACE);

        XWPFAbstractNum abstractNum = new XWPFAbstractNum(ctAbstract);
        BigInteger absId = numbering.addAbstractNum(abstractNum);
        if (absId == null) {
            absId = BigInteger.ONE;
        }

        BigInteger numId;
        try {
            numId = numbering.addNum(absId);
        } catch (Throwable ignore) {
            CTNum ctNum = (CTNum) XmlBeans.getContextTypeLoader().newInstance(CTNum.type, null);
            ctNum.setNumId(BigInteger.ONE);
            ctNum.addNewAbstractNumId().setVal(absId);
            numId = numbering.addNum(new XWPFNum(ctNum, numbering));
        }
        if (numId == null) {
            numId = BigInteger.ONE;
        }
        return numId.intValue();
    }

    private static void applyNumbering(XWPFParagraph paragraph, int level) {
        int numId = ensureHeadingNumbering(paragraph.getDocument());
        CTPPr ppr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTNumPr numPr = ppr.isSetNumPr() ? ppr.getNumPr() : ppr.addNewNumPr();
        (numPr.isSetNumId() ? numPr.getNumId() : numPr.addNewNumId()).setVal(BigInteger.valueOf(numId));
        (numPr.isSetIlvl() ? numPr.getIlvl() : numPr.addNewIlvl()).setVal(BigInteger.valueOf(level));
    }

    private static void writeHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle(level <= 0 ? "Heading1" : "Heading2");
        applyNumbering(paragraph, level <= 0 ? 0 : 1);
        paragraph.createRun().setText(text != null ? text : "");
    }

    private static void writeClassHeading(XWPFDocument doc, Table table, int level) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle(level <= 0 ? "Heading1" : "Heading2");
        applyNumbering(paragraph, level <= 0 ? 0 : 1);
        String stereos = classStereotypes(table);
        String title = stereos.isEmpty() ? table.getName() : table.getName() + " " + stereos;
        paragraph.createRun().setText(title);
    }

    private static String classStereotypes(Table table) {
        boolean structure = !table.isIdentifiable();
        boolean isAbstract = table.isAbstract();
        if (structure) {
            return "(Structure)";
        }
        if (isAbstract) {
            return "(Abstract Class)";
        }
        return "(Class)";
    }

    private static List<Row> collectRowsForClass(Model model, Container scope, Table cls) {
        List<Row> rows = new ArrayList<>();

        for (AttributeDef attribute : getElements(cls, AttributeDef.class)) {
            String type = typeName(attribute);
            if ("ObjectType".equalsIgnoreCase(type)) {
                continue;
            }
            rows.add(new Row(attribute.getName(), formatCardinality(attribute.getCardinality()), type, docOf(attribute)));
        }

        for (AssociationDef assoc : collectAssociations(model, scope)) {
            List<RoleDef> roles = assoc.getRoles();
            if (roles == null || roles.size() != 2) {
                continue;
            }
            RoleDef left = roles.get(0);
            RoleDef right = roles.get(1);
            addAssociationRow(rows, left, right, cls);
            addAssociationRow(rows, right, left, cls);
        }

        return rows;
    }

    private static void addAssociationRow(List<Row> rows, RoleDef me, RoleDef other, Table cls) {
        AbstractClassDef destination = me.getDestination();
        AbstractClassDef otherDest = other.getDestination();
        if (destination instanceof Table && otherDest instanceof Table) {
            if (destination == cls) {
                rows.add(new Row(roleLabel(me), formatCardinality(me.getCardinality()),
                        ((Table) otherDest).getName(), ""));
            }
        }
    }

    private static void writeAttributeTable(XWPFDocument doc, List<Row> rows) {
        CTBody body = doc.getDocument().getBody();
        CTTbl table = body.addNewTbl();

        final int cols = 4;
        CTTblGrid grid = table.addNewTblGrid();
        for (int i = 0; i < cols; i++) {
            grid.addNewGridCol();
        }

        CTRow header = table.addNewTr();
        addCellText(header, "Attributname");
        addCellText(header, "Kardinalität");
        addCellText(header, "Typ");
        addCellText(header, "Beschreibung");

        if (rows != null) {
            for (Row row : rows) {
                CTRow tr = table.addNewTr();
                addCellText(tr, nz(row.name));
                addCellText(tr, nz(row.card));
                addCellText(tr, nz(row.type));
                addCellText(tr, nz(row.descr));
            }
        }

        doc.createParagraph();
    }

    private static void addCellText(CTRow tr, String text) {
        CTTc cell = tr.addNewTc();
        CTP paragraph = cell.addNewP();
        CTR run = paragraph.addNewR();
        CTText t = run.addNewT();
        t.setStringValue(text != null ? text : "");
    }

    private static List<AssociationDef> collectAssociations(Model model, Container scope) {
        List<AssociationDef> list = new ArrayList<>();
        for (AssociationDef assoc : getElements(scope, AssociationDef.class)) {
            list.add(assoc);
        }
        return list;
    }

    private static String roleLabel(RoleDef role) {
        String name = role.getName();
        return (name != null && !name.isEmpty()) ? name : "role";
    }

    private static String typeName(AttributeDef attribute) {
        Type type = attribute.getDomain();
        if (type == null) {
            return "<Unknown>";
        }
        if (type instanceof ObjectType) {
            return "ObjectType";
        }
        if (type instanceof ReferenceType ref) {
            AbstractClassDef target = ref.getReferred();
            return target != null ? target.getName() : "Reference";
        }
        if (type instanceof CompositionType comp) {
            AbstractClassDef target = comp.getComponentType();
            return target != null ? target.getName() : "Composition";
        }
        if (type instanceof EnumerationType) {
            return attribute.isDomainBoolean() ? "Boolean" : attribute.getContainer().getName();
        }
        if (type instanceof SurfaceType) {
            return "Surface";
        }
        if (type instanceof MultiSurfaceType) {
            return "MultiSurface";
        }
        if (type instanceof AreaType) {
            return "Area";
        }
        if (type instanceof MultiAreaType) {
            return "MultiArea";
        }
        if (type instanceof MultiPolylineType) {
            return "MultiPolyline";
        }
        if (type instanceof PolylineType) {
            return "Polyline";
        }
        if (type instanceof CoordType coord) {
            NumericalType[] dims = coord.getDimensions();
            return "Coord" + (dims != null ? dims.length : 0);
        }
        if (type instanceof MultiCoordType multiCoord) {
            NumericalType[] dims = multiCoord.getDimensions();
            return "MultiCoord" + (dims != null ? dims.length : 0);
        }
        if (type instanceof NumericalType) {
            return "Numeric";
        }
        if (type instanceof TextType) {
            return "Text";
        }
        if (type instanceof TextOIDType oid) {
            Type base = oid.getOIDType();
            if (base instanceof TypeAlias alias) {
                return alias.getAliasing().getName();
            }
            return base != null ? base.getName() : "TextOID";
        }
        if (type instanceof FormattedType formatted && isDateOrTime(formatted)) {
            Domain base = formatted.getDefinedBaseDomain();
            return base != null ? base.getName() : "DateTime";
        }
        if (type instanceof TypeAlias alias) {
            return alias.getAliasing().getName();
        }
        String name = type.getName();
        return (name != null && !name.isEmpty()) ? name : type.getClass().getSimpleName();
    }

    private static boolean isDateOrTime(FormattedType type) {
        Domain base = type.getDefinedBaseDomain();
        return base == PredefinedModel.getInstance().XmlDate
                || base == PredefinedModel.getInstance().XmlDateTime
                || base == PredefinedModel.getInstance().XmlTime;
    }

    static String formatCardinality(Cardinality card) {
        if (card == null) {
            return "1";
        }
        long min = card.getMinimum();
        long max = card.getMaximum();
        String left = String.valueOf(min);
        String right = (max == Long.MAX_VALUE) ? "*" : String.valueOf(max);
        if (max >= 0 && min == max) {
            return left;
        }
        return left + ".." + right;
    }

    private static String docOf(Element element) {
        if (element instanceof AttributeDef attr) {
            String doc = attr.getDocumentation();
            return doc != null ? doc : "";
        }
        return "";
    }

    private static <T extends Element> List<T> getElements(Container container, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (container == null) {
            return out;
        }
        for (Iterator<?> it = container.iterator(); it.hasNext();) {
            Object element = it.next();
            if (type.isInstance(element)) {
                out.add(type.cast(element));
            }
        }
        out.sort(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    private static <T extends Element> List<T> sortByName(T[] arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>(Arrays.asList(arr));
        list.sort(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private record Row(String name, String card, String type, String descr) {}
}
