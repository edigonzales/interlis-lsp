package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.TransferDescription;
import java.util.Objects;

/**
 * Utility for producing HTML documents containing an INTERLIS model summary.
 */
public final class InterlisHtmlExporter {
    private InterlisHtmlExporter() {}

    public static String renderHtml(TransferDescription td, String title) {
        Objects.requireNonNull(td, "TransferDescription must not be null");
        return IliHtmlRenderer.render(td, title);
    }
}
