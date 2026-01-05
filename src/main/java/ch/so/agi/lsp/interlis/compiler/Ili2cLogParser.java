package ch.so.agi.lsp.interlis.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses ili2c log text and extracts structured error messages. */
public final class Ili2cLogParser {
    private Ili2cLogParser() {}

    // Matches: Error: <path>:<line>:<message>
    // Works with Windows drives (C:\...) because group(1) is greedy and the next token forces :<digits>:
    private static final Pattern ERROR_LINE =
            Pattern.compile("^Error:\\s+(.+):(\\d+):(.*)$");

    /**
     * Parse ili2c log text into structured messages.
     * Only lines starting with "Error:" are considered, except summary lines containing "...compiler run failed".
     */
    public static List<Ili2cUtil.Message> parseErrors(String logText) {
        List<Ili2cUtil.Message> out = new ArrayList<>();
        if (logText == null || logText.isBlank()) return out;

        String[] lines = logText.split("\\R");
        for (String raw : lines) {
            String line = raw.strip();
            if (!line.startsWith("Error:")) continue;
            if (line.contains("...compiler run failed")) continue;

            Matcher m = ERROR_LINE.matcher(line);
            if (!m.matches()) {
                // Not in the canonical "<path>:<line>:<msg>" shape; keep as file-unknown, line=1
                out.add(new Ili2cUtil.Message(
                        Ili2cUtil.Message.Severity.ERROR,
                        /*file*/ null,
                        /*line*/ 1,
                        /*column*/ 1,
                        line));
                continue;
            }

            String path = m.group(1).trim();
            int lineNo;
            try {
                lineNo = Integer.parseInt(m.group(2));
            } catch (NumberFormatException nfe) {
                lineNo = 1;
            }
            String msg = m.group(3).trim();

            out.add(new Ili2cUtil.Message(
                    Ili2cUtil.Message.Severity.ERROR,
                    path,
                    lineNo,
                    /* column unknown */ 1,
                    msg.isEmpty() ? "Error" : msg));
        }
        return out;
    }
}
