package ch.so.agi.lsp.interlis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
