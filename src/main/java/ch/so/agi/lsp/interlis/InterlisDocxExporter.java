package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.TransferDescription;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.xmlbeans.XmlBeans;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPrDefault;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

/**
 * Utility for producing DOCX files containing an INTERLIS model summary.
 */
public final class InterlisDocxExporter {
    private static final BigInteger A4_WIDTH_TWIPS = BigInteger.valueOf(11906);
    private static final BigInteger A4_HEIGHT_TWIPS = BigInteger.valueOf(16838);

    private InterlisDocxExporter() {}

    public static byte[] renderDocx(TransferDescription td, String title) throws IOException {
        Objects.requireNonNull(td, "TransferDescription must not be null");

        try (XWPFDocument doc = new XWPFDocument()) {
            removeLeadingEmptyParagraphs(doc);
            configurePage(doc);
            configureDefaults(doc);

            if (title != null && !title.isBlank()) {
                XWPFParagraph titleParagraph = doc.createParagraph();
                titleParagraph.setStyle("Title");
                titleParagraph.createRun().setText(title);
            }

            IliDocxRenderer.renderTransferDescription(doc, td);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                doc.write(out);
                return out.toByteArray();
            }
        }
    }

    private static void removeLeadingEmptyParagraphs(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        while (!paragraphs.isEmpty()) {
            XWPFParagraph first = paragraphs.get(0);
            if (first == null) {
                break;
            }
            String text = first.getText();
            if (text != null && !text.isBlank()) {
                break;
            }
            int pos = doc.getPosOfParagraph(first);
            if (pos < 0) {
                break;
            }
            doc.removeBodyElement(pos);
            paragraphs = doc.getParagraphs();
        }
    }

    private static void configurePage(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(A4_WIDTH_TWIPS);
        pageSz.setH(A4_HEIGHT_TWIPS);
        pageSz.setOrient(STPageOrientation.PORTRAIT);
    }

    private static void configureDefaults(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();
        CTStyles ctStyles = (CTStyles) XmlBeans.getContextTypeLoader().newInstance(CTStyles.type, null);
        CTDocDefaults docDefaults = ctStyles.addNewDocDefaults();
        CTRPrDefault runDefaults = docDefaults.addNewRPrDefault();
        CTRPr rpr = runDefaults.addNewRPr();
        CTFonts fonts = rpr.addNewRFonts();
        fonts.setAscii("Arial");
        fonts.setHAnsi("Arial");
        fonts.setCs("Arial");
        CTHpsMeasure size = rpr.addNewSz();
        size.setVal(BigInteger.valueOf(22));
        CTHpsMeasure sizeCs = rpr.addNewSzCs();
        sizeCs.setVal(BigInteger.valueOf(22));
        styles.setStyles(ctStyles);

        IliDocxRenderer.ensureAllStyles(doc);
    }
}
