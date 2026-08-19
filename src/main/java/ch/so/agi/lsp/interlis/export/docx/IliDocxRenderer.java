package ch.so.agi.lsp.interlis.export.docx;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Constant;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Evaluable;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.EnumTreeValueType;
import ch.interlis.ili2c.metamodel.Expression;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.FunctionCall;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiAreaType;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.NumericType;
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
import ch.interlis.ili2c.metamodel.ObjectPath;
import ch.interlis.ili2c.metamodel.UniqueEl;
import ch.interlis.ili2c.metamodel.UniquenessConstraint;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlBeans;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLevelSuffix;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/**
 * Renders INTERLIS model metadata into a DOCX document.
 */
public final class IliDocxRenderer {
    private static final String FONT_FAMILY = "Arial";
    private static final BigInteger DEFAULT_SPACING_AFTER = BigInteger.valueOf(144L);
    private static final BigInteger ATTRIBUTE_TABLE_WIDTH = BigInteger.valueOf(13500L);
    private static final BigInteger ENUM_TABLE_WIDTH = ATTRIBUTE_TABLE_WIDTH;
    private static final BigInteger[] TABLE_COLUMN_WIDTHS = new BigInteger[] {
            BigInteger.valueOf(3000L),
            BigInteger.valueOf(1500L),
            BigInteger.valueOf(2500L),
            BigInteger.valueOf(6500L)
    };
    private static final BigInteger[] ENUM_TABLE_COLUMN_WIDTHS = new BigInteger[] {
            BigInteger.valueOf(3000L),
            BigInteger.valueOf(3500L),
            BigInteger.valueOf(7000L)
    };
    private static final BigInteger UNIQUE_TABLE_WIDTH = ATTRIBUTE_TABLE_WIDTH;
    private static final BigInteger[] UNIQUE_TABLE_COLUMN_WIDTHS = new BigInteger[] {
            BigInteger.valueOf(1000L),
            BigInteger.valueOf(10000L),
            BigInteger.valueOf(2500L)
    };

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
            appendModelMetadata(doc, model);

            renderViewables(doc, model, model, 1);
            renderEnumerations(doc, model, model, 1);

            for (Topic topic : getElements(model, Topic.class)) {
                writeHeading(doc, topicTitle(topic), 0);
                writeDocumentationParagraph(doc, topic.getDocumentation());
                renderViewables(doc, model, topic, 1);
                renderEnumerations(doc, model, topic, 1);
            }
        }
    }

    static void ensureAllStyles(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();

        if (!styles.styleExist("Title")) {
            styles.addStyle(buildParagraphStyle("Title", "Title", null, true, 18));
        } else {
            XWPFStyle style = styles.getStyle("Title");
            if (style != null) {
                CTRPr rpr = style.getCTStyle().isSetRPr() ? style.getCTStyle().getRPr() : style.getCTStyle().addNewRPr();
                BigInteger halfPts = BigInteger.valueOf(18L * 2L);
                CTHpsMeasure sz = rpr.sizeOfSzArray() > 0 ? rpr.getSzArray(0) : rpr.addNewSz();
                sz.setVal(halfPts);
                CTHpsMeasure szCs = rpr.sizeOfSzCsArray() > 0 ? rpr.getSzCsArray(0) : rpr.addNewSzCs();
                szCs.setVal(halfPts);
                if (rpr.sizeOfBArray() == 0) {
                    rpr.addNewB();
                }
                ensureArialFonts(rpr);
            }
        }

        if (!styles.styleExist("Heading1")) {
            styles.addStyle(buildParagraphStyle("Heading1", "Heading 1", BigInteger.ZERO, true, 11));
        } else {
            XWPFStyle style = styles.getStyle("Heading1");
            if (style != null) {
                CTRPr rpr = style.getCTStyle().isSetRPr() ? style.getCTStyle().getRPr() : style.getCTStyle().addNewRPr();
                ensureArialFonts(rpr);
                BigInteger halfPts = BigInteger.valueOf(22L);
                CTHpsMeasure sz = rpr.sizeOfSzArray() > 0 ? rpr.getSzArray(0) : rpr.addNewSz();
                sz.setVal(halfPts);
                CTHpsMeasure szCs = rpr.sizeOfSzCsArray() > 0 ? rpr.getSzCsArray(0) : rpr.addNewSzCs();
                szCs.setVal(halfPts);
            }
        }
        if (!styles.styleExist("Heading2")) {
            styles.addStyle(buildParagraphStyle("Heading2", "Heading 2", BigInteger.ONE, true, 11));
        } else {
            XWPFStyle style = styles.getStyle("Heading2");
            if (style != null) {
                CTRPr rpr = style.getCTStyle().isSetRPr() ? style.getCTStyle().getRPr() : style.getCTStyle().addNewRPr();
                ensureArialFonts(rpr);
                BigInteger halfPts = BigInteger.valueOf(22L);
                CTHpsMeasure sz = rpr.sizeOfSzArray() > 0 ? rpr.getSzArray(0) : rpr.addNewSz();
                sz.setVal(halfPts);
                CTHpsMeasure szCs = rpr.sizeOfSzCsArray() > 0 ? rpr.getSzCsArray(0) : rpr.addNewSzCs();
                szCs.setVal(halfPts);
            }
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
        ensureArialFonts(rpr);
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
        applyParagraphSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        applyRunFont(run);
        run.setText(text != null ? text : "");
    }

    private static void writeViewableHeading(XWPFDocument doc, Viewable viewable, int level) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle(level <= 0 ? "Heading1" : "Heading2");
        applyNumbering(paragraph, level <= 0 ? 0 : 1);
        applyParagraphSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        applyRunFont(run);
        run.setText(viewableTitle(viewable));
    }

    private static String tableStereotypes(Table table) {
        boolean structure = !table.isIdentifiable();
        boolean isAbstract = table.isAbstract();
        if (structure) {
            return isAbstract ? "(Abstract Structure)" : "(Structure)";
        }
        if (isAbstract) {
            return "(Abstract Class)";
        }
        return "(Class)";
    }

    public static String viewableTitle(Viewable viewable) {
        if (viewable == null) {
            return "";
        }
        if (viewable instanceof Table table) {
            String stereos = tableStereotypes(table);
            return stereos.isEmpty() ? table.getName() : table.getName() + " " + stereos;
        }
        if (viewable instanceof View) {
            return viewable.getName() + " (View)";
        }
        return viewable.getName();
    }

    public static String enumerationTitle(Domain domain) {
        if (domain == null) {
            return "";
        }
        String name = domain.getName();
        return (name != null && !name.isEmpty()) ? name + " (Enumeration)" : "(Enumeration)";
    }

    private static String topicTitle(Topic topic) {
        if (topic == null) {
            return "";
        }
        String name = topic.getName();
        return (name != null && !name.isEmpty()) ? name + " (Topic)" : "(Topic)";
    }

    private static void renderViewables(XWPFDocument doc, Model model, Container scope, int headingLevel) {
        for (Table table : getElements(scope, Table.class)) {
            writeViewableSection(doc, model, scope, table, headingLevel);
        }

        for (Viewable viewable : getElements(scope, Viewable.class)) {
            if (viewable instanceof Table) {
                continue;
            }
            if (viewable instanceof View) {
                writeViewableSection(doc, model, scope, viewable, headingLevel);
            }
        }
    }

    private static void renderEnumerations(XWPFDocument doc, Model model, Container scope, int headingLevel) {
        for (Domain domain : getElements(scope, Domain.class)) {
            Type type = domain.getType();
            if (!(type instanceof EnumerationType) && !(type instanceof EnumTreeValueType)) {
                continue;
            }
            AbstractEnumerationType enumType = (AbstractEnumerationType) type;
            writeHeading(doc, enumerationTitle(domain), headingLevel);
            writeDocumentationParagraph(doc, domain.getDocumentation());
            writeEnumerationTable(doc, enumType);
        }
    }

    private static void writeViewableSection(XWPFDocument doc, Model model, Container scope, Viewable viewable,
            int headingLevel) {
        writeViewableHeading(doc, viewable, headingLevel);
        writeDocumentationParagraph(doc, viewable.getDocumentation());
        writeAttributeTable(doc, collectRowsForViewable(model, scope, viewable));
        if (viewable instanceof AbstractClassDef<?> classDef) {
            writeUniquenessTable(doc, classDef);
        }
    }

    public static List<Row> collectRowsForViewable(Model model, Container scope, Viewable viewable) {
        List<Row> rows = new ArrayList<>();

        for (AttributeDef attribute : getElements(viewable, AttributeDef.class)) {
            String type = typeName(attribute);
            if ("ObjectType".equalsIgnoreCase(type)) {
                continue;
            }
            String description = docOf(attribute);
            Type domainType = attribute.getDomain();
            if (domainType instanceof EnumerationType enumType && isInlineEnumeration(attribute, enumType)) {
                description = inlineEnumerationValues(enumType);
            }
            rows.add(new Row(attribute.getName(), formatCardinality(attribute.getCardinality()), type,
                    valueRange(attribute), description));
        }

        if (viewable instanceof Table table) {
            for (AssociationDef assoc : collectAssociations(model, scope)) {
                List<RoleDef> roles = assoc.getRoles();
                if (roles == null || roles.size() != 2) {
                    continue;
                }
                RoleDef left = roles.get(0);
                RoleDef right = roles.get(1);
                addAssociationRow(rows, left, right, table);
                addAssociationRow(rows, right, left, table);
            }
        }

        return rows;
    }

    private static void addAssociationRow(List<Row> rows, RoleDef me, RoleDef other, Table cls) {
        AbstractClassDef destination = me.getDestination();
        AbstractClassDef otherDest = other.getDestination();
        if (destination instanceof Table && otherDest instanceof Table) {
            if (destination == cls) {
                rows.add(new Row(roleLabel(me), formatCardinality(me.getCardinality()),
                        ((Table) otherDest).getName(), "", ""));
            }
        }
    }

    private static void writeAttributeTable(XWPFDocument doc, List<Row> rows) {
        writeAttributeTable(doc, rows, "Attributname");
    }

    private static void writeAttributeTable(XWPFDocument doc, List<Row> rows, String nameHeader) {
        XWPFTable table = doc.createTable();
        CTTbl ctTable = table.getCTTbl();
        configureTable(ctTable, ATTRIBUTE_TABLE_WIDTH);

        final int cols = TABLE_COLUMN_WIDTHS.length;
        CTTblGrid grid = ctTable.addNewTblGrid();
        for (int i = 0; i < cols; i++) {
            grid.addNewGridCol().setW(TABLE_COLUMN_WIDTHS[i]);
        }

        XWPFTableRow header = table.getRow(0);
        if (header == null) {
            header = table.createRow();
        }
        ensureCellCount(header, cols);
        setCellText(header.getCell(0), nz(nameHeader), true, TABLE_COLUMN_WIDTHS[0]);
        setCellText(header.getCell(1), "Kardinalität", true, TABLE_COLUMN_WIDTHS[1]);
        setCellText(header.getCell(2), "Typ", true, TABLE_COLUMN_WIDTHS[2]);
        setCellText(header.getCell(3), "Beschreibung", true, TABLE_COLUMN_WIDTHS[3]);

        if (rows != null) {
            for (Row row : rows) {
                XWPFTableRow tr = table.createRow();
                ensureCellCount(tr, cols);
                setCellText(tr.getCell(0), nz(row.name), false, TABLE_COLUMN_WIDTHS[0]);
                setCellText(tr.getCell(1), nz(row.card), false, TABLE_COLUMN_WIDTHS[1]);
                setCellText(tr.getCell(2), displayType(row), false, TABLE_COLUMN_WIDTHS[2]);
                setCellText(tr.getCell(3), nz(row.descr), false, TABLE_COLUMN_WIDTHS[3]);
            }
        }

        XWPFParagraph spacer = doc.createParagraph();
        applyParagraphSpacing(spacer);
    }

    private static void writeUniquenessTable(XWPFDocument doc, AbstractClassDef<?> viewable) {
        List<UniqueEntry> entries = collectUniquenessEntries(viewable);
        if (entries.isEmpty()) {
            return;
        }

        XWPFParagraph label = doc.createParagraph();
        applyParagraphSpacing(label);
        XWPFRun labelRun = label.createRun();
        applyRunFont(labelRun);
        labelRun.setBold(true);
        labelRun.setText("UNIQUE");

        XWPFTable table = doc.createTable();
        CTTbl ctTable = table.getCTTbl();
        configureTable(ctTable, UNIQUE_TABLE_WIDTH);

        final int cols = UNIQUE_TABLE_COLUMN_WIDTHS.length;
        CTTblGrid grid = ctTable.addNewTblGrid();
        for (int i = 0; i < cols; i++) {
            grid.addNewGridCol().setW(UNIQUE_TABLE_COLUMN_WIDTHS[i]);
        }

        XWPFTableRow header = table.getRow(0);
        if (header == null) {
            header = table.createRow();
        }
        ensureCellCount(header, cols);
        setCellText(header.getCell(0), "Nr.", true, UNIQUE_TABLE_COLUMN_WIDTHS[0]);
        setCellText(header.getCell(1), "UNIQUE-Definition", true, UNIQUE_TABLE_COLUMN_WIDTHS[1]);
        setCellText(header.getCell(2), "Herkunft", true, UNIQUE_TABLE_COLUMN_WIDTHS[2]);

        for (UniqueEntry entry : entries) {
            XWPFTableRow row = table.createRow();
            ensureCellCount(row, cols);
            setCellText(row.getCell(0), nz(entry.number()), false, UNIQUE_TABLE_COLUMN_WIDTHS[0]);
            setCellText(row.getCell(1), nz(entry.definition()), false, UNIQUE_TABLE_COLUMN_WIDTHS[1]);
            setCellText(row.getCell(2), nz(entry.origin()), false, UNIQUE_TABLE_COLUMN_WIDTHS[2]);
        }

        XWPFParagraph spacer = doc.createParagraph();
        applyParagraphSpacing(spacer);
    }

    private static List<UniqueEntry> collectUniquenessEntries(AbstractClassDef<?> viewable) {
        List<UniqueEntry> entries = new ArrayList<>();
        if (viewable == null) {
            return entries;
        }

        List<AbstractClassDef<?>> inheritance = new ArrayList<>();
        Set<AbstractClassDef<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Element current = viewable;
        while (current instanceof AbstractClassDef<?> classDef && visited.add(classDef)) {
            inheritance.add(0, classDef);
            current = classDef.getExtending();
        }

        int number = 1;
        for (AbstractClassDef<?> source : inheritance) {
            String origin = source == viewable
                    ? "direkt"
                    : "geerbt von " + nz(source.getScopedName());
            for (UniquenessConstraint constraint : getElements(source, UniquenessConstraint.class)) {
                entries.add(new UniqueEntry("U" + number++, formatUniqueness(constraint), origin));
            }
        }
        return entries;
    }

    private static String formatUniqueness(UniquenessConstraint constraint) {
        if (constraint == null) {
            return "UNIQUE [Definition nicht darstellbar];";
        }

        String scope = constraint.getLocal() ? "LOCAL" : "GLOBAL";
        if (constraint.perBasket()) {
            scope += ", per Basket";
        }

        StringBuilder definition = new StringBuilder("UNIQUE (").append(scope).append(") ");
        ObjectPath prefix = constraint.getPrefix();
        if (prefix != null) {
            definition.append(formatObjectPath(prefix)).append(" : ");
        }

        UniqueEl elements = constraint.getElements();
        List<String> paths = new ArrayList<>();
        if (elements != null && elements.getAttributes() != null) {
            for (ObjectPath path : elements.getAttributes()) {
                if (path != null) {
                    paths.add(formatObjectPath(path));
                }
            }
        }
        definition.append(paths.isEmpty() ? "[keine Schlüsselattribute]" : String.join(", ", paths));

        Evaluable preCondition = constraint.getPreCondition();
        if (preCondition != null) {
            definition.append(" WHERE ").append(formatEvaluable(preCondition));
        }
        return definition.append(";").toString();
    }

    private static String formatObjectPath(ObjectPath path) {
        if (path == null) {
            return "[Pfad nicht darstellbar]";
        }
        String value = path.toString();
        return value == null || value.isBlank() ? "[Pfad nicht darstellbar]" : value;
    }

    private static String formatEvaluable(Evaluable evaluable) {
        if (evaluable == null) {
            return "[Bedingung nicht darstellbar]";
        }
        if (evaluable instanceof ObjectPath path) {
            return formatObjectPath(path);
        }
        if (evaluable instanceof FunctionCall functionCall) {
            String name = functionCall.getFunction() != null ? functionCall.getFunction().getName() : "FUNCTION";
            Evaluable[] arguments = functionCall.getArguments();
            List<String> formattedArguments = new ArrayList<>();
            if (arguments != null) {
                for (Evaluable argument : arguments) {
                    formattedArguments.add(formatEvaluable(argument));
                }
            }
            return nz(name) + "(" + String.join(", ", formattedArguments) + ")";
        }
        if (evaluable instanceof Expression.Equality expression) {
            return formatBinary(expression.getLeft(), " == ", expression.getRight());
        }
        if (evaluable instanceof Expression.Inequality expression) {
            return formatBinary(expression.getLeft(), " <> ", expression.getRight());
        }
        if (evaluable instanceof Expression.GreaterThan expression) {
            return formatBinary(expression.getLeft(), " > ", expression.getRight());
        }
        if (evaluable instanceof Expression.GreaterThanOrEqual expression) {
            return formatBinary(expression.getLeft(), " >= ", expression.getRight());
        }
        if (evaluable instanceof Expression.LessThan expression) {
            return formatBinary(expression.getLeft(), " < ", expression.getRight());
        }
        if (evaluable instanceof Expression.LessThanOrEqual expression) {
            return formatBinary(expression.getLeft(), " <= ", expression.getRight());
        }
        if (evaluable instanceof Expression.Addition expression) {
            return formatBinary(expression.getLeft(), " + ", expression.getRight());
        }
        if (evaluable instanceof Expression.Subtraction expression) {
            return formatBinary(expression.getLeft(), " - ", expression.getRight());
        }
        if (evaluable instanceof Expression.Multiplication expression) {
            return formatBinary(expression.getLeft(), " * ", expression.getRight());
        }
        if (evaluable instanceof Expression.Division expression) {
            return formatBinary(expression.getLeft(), " / ", expression.getRight());
        }
        if (evaluable instanceof Expression.Implication expression) {
            return formatBinary(expression.getLeft(), " => ", expression.getRight());
        }
        if (evaluable instanceof Expression.Conjunction expression) {
            return joinEvaluables(expression.getConjoined(), " AND ");
        }
        if (evaluable instanceof Expression.Disjunction expression) {
            return joinEvaluables(expression.getDisjoined(), " OR ");
        }
        if (evaluable instanceof Expression.DefinedCheck expression) {
            return "DEFINED(" + formatEvaluable(expression.getArgument()) + ")";
        }
        if (evaluable instanceof Expression.Negation expression) {
            return "NOT (" + formatEvaluable(expression.getNegated()) + ")";
        }
        if (evaluable instanceof Expression.Subexpression expression) {
            return "(" + formatEvaluable(expression.getSubexpression()) + ")";
        }
        if (evaluable instanceof Constant.EnumerationRange range) {
            String[] prefix = range.getCommonPrefix();
            String prefixText = prefix == null ? "" : String.join(".", prefix);
            String separator = prefixText.isEmpty() ? "" : ".";
            return prefixText + separator + nz(range.getFrom()) + ".." + nz(range.getTo());
        }
        if (evaluable instanceof Constant.AttributePath
                || evaluable instanceof Constant.Class
                || evaluable instanceof Constant.Enumeration
                || evaluable instanceof Constant.Numeric
                || evaluable instanceof Constant.Text
                || evaluable instanceof Constant.Undefined) {
            return evaluable.toString();
        }
        return "[Bedingung nicht darstellbar]";
    }

    private static String formatBinary(Evaluable left, String operator, Evaluable right) {
        return formatEvaluable(left) + operator + formatEvaluable(right);
    }

    private static String joinEvaluables(Evaluable[] evaluables, String separator) {
        List<String> values = new ArrayList<>();
        if (evaluables != null) {
            for (Evaluable evaluable : evaluables) {
                values.add(formatEvaluable(evaluable));
            }
        }
        return String.join(separator, values);
    }

    private static void writeEnumerationTable(XWPFDocument doc, AbstractEnumerationType enumType) {
        XWPFTable table = doc.createTable();
        CTTbl ctTable = table.getCTTbl();
        configureTable(ctTable, ENUM_TABLE_WIDTH);

        final int cols = ENUM_TABLE_COLUMN_WIDTHS.length;
        CTTblGrid grid = ctTable.addNewTblGrid();
        for (int i = 0; i < cols; i++) {
            grid.addNewGridCol().setW(ENUM_TABLE_COLUMN_WIDTHS[i]);
        }

        XWPFTableRow header = table.getRow(0);
        if (header == null) {
            header = table.createRow();
        }
        ensureCellCount(header, cols);
        setCellText(header.getCell(0), "Wert", true, ENUM_TABLE_COLUMN_WIDTHS[0]);
        setCellText(header.getCell(1), "Anzeigename", true, ENUM_TABLE_COLUMN_WIDTHS[1]);
        setCellText(header.getCell(2), "Beschreibung", true, ENUM_TABLE_COLUMN_WIDTHS[2]);

        List<DetailedEnumEntry> entries = collectDetailedEnumerationEntries(enumType);
        for (DetailedEnumEntry entry : entries) {
            XWPFTableRow tr = table.createRow();
            ensureCellCount(tr, cols);
            setCellText(tr.getCell(0), nz(entry.value()), false, ENUM_TABLE_COLUMN_WIDTHS[0]);
            setCellText(tr.getCell(1), nz(entry.displayName()), false, ENUM_TABLE_COLUMN_WIDTHS[1]);
            setCellText(tr.getCell(2), nz(entry.documentation()), false, ENUM_TABLE_COLUMN_WIDTHS[2]);
        }

        XWPFParagraph spacer = doc.createParagraph();
        applyParagraphSpacing(spacer);
    }

    public static List<EnumEntry> collectEnumerationEntries(AbstractEnumerationType enumType) {
        List<EnumEntry> entries = new ArrayList<>();
        for (DetailedEnumEntry entry : collectDetailedEnumerationEntries(enumType)) {
            entries.add(new EnumEntry(entry.value(), entry.documentation()));
        }
        return entries;
    }

    private static List<DetailedEnumEntry> collectDetailedEnumerationEntries(AbstractEnumerationType enumType) {
        List<DetailedEnumEntry> entries = new ArrayList<>();
        if (enumType == null) {
            return entries;
        }
        Enumeration enumeration = enumType.getConsolidatedEnumeration();
        if (enumeration != null) {
            boolean includeIntermediateValues = enumType instanceof EnumTreeValueType;
            appendEnumerationEntries(entries, "", enumeration, includeIntermediateValues);
        }
        return entries;
    }

    private static void appendEnumerationEntries(List<DetailedEnumEntry> target, String prefix, Enumeration enumeration,
            boolean includeIntermediateValues) {
        if (enumeration == null) {
            return;
        }
        for (Iterator<Enumeration.Element> it = enumeration.getElements(); it != null && it.hasNext();) {
            Enumeration.Element element = it.next();
            if (element == null) {
                continue;
            }
            String name = element.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String value = prefix.isEmpty() ? name : prefix + "." + name;
            Enumeration sub = element.getSubEnumeration();
            boolean hasSubElements = sub != null && sub.size() > 0;
            if (!hasSubElements || includeIntermediateValues) {
                target.add(new DetailedEnumEntry(value, enumDisplayName(element),
                        nz(element.getDocumentation())));
            }
            if (hasSubElements) {
                appendEnumerationEntries(target, value, sub, includeIntermediateValues);
            }
        }
    }

    private static String enumDisplayName(Enumeration.Element element) {
        if (element == null || element.getMetaValues() == null) {
            return "";
        }
        return nz(element.getMetaValues().getValue("ili2db.dispName"));
    }

    private static boolean isInlineEnumeration(AttributeDef attribute, EnumerationType enumType) {
        if (attribute == null || enumType == null) {
            return false;
        }
        Element container = enumType.getContainer();
        return !(container instanceof Domain);
    }

    private static String inlineEnumerationValues(EnumerationType enumType) {
        List<DetailedEnumEntry> entries = collectDetailedEnumerationEntries(enumType);
        List<String> values = new ArrayList<>();
        for (DetailedEnumEntry entry : entries) {
            if (entry.value() != null && !entry.value().isEmpty()) {
                String displayName = nz(entry.displayName()).trim();
                values.add(displayName.isEmpty()
                        ? entry.value()
                        : entry.value() + " (" + displayName + ")");
            }
        }
        return String.join(", ", values);
    }

    private static void ensureCellCount(XWPFTableRow row, int cols) {
        while (row.getTableCells().size() < cols) {
            row.addNewTableCell();
        }
    }

    private static void setCellText(XWPFTableCell cell, String text, boolean bold, BigInteger width) {
        if (cell == null) {
            return;
        }
        if (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
        if (width != null) {
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                tcPr = cell.getCTTc().addNewTcPr();
            }
            CTTblWidth cellWidth = tcPr.getTcW();
            if (cellWidth == null) {
                cellWidth = tcPr.addNewTcW();
            }
            cellWidth.setW(width);
            cellWidth.setType(STTblWidth.DXA);
        }
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        applyRunFont(run);
        run.setBold(bold);
        run.setText(text != null ? text : "");
    }

    private static void configureTable(CTTbl ctTable, BigInteger tableWidth) {
        if (ctTable == null) {
            return;
        }
        CTTblPr tblPr = ctTable.getTblPr();
        if (tblPr == null) {
            tblPr = ctTable.addNewTblPr();
        }
        CTTblWidth width = tblPr.getTblW();
        if (width == null) {
            width = tblPr.addNewTblW();
        }
        width.setW(tableWidth);
        width.setType(STTblWidth.DXA);

        CTTblBorders borders = tblPr.getTblBorders();
        if (borders == null) {
            borders = tblPr.addNewTblBorders();
        }
        configureBorder(borders.getTop() != null ? borders.getTop() : borders.addNewTop());
        configureBorder(borders.getBottom() != null ? borders.getBottom() : borders.addNewBottom());
        configureBorder(borders.getLeft() != null ? borders.getLeft() : borders.addNewLeft());
        configureBorder(borders.getRight() != null ? borders.getRight() : borders.addNewRight());
        configureBorder(borders.getInsideH() != null ? borders.getInsideH() : borders.addNewInsideH());
        configureBorder(borders.getInsideV() != null ? borders.getInsideV() : borders.addNewInsideV());
    }

    private static void configureBorder(CTBorder border) {
        if (border == null) {
            return;
        }
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4L));
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
        if (type instanceof EnumerationType enumType) {
            if (attribute.isDomainBoolean()) {
                return "Boolean";
            }
            Element container = enumType.getContainer();
            if (container instanceof Domain domain) {
                return domain.getName();
            }
            return "Enumeration";
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

    private static String valueRange(AttributeDef attribute) {
        if (attribute == null || attribute.getDomain() == null) {
            return "";
        }
        Type type = Type.findReal(attribute.getDomain());
        if (type instanceof TextType || type instanceof NumericType) {
            return type.toString();
        }
        return "";
    }

    private static String displayType(Row row) {
        if (row == null) {
            return "";
        }
        String range = nz(row.range());
        return range.isBlank() ? nz(row.type()) : range;
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

    private static void appendModelMetadata(XWPFDocument doc, Model model) {
        if (model == null) {
            return;
        }
        String title = nz(model.getMetaValue("title"));
        if (!title.isBlank()) {
            writeMetadataParagraph(doc, "Titel: " + title);
        }
        String shortDescr = nz(model.getMetaValue("shortDescription"));
        if (!shortDescr.isBlank()) {
            writeMetadataParagraph(doc, "Beschreibung: " + shortDescr);
        }
    }

    private static void writeMetadataParagraph(XWPFDocument doc, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        XWPFParagraph paragraph = doc.createParagraph();
        applyParagraphSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        applyRunFont(run);
        run.setText(text);
    }

    private static void writeDocumentationParagraph(XWPFDocument doc, String documentation) {
        if (documentation == null || documentation.trim().isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = doc.createParagraph();
        applyParagraphSpacing(paragraph);
        String[] lines = documentation.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            applyRunFont(run);
            run.setText(lines[i]);
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private static void applyRunFont(XWPFRun run) {
        run.setFontFamily(FONT_FAMILY);
    }

    private static void ensureArialFonts(CTRPr rpr) {
        if (rpr == null) {
            return;
        }
        if (rpr.sizeOfRFontsArray() == 0) {
            rpr.addNewRFonts();
        }
        rpr.getRFontsArray(0).setAscii(FONT_FAMILY);
        rpr.getRFontsArray(0).setHAnsi(FONT_FAMILY);
        rpr.getRFontsArray(0).setCs(FONT_FAMILY);
    }

    public static <T extends Element> List<T> getElements(Container container, Class<T> type) {
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

    public static <T extends Element> List<T> sortByName(T[] arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>(Arrays.asList(arr));
        list.sort(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    public static String nz(String value) {
        return value == null ? "" : value;
    }

    private static void applyParagraphSpacing(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return;
        }
        CTP ctP = paragraph.getCTP();
        CTPPr ppr = ctP.isSetPPr() ? ctP.getPPr() : ctP.addNewPPr();
        CTSpacing spacing = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        spacing.setAfter(DEFAULT_SPACING_AFTER);
    }

    private static record DetailedEnumEntry(String value, String displayName, String documentation) {}

    private static record UniqueEntry(String number, String definition, String origin) {}

    public static record EnumEntry(String value, String documentation) {}

    public static record Row(String name, String card, String type, String range, String descr) {
        public Row(String name, String card, String type, String descr) {
            this(name, card, type, "", descr);
        }
    }
}
