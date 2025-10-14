package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.AbstractEnumerationType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.EnumTreeValueType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import java.util.List;
import java.util.Objects;

/**
 * Renders INTERLIS model metadata into a standalone HTML document.
 */
final class IliHtmlRenderer {
    private static final String STYLE = """
:root {
  color-scheme: light dark;
}
body {
  margin: 0;
  padding: 0;
  font-family: Arial, sans-serif;
  background: #ffffff;
  color: #1b1b1b;
  line-height: 1.5;
}
@media (prefers-color-scheme: dark) {
  body {
    background: #ffffff;
    color: #1b1b1b;
  }
  table {
    border-color: #999999;
    background-color: rgba(255, 255, 255, 0.96);
  }
  th {
    background-color: #ffffff;
  }
  th, td {
    border-color: #999999;
  }
}
.container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}
.doc-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 24px;
}
.heading {
  font-size: 20px;
  font-weight: 600;
  margin: 24px 0 12px;
  display: flex;
  gap: 12px;
  align-items: baseline;
}
.heading.level-2 {
  font-size: 17px;
}
.heading-number {
  font-weight: 600;
  min-width: 60px;
}
.heading-text {
  flex: 1 1 auto;
}
.metadata {
  margin: 4px 0;
  font-weight: 500;
}
.documentation {
  margin: 8px 0 16px;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin: 0 0 24px;
  border: 1px solid #999999;
  background: rgba(255, 255, 255, 0.96);
}
th, td {
  border: 1px solid #999999;
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
  font-size: 14px;
}
th {
  font-weight: 600;
  background-color: #f1f1f1;
}
.annotation {
  color: #6b6b6b;
  font-size: 13px;
}
""";

    private IliHtmlRenderer() {}

    static String render(TransferDescription td, String documentTitle) {
        Objects.requireNonNull(td, "TransferDescription is null");

        String titleText = (documentTitle == null || documentTitle.isBlank()) ? "INTERLIS" : documentTitle;

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\"/>\n");
        sb.append("<title>").append(escape(titleText)).append("</title>\n");
        sb.append("<style>").append(STYLE).append("</style>\n");
        sb.append("</head>\n<body>\n<div class=\"container\">\n");

        if (documentTitle != null && !documentTitle.isBlank()) {
            sb.append("<h1 class=\"doc-title\">").append(escape(documentTitle)).append("</h1>\n");
        }

        Numbering numbering = new Numbering();
        Model[] models = td.getModelsFromLastFile();
        if (models != null) {
            for (Model model : IliDocxRenderer.sortByName(models)) {
                appendHeading(sb, numbering, 0, model.getName());
                appendModelMetadata(sb, model);

                renderViewables(sb, numbering, model, model, 1);
                renderEnumerations(sb, numbering, model, model, 1);

                for (Topic topic : IliDocxRenderer.getElements(model, Topic.class)) {
                    appendHeading(sb, numbering, 0, topic.getName());
                    appendDocumentation(sb, topic.getDocumentation());
                    renderViewables(sb, numbering, model, topic, 1);
                    renderEnumerations(sb, numbering, model, topic, 1);
                }
            }
        }

        sb.append("</div>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static void renderViewables(StringBuilder sb, Numbering numbering, Model model, Container scope, int headingLevel) {
        for (Table table : IliDocxRenderer.getElements(scope, Table.class)) {
            writeViewableSection(sb, numbering, model, scope, table, headingLevel);
        }

        for (Viewable viewable : IliDocxRenderer.getElements(scope, Viewable.class)) {
            if (viewable instanceof Table) {
                continue;
            }
            if (viewable instanceof View) {
                writeViewableSection(sb, numbering, model, scope, viewable, headingLevel);
            }
        }
    }

    private static void renderEnumerations(StringBuilder sb, Numbering numbering, Model model, Container scope, int headingLevel) {
        for (Domain domain : IliDocxRenderer.getElements(scope, Domain.class)) {
            if (!(domain.getType() instanceof EnumerationType) && !(domain.getType() instanceof EnumTreeValueType)) {
                continue;
            }
            AbstractEnumerationType enumType = (AbstractEnumerationType) domain.getType();
            appendHeading(sb, numbering, headingLevel, IliDocxRenderer.enumerationTitle(domain));
            appendDocumentation(sb, domain.getDocumentation());
            appendEnumerationTable(sb, IliDocxRenderer.collectEnumerationEntries(enumType));
        }
    }

    private static void writeViewableSection(StringBuilder sb, Numbering numbering, Model model, Container scope,
            Viewable viewable, int headingLevel) {
        appendHeading(sb, numbering, headingLevel, IliDocxRenderer.viewableTitle(viewable));
        appendDocumentation(sb, viewable.getDocumentation());
        appendAttributeTable(sb, IliDocxRenderer.collectRowsForViewable(model, scope, viewable));
    }

    private static void appendModelMetadata(StringBuilder sb, Model model) {
        if (model == null) {
            return;
        }
        String title = IliDocxRenderer.nz(model.getMetaValue("title"));
        if (!title.isBlank()) {
            sb.append("<p class=\"metadata\"><strong>Titel:</strong> ").append(escape(title)).append("</p>\n");
        }
        String shortDescr = IliDocxRenderer.nz(model.getMetaValue("shortDescription"));
        if (!shortDescr.isBlank()) {
            sb.append("<p class=\"metadata\"><strong>Beschreibung:</strong> ")
                    .append(escape(shortDescr)).append("</p>\n");
        }
    }

    private static void appendDocumentation(StringBuilder sb, String documentation) {
        if (documentation == null || documentation.trim().isEmpty()) {
            return;
        }
        sb.append("<p class=\"documentation\">");
        String[] lines = documentation.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            sb.append(escape(lines[i]));
            if (i < lines.length - 1) {
                sb.append("<br/>");
            }
        }
        sb.append("</p>\n");
    }

    private static void appendAttributeTable(StringBuilder sb, List<IliDocxRenderer.Row> rows) {
        sb.append("<table class=\"attributes\">\n");
        sb.append("<colgroup><col style=\"width:25%\"/><col style=\"width:17%\"/><col style=\"width:25%\"/><col style=\"width:33%\"/></colgroup>\n");
        sb.append("<thead><tr><th>Attributname</th><th>Kardinalität</th><th>Typ</th><th>Beschreibung</th></tr></thead>\n<tbody>\n");
        if (rows != null) {
            for (IliDocxRenderer.Row row : rows) {
                sb.append("<tr><td>").append(escape(IliDocxRenderer.nz(row.name()))).append("</td>");
                sb.append("<td>").append(escape(IliDocxRenderer.nz(row.card()))).append("</td>");
                sb.append("<td>").append(escape(IliDocxRenderer.nz(row.type()))).append("</td>");
                sb.append("<td>").append(escape(IliDocxRenderer.nz(row.descr()))).append("</td></tr>\n");
            }
        }
        sb.append("</tbody>\n</table>\n");
    }

    private static void appendEnumerationTable(StringBuilder sb, List<IliDocxRenderer.EnumEntry> entries) {
        sb.append("<table class=\"enumeration\">\n");
        sb.append("<colgroup><col style=\"width:35%\"/><col style=\"width:65%\"/></colgroup>\n");
        sb.append("<thead><tr><th>Wert</th><th>Beschreibung</th></tr></thead>\n<tbody>\n");
        for (IliDocxRenderer.EnumEntry entry : entries) {
            sb.append("<tr><td>").append(escape(IliDocxRenderer.nz(entry.value()))).append("</td>");
            sb.append("<td>").append(escape(IliDocxRenderer.nz(entry.documentation()))).append("</td></tr>\n");
        }
        sb.append("</tbody>\n</table>\n");
    }

    private static void appendHeading(StringBuilder sb, Numbering numbering, int headingLevel, String text) {
        String headingText = text != null ? text : "";
        String number = numbering.next(headingLevel);
        String tag = headingLevel <= 0 ? "h2" : "h3";
        sb.append('<').append(tag).append(" class=\"heading level-")
                .append(headingLevel <= 0 ? "1" : "2").append("\">");
        if (!number.isEmpty()) {
            sb.append("<span class=\"heading-number\">").append(escape(number)).append("</span>");
        }
        sb.append("<span class=\"heading-text\">").append(escape(headingText)).append("</span>");
        sb.append("</").append(tag).append(">\n");
    }

    private static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static final class Numbering {
        private int level0 = 0;
        private int level1 = 0;

        String next(int headingLevel) {
            if (headingLevel <= 0) {
                level0++;
                level1 = 0;
                return Integer.toString(level0);
            }
            if (level0 <= 0) {
                level0 = 1;
            }
            level1++;
            return level0 + "." + level1;
        }
    }
}
