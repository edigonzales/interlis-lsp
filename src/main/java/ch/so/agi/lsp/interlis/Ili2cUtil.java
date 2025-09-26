package ch.so.agi.lsp.interlis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.StdListener;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox_j.logging.FileLogger;

/**
 * Wraps calls into the ili2c compiler. Replace placeholders with real ili2c API calls.
 */
public class Ili2cUtil {
    private static final Logger LOG = LoggerFactory.getLogger(Ili2cUtil.class);

    private final ClientSettings settings;

    public Ili2cUtil(ClientSettings settings) {
        this.settings = settings != null ? settings : new ClientSettings();
    }
    
    public static class Message {
        public enum Severity { ERROR, WARNING, INFO }
        private final Severity severity;
        private final String fileUriOrPath;
        private final int line;   // 1-based
        private final int column; // 1-based
        private final String text;

        public Message(Severity severity, String fileUriOrPath, int line, int column, String text) {
            this.severity = severity;
            this.fileUriOrPath = fileUriOrPath;
            this.line = line;
            this.column = column;
            this.text = text;
        }
        public Severity getSeverity() { return severity; }
        public String getFileUriOrPath() { return fileUriOrPath; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getText() { return text; }
    }

    public static class CompilationOutcome {
        private final String logText;
        private final List<Message> messages;
        
        public CompilationOutcome(String logText, List<Message> messages) {
            this.logText = logText;
            this.messages = messages;
        }

        public String getLogText() {
            return logText;
        }

        public List<Message> getMessages() {
            return messages;
        }
    }

    /** Validate an .ili file by calling ili2c's Java API (no external process!). */
    public CompilationOutcome compile(String fileUriOrPath) {
        StringBuilder log = new StringBuilder();
        List<Message> messages = new ArrayList<>();
        
        LOG.info("fileUriOrPath: " + fileUriOrPath);
        LOG.info("settings.getModelRepositories(): " + settings.getModelRepositories());
               
        Path logFile = null;
        FileLogger flog = null;
        String logText = "";
        try {
            logFile = Files.createTempFile("ili2c_", ".log");
            flog = new FileLogger(logFile.toFile(), false);
            
            LOG.info("logFile: " + logFile.toString());

            
            StdListener.getInstance().skipInfo(true);
            EhiLogger.getInstance().addListener(flog);
            EhiLogger.getInstance().removeListener(StdListener.getInstance());
            
            EhiLogger.logState("ili2c-" + TransferDescription.getVersion());
 
            Ili2cSettings set = new Ili2cSettings();
            ch.interlis.ili2c.Main.setDefaultIli2cPathMap(set);
            String repos = settings.getModelRepositories();
            if (repos != null && !repos.isBlank()) {
                set.setIlidirs(repos);
            } else {
                set.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);
            }

            Configuration cfg = new Configuration();
            cfg.addFileEntry(new FileEntry(fileUriOrPath, FileEntryKind.ILIMODELFILE));
            cfg.setAutoCompleteModelList(true);
            cfg.setGenerateWarnings(true);
            
            DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date today = new Date();
            String dateOut = dateFormatter.format(today);

            TransferDescription td = ch.interlis.ili2c.Main.runCompiler(cfg, set, null);
            
            if (td == null) {
                EhiLogger.logError("...compiler run failed " + dateOut);
            } else {
                EhiLogger.logState("...compiler run done " + dateOut);
            }
        } catch (Exception e) {
            return new CompilationOutcome("[ili2c] failed: " + e.getMessage(), messages);
        } finally {
            EhiLogger.getInstance().addListener(StdListener.getInstance());

            if (flog != null) {
                try { flog.close(); } catch (Exception ignore) {}
                EhiLogger.getInstance().removeListener(flog);
            }
        }

        try {
            logText = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "[ili2c] log file not found: " + logFile;
        } catch (IOException io) {
            logText = "[ili2c] failed to read log file: " + io.getMessage();
        } finally {
            // TODO
            // try { Files.deleteIfExists(logFile); } catch (IOException ignore) {}
        }

        messages = Ili2cLogParser.parseErrors(logText);
//        log.append("ERROR: ").append(fileUriOrPath).append(":5:10 Unexpected token 'MODEL'").append('\n');
//        messages.add(new Message(Message.Severity.ERROR, fileUriOrPath, 5, 10, "Unexpected token 'MODEL'"));
        // --- END PLACEHOLDER ---

        return new CompilationOutcome(logText, messages);
    }
}
