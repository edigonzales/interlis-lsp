package ch.so.agi.lsp.interlis;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.StdListener;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.generator.Interlis2Generator;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox_j.logging.FileLogger;

/**
 * Wraps calls into the ili2c compiler. Replace placeholders with real ili2c API calls.
 */
public class Ili2cUtil {
    private static final Logger LOG = LoggerFactory.getLogger(Ili2cUtil.class);

    private static final ReentrantLock COMPILE_LOCK = new ReentrantLock();

    private static volatile CompilationMonitor compilationMonitor;

    interface CompilationMonitor {
        void onStart(Thread thread);

        void onFinish(Thread thread);
    }

    static void setCompilationMonitor(CompilationMonitor monitor) {
        compilationMonitor = monitor;
    }

//    private final ClientSettings settings;

//    public Ili2cUtil(ClientSettings settings) {
//        this.settings = settings != null ? settings : new ClientSettings();
//    }
    
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
        private final TransferDescription td;
        private final String logText;
        private final List<Message> messages;

        public CompilationOutcome(TransferDescription td, String logText, List<Message> messages) {
            this.td = td;
            this.logText = logText;
            this.messages = messages;
        }
        
        public TransferDescription getTransferDescription() {
            return td;
        }

        public String getLogText() {
            return logText;
        }

        public List<Message> getMessages() {
            return messages;
        }
    }

    /**
     * Pretty print the provided INTERLIS source using ili2c's formatter.
     * <p>
     * In the real implementation this would delegate to the ili2c library. For
     * testing purposes we normalise leading/trailing whitespace and ensure the
     * document ends with a newline.
     *
     * @param source raw INTERLIS source code
     * @return formatted INTERLIS source code
     * @throws IOException 
     */
    public static String prettyPrint(ClientSettings settings, String sourceFile) throws IOException {
        settings = settings != null ? settings : new ClientSettings();

        if (sourceFile == null) {
            return "";
        }
        
        CompilationOutcome outcome = compile(settings, sourceFile);
        TransferDescription td = outcome != null ? outcome.getTransferDescription() : null;
        return prettyPrint(td, sourceFile);
    }

    public static String prettyPrint(TransferDescription td, String sourceFile) throws IOException {
        if (sourceFile == null) {
            return "";
        }
        
        Path targetFile = Files.createTempFile("", ".ili");
        if (td == null) {
            return null;
        }
        
        TransferDescription desc = new TransferDescription();
        // TODO Do we want to use a cache again?
        try (OutputStreamWriter target = new OutputStreamWriter(
                new FileOutputStream(targetFile.toAbsolutePath().toString()), StandardCharsets.UTF_8)) {

            for (Model model : td.getModelsFromLastFile()) {
                desc.add(model);
            }
            
            Interlis2Generator gen = new Interlis2Generator();
            gen.generate(target, desc, false); // emitPredefined = config.isIncPredefModel() ?
        }
        
        String formattedBody = Files.readString(targetFile);            
        return formattedBody;
    }

    /** Validate an .ili file by calling ili2c's Java API (no external process!). */
    public static CompilationOutcome compile(ClientSettings settings, String fileUriOrPath) {
        COMPILE_LOCK.lock();
        CompilationMonitor monitor = compilationMonitor;
        boolean notifiedStart = false;
        settings = settings != null ? settings : new ClientSettings();

        List<Message> messages = new ArrayList<>();

        LOG.info("fileUriOrPath: " + fileUriOrPath);
        LOG.info("settings.getModelRepositories(): " + settings.getModelRepositories());
               
        Path logFile = null;
        FileLogger flog = null;
        TransferDescription td = null;
        String logText = "";
        try {
            if (monitor != null) {
                monitor.onStart(Thread.currentThread());
                notifiedStart = true;
            }

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

            td = ch.interlis.ili2c.Main.runCompiler(cfg, set, null);
            
            if (td == null) {
                EhiLogger.logError("...compiler run failed " + dateOut);
            } else {
                EhiLogger.logState("...compiler run done " + dateOut);
            }
        } catch (Exception e) {
            return new CompilationOutcome(null, "[ili2c] failed: " + e.getMessage(), messages);
        } finally {
            try {
                StdListener.getInstance().skipInfo(false);
                EhiLogger.getInstance().addListener(StdListener.getInstance());

                if (flog != null) {
                    try { flog.close(); } catch (Exception ignore) {}
                    EhiLogger.getInstance().removeListener(flog);
                }

                if (logFile != null) {
                    try {
                        logText = Files.exists(logFile)
                                ? Files.readString(logFile, StandardCharsets.UTF_8)
                                : "[ili2c] log file not found: " + logFile;
                    } catch (IOException io) {
                        logText = "[ili2c] failed to read log file: " + io.getMessage();
                    } finally {
                        try {
                            Files.deleteIfExists(logFile);
                        } catch (IOException ignore) {
                        }
                    }
                }
            } finally {
                if (monitor != null && notifiedStart) {
                    monitor.onFinish(Thread.currentThread());
                }
                COMPILE_LOCK.unlock();
            }
        }

        messages = Ili2cLogParser.parseErrors(logText);

        return new CompilationOutcome(td, logText, messages);
    }
}
