package ch.so.agi.lsp.interlis;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.Collections;

public class InterlisTextDocumentService implements TextDocumentService {
    private final InterlisLanguageServer server;

    public InterlisTextDocumentService(InterlisLanguageServer server) {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        InterlisValidator.ValidationOutcome outcome = new InterlisValidator().validate(uri);
        server.publishDiagnostics(uri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        InterlisValidator.ValidationOutcome outcome = new InterlisValidator().validate(uri);
        server.publishDiagnostics(uri, DiagnosticsMapper.toDiagnostics(outcome.getMessages()));
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        server.publishDiagnostics(uri, Collections.emptyList());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Optional: re-validate on save
    }
}
