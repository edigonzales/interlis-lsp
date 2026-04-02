package ch.so.agi.lsp.interlis.glsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.lsp.interlis.diagram.InterlisDiagramModel;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.workspace.CommandHandlers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.eclipse.glsp.server.features.core.model.RequestModelAction;
import org.eclipse.glsp.server.model.DefaultGModelState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisGlspSourceModelStorageTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        InterlisGlspBridge.clear();
    }

    @Test
    void loadSourceModelUsesFriendlyMissingFileMessageWithoutWarnNoise() {
        Path missing = tempDir.resolve("MissingDiagram.ili");

        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisGlspBridge.bindLanguageServer(server);

        InterlisGlspSourceModelStorage storage = new InterlisGlspSourceModelStorage();
        storage.modelState = new DefaultGModelState();

        RequestModelAction request = new RequestModelAction(Map.of("sourceUri", missing.toUri().toString()));

        Logger logger = (Logger) LogManager.getLogger(InterlisGlspSourceModelStorage.class);
        Level originalLevel = logger.getLevel();
        List<LogEvent> events = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender(
                "glsp-source-storage-test",
                null,
                PatternLayout.createDefaultLayout(),
                false,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event != null) {
                    events.add(event.toImmutable());
                }
            }
        };

        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            storage.loadSourceModel(request);
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }

        assertEquals(
                CommandHandlers.diagramSourceMissingMessage(),
                storage.modelState.getProperty(InterlisGlspModelStateKeys.ERROR, String.class).orElse(null));
        assertTrue(storage.modelState.getProperty(InterlisGlspModelStateKeys.MODEL, InterlisDiagramModel.DiagramModel.class)
                .isEmpty());
        assertTrue(events.stream().anyMatch(event ->
                        Level.DEBUG.equals(event.getLevel())
                                && event.getMessage().getFormattedMessage().contains("source model missing")),
                "Expected missing source failures to be logged at DEBUG");
        assertFalse(events.stream().anyMatch(event ->
                        Level.WARN.equals(event.getLevel())
                                || Level.ERROR.equals(event.getLevel())
                                || Level.FATAL.equals(event.getLevel())),
                "Expected missing source failures to avoid WARN-or-higher logging");
    }
}
