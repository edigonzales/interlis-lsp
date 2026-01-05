package ch.so.agi.lsp.interlis.compiler;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticsMapper {
    private static final int WHOLE_LINE_FALLBACK_END_COL = 10_000;

    public static List<Diagnostic> toDiagnostics(List<Ili2cUtil.Message> messages) {
        List<Diagnostic> list = new ArrayList<>();
        if (messages == null) return list;

        for (Ili2cUtil.Message m : messages) {
            Diagnostic d = new Diagnostic();
            d.setMessage(m.getText());
            d.setSeverity(switch (m.getSeverity()) {
                case ERROR -> DiagnosticSeverity.Error;
                case WARNING -> DiagnosticSeverity.Warning;
                case INFO -> DiagnosticSeverity.Information;
            });

            int line0 = Math.max(0, m.getLine() - 1);
            int col0  = Math.max(0, m.getColumn() - 1);

            // If column is unknown (<=1), highlight the whole line
            int endCol = (m.getColumn() <= 1) ? WHOLE_LINE_FALLBACK_END_COL : Math.max(col0 + 1, col0);

            d.setRange(new Range(new Position(line0, (m.getColumn() <= 1) ? 0 : col0),
                                 new Position(line0, endCol)));
            list.add(d);
        }
        return list;
    }
}
