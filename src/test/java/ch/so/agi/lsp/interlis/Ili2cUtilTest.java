package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.compiler.NetworkProblems;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ili2cUtilTest {
    @AfterEach
    void clearMonitor() {
        Ili2cUtil.setCompilationMonitor(null);
    }

    @Test
    void compileRunsSequentiallyWhenInvokedConcurrently(@TempDir Path tempDir) throws Exception {
        Path modelPath = Files.createTempFile(tempDir, "Concurrent", ".ili");
        Files.writeString(modelPath, String.join("\n",
                "INTERLIS 2.3;",
                "MODEL Concurrent (en)",
                "AT \"http://example.com/Concurrent.ili\"",
                "VERSION \"2024-01-01\" =",
                "END Concurrent.",
                ""));

        int workerCount = 3;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(workerCount);
        List<Future<Ili2cUtil.CompilationOutcome>> futures = new ArrayList<>();

        AtomicInteger concurrentRuns = new AtomicInteger();
        AtomicInteger maxConcurrentRuns = new AtomicInteger();

        Ili2cUtil.setCompilationMonitor(new Ili2cUtil.CompilationMonitor() {
            @Override
            public void onStart(Thread thread) {
                int running = concurrentRuns.incrementAndGet();
                maxConcurrentRuns.updateAndGet(prev -> Math.max(prev, running));
            }

            @Override
            public void onFinish(Thread thread) {
                concurrentRuns.decrementAndGet();
            }
        });

        for (int i = 0; i < workerCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for start signal");
                }
                ClientSettings cfg = new ClientSettings();
                return Ili2cUtil.compile(cfg, modelPath.toString());
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        for (Future<Ili2cUtil.CompilationOutcome> future : futures) {
            Ili2cUtil.CompilationOutcome outcome = future.get(30, TimeUnit.SECONDS);
            assertNotNull(outcome, "Expected compile to return an outcome");
            assertNotNull(outcome.getTransferDescription(), "Expected compile to succeed");
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "Expected executor to terminate");

        assertTrue(maxConcurrentRuns.get() <= 1,
                "Expected at most one compile invocation to run at a time but saw " + maxConcurrentRuns.get());
    }

    private static String model(String name, String body) {
        return "INTERLIS 2.4;\nMODEL " + name
                + " (de) AT \"https://example.com\" VERSION \"2025-09-17\" =\n"
                + body + "\nEND " + name + ".\n";
    }

    @Test
    void snapshotResolvesLocalAndRelativeRepositories(@TempDir Path directory) throws Exception {
        Path repositories = Files.createDirectory(directory.resolve("repos"));
        Files.writeString(directory.resolve("LocalDependency.ili"), model("LocalDependency", "DOMAIN LocalText = TEXT;"));
        Files.writeString(repositories.resolve("OtherDependency.ili"), model("OtherDependency", "DOMAIN OtherText = TEXT;"));
        Path original = directory.resolve("Snapshot.ili");
        Files.writeString(original, "original disk text");
        String source = model("Snapshot", "IMPORTS LocalDependency, OtherDependency;\n"
                + "STRUCTURE S = a : LocalDependency.LocalText; b : OtherDependency.OtherText; END S;");
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories("%ILI_DIR;repos;%JAR_DIR");
        var outcome = Ili2cUtil.compileSnapshot(settings, original.toString(), source);
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());
        assertEquals("original disk text", Files.readString(original));
        assertEquals("%ILI_DIR;repos;%JAR_DIR", settings.getModelRepositories());
        String formatted = Ili2cUtil.prettyPrint(outcome.getTransferDescription(), original.toString());
        assertTrue(formatted.contains("MODEL Snapshot"));
        assertFalse(formatted.contains("MODEL LocalDependency"));
        assertFalse(formatted.contains("MODEL OtherDependency"));
    }

    @Test
    void snapshotDefaultRepositoryStillUsesOriginalDirectory(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("LocalDependency.ili"), model("LocalDependency", "DOMAIN LocalText = TEXT;"));
        var outcome = Ili2cUtil.compileSnapshot(new ClientSettings(), directory.resolve("Snapshot.ili").toString(),
                model("Snapshot", "IMPORTS LocalDependency;"));
        assertNotNull(outcome.getTransferDescription(), outcome.getLogText());
    }

    @Test
    void snapshotRemapsErrorsAndCleansUpOnFailure(@TempDir Path directory) throws Exception {
        Path original = directory.resolve("Snapshot.ili");
        AtomicReference<Path> temporary = new AtomicReference<>();
        var outcome = Ili2cUtil.compileSnapshot(new ClientSettings(), original.toString(), model("Snapshot", "STRUCTURE S = a : ; END S;"), (settings, path) -> {
            temporary.set(Path.of(path));
            assertEquals("Snapshot.ili", Path.of(path).getFileName().toString());
            try { assertEquals(model("Snapshot", "STRUCTURE S = a : ; END S;"), Files.readString(Path.of(path))); }
            catch (java.io.IOException ex) { throw new AssertionError(ex); }
            return Ili2cUtil.compile(settings, path);
        });
        assertNull(outcome.getTransferDescription());
        assertFalse(outcome.getMessages().isEmpty(), outcome.getLogText());
        assertTrue(outcome.getMessages().stream().anyMatch(message -> original.toString().equals(message.getFileUriOrPath())), outcome.getLogText());
        assertTrue(outcome.getMessages().stream().noneMatch(message -> temporary.get().toString().equals(message.getFileUriOrPath())));
        assertFalse(outcome.getLogText().contains(temporary.get().toString()));
        assertFalse(Files.exists(temporary.get().getParent()));
        assertThrows(java.util.concurrent.CancellationException.class, () ->
            Ili2cUtil.compileSnapshot(new ClientSettings(), original.toString(), "source", (settings, path) -> {
                temporary.set(Path.of(path));
                throw new java.util.concurrent.CancellationException();
            }));
        assertFalse(Files.exists(temporary.get().getParent()));
        assertFalse(Files.exists(original));
    }

    @Test
    void realCompilerExplainsUnavailableRepositoryAndKeepsRawLog(@TempDir Path directory) throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        String repository = "http://127.0.0.1:" + port + "/";
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories(repository);
        var outcome = Ili2cUtil.compileSnapshot(settings, directory.resolve("Snapshot.ili").toString(),
                model("Snapshot", "IMPORTS MissingNetworkTestModel;"));
        assertNull(outcome.getTransferDescription());
        assertFalse(outcome.getNetworkProblems().isEmpty(), outcome.getLogText());
        assertEquals(NetworkProblems.Kind.CONNECTION, outcome.getNetworkProblems().get(0).kind());
        assertTrue(outcome.getLogText().startsWith("Network warning:"), outcome.getLogText());
        assertTrue(outcome.getLogText().contains("ConnectException"), outcome.getLogText());
        assertThrows(UnsupportedOperationException.class, () -> outcome.getNetworkProblems().clear());
    }

    @Test
    void compileExceptionPreservesLogsAndNetworkCause(@TempDir Path directory) {
        Ili2cUtil.setCompilationMonitor(new Ili2cUtil.CompilationMonitor() {
            @Override public void onStart(Thread thread) {
                ch.ehi.basics.logging.EhiLogger.logState("before failure");
                throw new RuntimeException("wrapped", new java.net.UnknownHostException("repo.example"));
            }
            @Override public void onFinish(Thread thread) {}
        });
        var outcome = Ili2cUtil.compile(new ClientSettings(), directory.resolve("Source.ili").toString());
        assertEquals(NetworkProblems.Kind.DNS, outcome.getNetworkProblems().get(0).kind());
        assertTrue(outcome.getLogText().contains("UnknownHostException"));
        assertTrue(outcome.getLogText().contains("wrapped"));
        assertTrue(outcome.getMessages().isEmpty());
    }
}
