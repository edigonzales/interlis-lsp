package ch.so.agi.lsp.interlis.compiler;

import ch.ehi.basics.logging.EhiLogger;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetworkProblemsTest {
    @Test
    void recognizesNestedCausesWithoutClaimingComputerIsOffline() {
        for (var pair : List.of(
                new Object[]{new UnknownHostException("host"), NetworkProblems.Kind.DNS},
                new Object[]{new SocketTimeoutException("Read timed out"), NetworkProblems.Kind.TIMEOUT},
                new Object[]{new ConnectException("Connection refused"), NetworkProblems.Kind.CONNECTION},
                new Object[]{new SSLHandshakeException("certificate"), NetworkProblems.Kind.TLS})) {
            var problem = NetworkProblems.fromException(new RuntimeException("wrapped", (Exception) pair[0]), "https://repo.example");
            assertEquals(pair[1], problem.kind());
            assertEquals("https://repo.example", problem.target());
            assertFalse(problem.describe().contains("offline"));
        }
    }

    @Test
    void recognizesOnlyExplicitLoggedNetworkFailuresAndUnambiguousTargets() {
        var problem = NetworkProblems.fromText("repository <https://repo.example/> ignored; java.net.UnknownHostException: repo.example", null);
        assertEquals(NetworkProblems.Kind.DNS, problem.kind());
        assertEquals("https://repo.example/", problem.target());
        var http = NetworkProblems.fromText("java.io.IOException: Server returned HTTP response code: 503 for URL: https://repo.example/ilimodels.xml", null);
        assertEquals(503, http.httpStatus());
        assertNull(NetworkProblems.fromText("java.net.ConnectException: https://one.example https://two.example", "https://fallback.example").target());
        for (String text : List.of("Missing import ModelA", "Error: model.ili:4:unexpected token", "Error: File not found", "404", "request failed")) {
            assertNull(NetworkProblems.fromText(text, "https://repo.example"));
        }
        assertNull(NetworkProblems.fromException(new FileNotFoundException("local.ili"), null));
        assertNull(NetworkProblems.fromException(new IllegalStateException("unknown"), null));
    }

    @Test
    void collectorsDeduplicateIgnoreOtherThreadsAndStopListeningOnClose() throws Exception {
        var collector = new NetworkProblems.Collector("https://repo.example");
        try (collector) {
            EhiLogger.logError(new SocketTimeoutException());
            EhiLogger.logError(new SocketTimeoutException());
            Thread thread = new Thread(() -> EhiLogger.logError(new UnknownHostException()));
            thread.start();
            thread.join();
            assertEquals(1, collector.problems().size());
        }
        EhiLogger.logError(new UnknownHostException());
        assertEquals(1, collector.problems().size());
        assertEquals(1, NetworkProblems.summary(collector.problems()).lines().count());
    }

    @Test
    void networkFailuresDoNotInventSourceDiagnostics() {
        var messages = Ili2cLogParser.parseErrors("Error: java.net.ConnectException: Connection refused\n"
                + "Error: model.ili:7:Unknown type MissingType\n");
        assertEquals(1, messages.size());
        assertEquals(7, messages.get(0).getLine());
    }
}
