package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class InterlisWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void compileCommandAcceptsJsonObjectArgument() throws Exception {
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

        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisWorkspaceService workspace = new InterlisWorkspaceService(server);

        JsonObject argument = JsonParser.parseString("{\"uri\":\"" + iliFile.toUri() + "\"}").getAsJsonObject();

        ExecuteCommandParams params = new ExecuteCommandParams();
        params.setCommand(InterlisLanguageServer.CMD_COMPILE);
        params.setArguments(List.of(argument));

        CompletableFuture<Object> future = workspace.executeCommand(params);
        Object result = future.get(30, TimeUnit.SECONDS);

        assertInstanceOf(String.class, result);
    }
}
