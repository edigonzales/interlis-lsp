package ch.so.agi.lsp.interlis;

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
        assertTrue(html.contains("plantuml/png"));
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
