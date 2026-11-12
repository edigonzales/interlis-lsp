package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.lsp4j.*;
import ch.so.agi.lsp.interlis.compiler.*;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormattingFeatureTest {

    @Test
    void initializeAdvertisesFormattingCapability() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();

        InitializeResult result = server.initialize(new InitializeParams()).get();
        ServerCapabilities capabilities = result.getCapabilities();

        Either<Boolean, ?> provider = capabilities.getDocumentFormattingProvider();
        assertNotNull(provider);
        assertTrue(provider.isLeft());
        assertTrue(provider.getLeft());
    }

    @Test
    void formattingPrettyPrintsEntireDocument() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisTextDocumentService service = (InterlisTextDocumentService) server.getTextDocumentService();

        Path iliFile = Files.createTempFile("ili-format", ".ili");
        String unformatted = """
INTERLIS 2.4;

MODEL 

ModelA (de)
  AT "https://example.com"
  VERSION "2025-09-17"
  =

  STRUCTURE StructA =
    attr1 : TEXT;
  END StructA;

END ModelA.     
                """;
        Files.writeString(iliFile, unformatted);

        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(iliFile.toUri().toString()));

        List<? extends TextEdit> edits = service.formatting(params).get();
        assertEquals(1, edits.size());

        TextEdit edit = edits.get(0);
        String expected = """
INTERLIS 2.4;

MODEL ModelA (de)
  AT "https://example.com"
  VERSION "2025-09-17"
  =

  STRUCTURE StructA =
    attr1 : TEXT;
  END StructA;

END ModelA.
                """;
        assertEquals(expected, edit.getNewText());
        assertEquals(0, edit.getRange().getStart().getLine());
        assertEquals(0, edit.getRange().getStart().getCharacter());
    }

    @Test
    void formattingReturnsNoEditWhenAlreadyPretty() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer();
        InterlisTextDocumentService service = (InterlisTextDocumentService) server.getTextDocumentService();

        Path iliFile = Files.createTempFile("ili-format", ".ili");
        String unformatted = """
INTERLIS 2.4;

MODEL ModelA (de)
  AT "https://example.com"
  VERSION "2025-09-17"
  =

  STRUCTURE StructA =
    attr1 : TEXT;
  END StructA;

END ModelA.
                """;
        Files.writeString(iliFile, unformatted);

        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(iliFile.toUri().toString()));

        List<? extends TextEdit> edits = service.formatting(params).get();
        assertTrue(edits.isEmpty());
    }

    private static final String VALID = """
            INTERLIS 2.4;
            MODEL ModelA (de) AT "https://example.com" VERSION "2025-09-17" =
            STRUCTURE StructA = attr1 : TEXT; END StructA;
            END ModelA.
            """;

    private static DocumentFormattingParams formattingParams(Path file) {
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(file.toUri().toString()));
        return params;
    }

    private static void change(InterlisTextDocumentService service, Path file, int version, String text) {
        service.didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(file.toUri().toString(), version),
                List.of(new TextDocumentContentChangeEvent(text))));
    }

    @Test
    void invalidDirtyModelsNeverUseSuccessfulSavedSnapshot(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("ModelA.ili");
        Files.writeString(file, VALID);
        CompilationCache cache = new CompilationCache();
        InterlisTextDocumentService service = new InterlisTextDocumentService(
                new InterlisLanguageServer(), cache, Ili2cUtil::compile);
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(file.toUri().toString(), "interlis", 1, VALID)));
        var saved = cache.getSavedAttempt(file.toString());
        assertNotNull(saved.getTransferDescription());
        int version = 2;
        for (String invalid : List.of(
                VALID.replace("attr1 : TEXT;", "attr1 : ;"),
                VALID.replace("attr1 : TEXT;", "attr1 : UnknownType;"),
                VALID.replace("STRUCTURE StructA", "IMPORTS MissingFormattingDependency; STRUCTURE StructA"),
                "  \n")) {
            change(service, file, version++, invalid);
            assertTrue(service.formatting(formattingParams(file)).get(30, TimeUnit.SECONDS).isEmpty());
            assertSame(saved, cache.getSavedAttempt(file.toString()));
            assertSame(saved, cache.getSuccessful(file.toString()));
            assertEquals(VALID, Files.readString(file));
        }
    }

    @Test
    void warningsDoNotPreventFormatting(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("ModelA.ili");
        Files.writeString(file, VALID);
        var compiled = Ili2cUtil.compile(new ClientSettings(), file.toString());
        var warning = new Ili2cUtil.Message(Ili2cUtil.Message.Severity.WARNING, file.toString(), 1, 1, "warning");
        InterlisTextDocumentService service = new InterlisTextDocumentService(new InterlisLanguageServer(),
                new CompilationCache(), (settings, path) -> new Ili2cUtil.CompilationOutcome(
                        compiled.getTransferDescription(), "warning", List.of(warning)));
        assertEquals(1, service.formatting(formattingParams(file)).get(30, TimeUnit.SECONDS).size());
    }

    @Test
    void discardsChangedClosedReopenedAndCancelledRequests(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("ModelA.ili");
        Files.writeString(file, VALID);
        var compiled = Ili2cUtil.compile(new ClientSettings(), file.toString());
        for (String action : List.of("change", "close", "reopen", "cancel")) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Path> temporary = new AtomicReference<>();
            InterlisTextDocumentService service = new InterlisTextDocumentService(new InterlisLanguageServer(),
                    new CompilationCache(), (settings, path) -> {
                        if (!path.equals(file.toString())) {
                            temporary.set(Path.of(path));
                            started.countDown();
                            try {
                                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new CancellationException();
                            } finally {
                                finished.countDown();
                            }
                        }
                        return compiled;
                    });
            var item = new TextDocumentItem(file.toUri().toString(), "interlis", 1, VALID);
            service.didOpen(new DidOpenTextDocumentParams(item));
            var request = service.formatting(formattingParams(file));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                switch (action) {
                    case "change" -> change(service, file, 2, VALID + "!! new edit");
                    case "close" -> service.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(item.getUri())));
                    case "reopen" -> {
                        service.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(item.getUri())));
                        service.didOpen(new DidOpenTextDocumentParams(item));
                    }
                    case "cancel" -> request.cancel(true);
                }
            } finally {
                release.countDown();
            }
            if (action.equals("cancel")) assertTrue(request.isCancelled());
            else assertTrue(request.get(10, TimeUnit.SECONDS).isEmpty(), action);
            assertTrue(finished.await(10, TimeUnit.SECONDS));
            // Cancellation completes the future before the worker's finally block; await cleanup explicitly.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (Files.exists(temporary.get().getParent()) && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(Files.exists(temporary.get().getParent()), action);
        }
    }

    @Test
    void formattingReportsNetworkFailureOnlyForCurrentSnapshot(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("ModelA.ili");
        Files.writeString(file, VALID);
        var compiled = Ili2cUtil.compile(new ClientSettings(), file.toString());
        var problem = new NetworkProblems.Problem(NetworkProblems.Kind.CONNECTION, "https://repo.example", null);
        for (String action : List.of("current", "change", "close")) {
            StringBuffer log = new StringBuffer();
            StringBuffer debug = new StringBuffer();
            InterlisLanguageServer server = new InterlisLanguageServer() {
                @Override public void logToClient(String text) { log.append(text); }
                @Override public void debugLogToClient(String text) { debug.append(text); }
            };
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompilationCache cache = new CompilationCache();
            InterlisTextDocumentService service = new InterlisTextDocumentService(server, cache, (settings, path) -> {
                if (path.equals(file.toString())) return compiled;
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("timed out");
                } catch (InterruptedException ex) { throw new CancellationException(); }
                return new Ili2cUtil.CompilationOutcome(null, "technical ConnectException", List.of(), List.of(problem));
            });
            service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(file.toUri().toString(), "interlis", 1, VALID)));
            log.setLength(0);
            debug.setLength(0);
            var request = service.formatting(formattingParams(file));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                if (action.equals("change")) change(service, file, 2, VALID + "!! changed");
                if (action.equals("close")) service.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(file.toUri().toString())));
            } finally { release.countDown(); }
            assertTrue(request.get(10, TimeUnit.SECONDS).isEmpty());
            if (action.equals("current")) {
                assertTrue(log.toString().contains("Saving can continue"));
                assertTrue(log.toString().contains("https://repo.example"));
                assertEquals(1, log.toString().lines().filter(line -> line.startsWith("Network warning:")).count());
                assertTrue(debug.toString().contains("technical ConnectException"));
            } else {
                assertEquals("", log.toString(), action);
                assertEquals("", debug.toString(), action);
            }
            assertEquals(VALID, Files.readString(file));
            assertSame(compiled, cache.getSavedAttempt(file.toString()));
        }
    }

    @Test
    void successfulCompilationWithNetworkWarningStillFormats(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("ModelA.ili");
        Files.writeString(file, VALID);
        var compiled = Ili2cUtil.compile(new ClientSettings(), file.toString());
        var problem = new NetworkProblems.Problem(NetworkProblems.Kind.TIMEOUT, null, null);
        InterlisTextDocumentService service = new InterlisTextDocumentService(new InterlisLanguageServer(),
                new CompilationCache(), (settings, path) -> new Ili2cUtil.CompilationOutcome(
                        compiled.getTransferDescription(), "", List.of(), List.of(problem)));
        assertEquals(1, service.formatting(formattingParams(file)).get(10, TimeUnit.SECONDS).size());
    }
}
