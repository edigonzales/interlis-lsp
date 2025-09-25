package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticsMapper {
    public static List<Diagnostic> toDiagnostics(List<InterlisValidator.Message> messages) {
        List<Diagnostic> list = new ArrayList<>();
        if (messages == null) return list;
        for (InterlisValidator.Message m : messages) {
            Diagnostic d = new Diagnostic();
            d.setMessage(m.getText());
            d.setSeverity(switch (m.getSeverity()) {
                case ERROR -> DiagnosticSeverity.Error;
                case WARNING -> DiagnosticSeverity.Warning;
                case INFO -> DiagnosticSeverity.Information;
            });
            int startLine = Math.max(0, m.getLine() - 1);
            int startCol  = Math.max(0, m.getColumn() - 1);
            Range r = new Range(new Position(startLine, startCol),
                                new Position(startLine, Math.max(startCol, startCol + 1)));
            d.setRange(r);
            list.add(d);
        }
        return list;
    }
}
