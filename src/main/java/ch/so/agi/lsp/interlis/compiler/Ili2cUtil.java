package ch.so.agi.lsp.interlis.compiler;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.so.agi.lsp.interlis.server.ClientSettings;
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

    public interface CompilationMonitor {
        void onStart(Thread thread);

        void onFinish(Thread thread);
    }

    public static void setCompilationMonitor(CompilationMonitor monitor) {
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

    /** Pretty print a saved source file. */
    public static String prettyPrint(ClientSettings settings, String sourceFile) throws IOException {
        if (sourceFile == null) return "";
        CompilationOutcome outcome = compile(settings, sourceFile);
        return prettyPrint(outcome != null ? outcome.getTransferDescription() : null, sourceFile);
    }

    public static String prettyPrint(TransferDescription td, String sourceFile) throws IOException {
        if (sourceFile == null) return "";
        if (td == null) return null;
        // The generator accepts a Writer; no temporary output file is necessary.
        StringWriter target = new StringWriter();
        TransferDescription desc = new TransferDescription();
        for (Model model : td.getModelsFromLastFile()) {
            desc.add(model);
        }
        new Interlis2Generator().generate(target, desc, false);
        return target.toString();
    }

    public static CompilationOutcome compileSnapshot(ClientSettings settings, String originalPath, String text)
            throws IOException {
        return compileSnapshot(settings, originalPath, text, Ili2cUtil::compile);
    }

    /** Compile isolated editor text without touching the source file or saved compilation caches. */
    public static CompilationOutcome compileSnapshot(ClientSettings settings, String originalPath, String text,
            BiFunction<ClientSettings, String, CompilationOutcome> compiler) throws IOException {
        Path original = (originalPath.startsWith("file:") ? Path.of(URI.create(originalPath)) : Path.of(originalPath))
                .toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory("ili-snapshot-");
        Path source = directory.resolve(original.getFileName());
        try {
            Files.writeString(source, text, StandardCharsets.UTF_8);
            String repositories = settings != null ? settings.getModelRepositories() : "";
            if (repositories == null || repositories.isBlank()) repositories = Ili2cSettings.DEFAULT_ILIDIRS;
            List<String> resolved = new ArrayList<>();
            for (String entry : repositories.split(";")) {
                entry = entry.trim().replace("%ILI_DIR", original.getParent().toString());
                if (entry.isEmpty()) continue;
                if (!entry.contains("%") && !entry.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
                        && !Path.of(entry).isAbsolute()) {
                    entry = original.getParent().resolve(entry).normalize().toString();
                }
                resolved.add(entry);
            }
            ClientSettings snapshotSettings = new ClientSettings();
            snapshotSettings.setModelRepositories(String.join(";", resolved));
            CompilationOutcome outcome = compiler.apply(snapshotSettings, source.toString());
            if (outcome == null) return null;
            String sourcePath = source.toString();
            String targetPath = original.toString();
            List<Message> messages = outcome.getMessages().stream().map(message -> new Message(
                    message.getSeverity(),
                    sourcePath.equals(message.getFileUriOrPath()) || source.toUri().toString().equals(message.getFileUriOrPath())
                            ? targetPath : message.getFileUriOrPath(),
                    message.getLine(), message.getColumn(), message.getText().replace(source.toUri().toString(), original.toUri().toString()).replace(sourcePath, targetPath))).toList();
            return new CompilationOutcome(outcome.getTransferDescription(),
                    outcome.getLogText().replace(source.toUri().toString(), original.toUri().toString()).replace(sourcePath, targetPath), messages);
        } finally {
            try {
                Files.deleteIfExists(source);
            } finally {
                Files.deleteIfExists(directory);
            }
        }
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
