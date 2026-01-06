package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.export.html.InterlisHtmlExporter;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.workspace.CommandHandlers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

class CommandHandlersTest {

    @TempDir
    Path tempDir;

    @Test
    void generateUmlReturnsHtmlFromModel() throws Exception {
        Path iliFile = tempDir.resolve("SimpleModel.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleModel (en)\n" +
                "AT \"http://example.com/SimpleModel.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Person =\n" +
                "    END Person;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleModel.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        CompletableFuture<Object> future = handlers.generateUml(iliFile.toString());
        Object htmlObj = future.get(30, TimeUnit.SECONDS);
        String html = assertInstanceOf(String.class, htmlObj);

        assertTrue(html.contains("classDiagram"));
        assertTrue(html.contains("Person"));
    }

    @Test
    void generatePlantUmlReturnsHtmlFromModel() throws Exception {
        Path iliFile = tempDir.resolve("SimpleModelPlant.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleModelPlant (en)\n" +
                "AT \"http://example.com/SimpleModelPlant.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS Thing =\n" +
                "    END Thing;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleModelPlant.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        CompletableFuture<Object> future = handlers.generatePlantUml(iliFile.toString());
        Object htmlObj = future.get(30, TimeUnit.SECONDS);
        String html = assertInstanceOf(String.class, htmlObj);

        assertTrue(html.contains("@startuml"));
        assertTrue(html.contains("https://uml.planttext.com/plantuml/png/"));
        assertTrue(html.contains("https://uml.planttext.com/plantuml/svg/"));
    }

    @Test
    void exportGraphmlReturnsDiagramText() throws Exception {
        Path iliFile = tempDir.resolve("SimpleGraph.ili");
        Files.writeString(iliFile, "INTERLIS 2.3;\n" +
                "MODEL SimpleGraph (en)\n" +
                "AT \"http://example.com/SimpleGraph.ili\"\n" +
                "VERSION \"2024-01-01\" =\n" +
                "  TOPIC SimpleTopic =\n" +
                "    CLASS NodeA =\n" +
                "    END NodeA;\n" +
                "    CLASS NodeB =\n" +
                "    END NodeB;\n" +
                "    ASSOCIATION Connects =\n" +
                "      a -- {0..*} NodeA;\n" +
                "      b -- {1} NodeB;\n" +
                "    END Connects;\n" +
                "  END SimpleTopic;\n" +
                "END SimpleGraph.\n");

        Ili2cUtil.CompilationOutcome outcome = Ili2cUtil.compile(new ClientSettings(), iliFile.toString());
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());

        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        CompletableFuture<String> future = handlers.exportGraphml(iliFile.toString());
        String graphml = future.get(30, TimeUnit.SECONDS);

        assertNotNull(graphml);
        assertTrue(graphml.contains("<graphml"));
        assertTrue(graphml.contains("NodeA"));
        assertTrue(graphml.contains("NodeB"));
    }

    @Test
    void exportDocxFailsWithResponseErrorWhenCompilationFails() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        Path nonexistent = tempDir.resolve("Missing.ili");
        CompletableFuture<String> future = handlers.exportDocx(nonexistent.toUri().toString(), null);

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        Throwable cause = exec.getCause();
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, cause);
        assertEquals(ResponseErrorCode.InternalError.getValue(), ree.getResponseError().getCode());
        assertTrue(ree.getMessage().contains(nonexistent.getFileName().toString()));
    }

    @Test
    void exportGraphmlFailsWithResponseErrorWhenCompilationFails() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        Path nonexistent = tempDir.resolve("MissingGraph.ili");
        CompletableFuture<String> future = handlers.exportGraphml(nonexistent.toUri().toString());

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        Throwable cause = exec.getCause();
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, cause);
        assertEquals(ResponseErrorCode.InternalError.getValue(), ree.getResponseError().getCode());
        assertTrue(ree.getMessage().contains(nonexistent.getFileName().toString()));
    }

    @Test
    void exportHtmlFailsWithResponseErrorWhenCompilationFails() {
        InterlisLanguageServer server = new InterlisLanguageServer();
        CommandHandlers handlers = new CommandHandlers(server);

        Path nonexistent = tempDir.resolve("MissingHtml.ili");
        CompletableFuture<String> future = handlers.exportHtml(nonexistent.toUri().toString(), null);

        ExecutionException exec = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        Throwable cause = exec.getCause();
        ResponseErrorException ree = assertInstanceOf(ResponseErrorException.class, cause);
        assertEquals(ResponseErrorCode.InternalError.getValue(), ree.getResponseError().getCode());
        assertTrue(ree.getMessage().contains(nonexistent.getFileName().toString()));
    }
}
