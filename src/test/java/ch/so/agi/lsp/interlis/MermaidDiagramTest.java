package ch.so.agi.lsp.interlis;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.compiler.Ili2cUtil;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import ch.so.agi.lsp.interlis.text.InterlisTextDocumentService;
import ch.so.agi.lsp.interlis.workspace.CommandHandlers;
import ch.so.agi.lsp.interlis.workspace.InterlisWorkspaceService;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MermaidDiagramTest {
    @TempDir Path directory;
    private static final String MODEL = """
            INTERLIS 2.3;
            MODEL Auto (en) AT "http://example.com" VERSION "2024-01-01" =
              TOPIC T =
                STRUCTURE Location = Label : TEXT; END Location;
                CLASS Thing = Name : TEXT; MANDATORY CONSTRAINT Name <> ""; END Thing;
              END T;
            END Auto.
            """;

    private static class Server extends InterlisLanguageServer {
        final CompilationCache cache = new CompilationCache();
        final AtomicInteger compiles = new AtomicInteger();
        int notifications;
        final InterlisTextDocumentService documents = new InterlisTextDocumentService(this, cache, (settings, path) -> {
            compiles.incrementAndGet();
            return Ili2cUtil.compile(settings, path);
        });
        @Override public InterlisTextDocumentService getInterlisTextDocumentService() { return documents; }
        @Override public void notifyCompileFinished(String uri, boolean success) { notifications++; }
    }

    private void change(Server server, Path file, int version, String text) {
        server.documents.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file.toUri().toString(), version),
                List.of(new TextDocumentContentChangeEvent(text))));
    }

    @Test
    void rendersSavedSnapshotsAndNewSettingsWithoutCompilingOrNotifying() throws Exception {
        Path file = directory.resolve("Auto.ili");
        Files.writeString(file, MODEL);
        Server server = new Server();
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories("%ILI_DIR;%JAR_DIR");
        server.setClientSettings(settings);
        server.documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(file.toUri().toString(), "interlis", 1, MODEL)));
        CommandHandlers handlers = new CommandHandlers(server);
        var saved = server.cache.getSavedAttempt(file.toString());
        assertNotNull(saved.getTransferDescription(), saved.getLogText());
        int compileCount = server.compiles.get(), notificationCount = server.notifications;
        var current = handlers.mermaidDiagram(file.toString()).get();
        assertEquals("current", current.status());
        assertTrue(current.source().contains("Name[0..1]"));
        assertFalse(current.hideEmptyMembersBox());
        change(server, file, 2, "");
        assertEquals(current.source(), handlers.mermaidDiagram(file.toString()).get().source());
        assertSame(saved, server.cache.getSavedAttempt(file.toString()));
        assertEquals(compileCount, server.compiles.get());
        assertEquals(notificationCount, server.notifications);

        String invalid = MODEL.replace("Name : TEXT", "Name : MissingType");
        Files.writeString(file, invalid);
        change(server, file, 3, invalid);
        server.documents.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(file.toUri().toString())));
        var failed = server.cache.getSavedAttempt(file.toString());
        compileCount = server.compiles.get(); notificationCount = server.notifications;
        assertNull(failed.getTransferDescription());
        assertEquals("last-valid", handlers.mermaidDiagram(file.toString()).get().status());
        settings.setUmlAttributeMode("NONE");
        var compact = handlers.mermaidDiagram(file.toString()).get();
        assertEquals("last-valid", compact.status());
        assertTrue(compact.hideEmptyMembersBox());
        assertFalse(compact.source().contains("Name["));
        assertTrue(compact.source().contains("Structure"));
        assertSame(failed, server.cache.getSavedAttempt(file.toString()));
        assertSame(saved, server.cache.getSuccessful(file.toString()));
        assertEquals(compileCount, server.compiles.get());
        assertEquals(notificationCount, server.notifications);
    }

    @Test
    void emptySavedFileStaysEmptyDespiteUnsavedEditorTextAndRecoversOnSave() throws Exception {
        Path file = directory.resolve("Auto.ili");
        Files.writeString(file, MODEL);
        Server server = new Server();
        server.documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(file.toUri().toString(), "interlis", 1, MODEL)));
        Files.writeString(file, "");
        change(server, file, 2, "");
        server.documents.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(file.toUri().toString())));
        CommandHandlers handlers = new CommandHandlers(server);
        assertEquals("empty", handlers.mermaidDiagram(file.toString()).get().status());
        change(server, file, 3, MODEL);
        assertEquals("empty", handlers.mermaidDiagram(file.toString()).get().status());
        Files.writeString(file, MODEL);
        server.documents.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(file.toUri().toString())));
        assertEquals("current", handlers.mermaidDiagram(file.toString()).get().status());
    }

    @Test
    void requestValidatesArgumentsAndDoesNotCompileWhenSnapshotsAreMissing() throws Exception {
        Path file = directory.resolve("Auto.ili");
        Files.writeString(file, MODEL);
        Server server = new Server();
        var workspace = (InterlisWorkspaceService) server.getWorkspaceService();
        assertEquals("unavailable", workspace.mermaidDiagram(Map.of("uri", file.toUri().toString())).get().status());
        server.cache.putSavedAttempt(file.toString(), new Ili2cUtil.CompilationOutcome(null, "invalid", List.of()));
        assertEquals("unavailable", workspace.mermaidDiagram(Map.of("uri", file.toUri().toString())).get().status());
        assertEquals("unavailable", new CommandHandlers(server).mermaidDiagram(directory.resolve("missing.ili").toString()).get().status());
        assertThrows(Exception.class, () -> workspace.mermaidDiagram(Map.of()).get());
        assertEquals(0, server.compiles.get());
        assertEquals(0, server.notifications);
    }
}
