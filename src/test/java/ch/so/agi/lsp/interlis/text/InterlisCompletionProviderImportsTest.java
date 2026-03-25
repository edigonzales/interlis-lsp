package ch.so.agi.lsp.interlis.text;

import ch.so.agi.lsp.interlis.compiler.CompilationCache;
import ch.so.agi.lsp.interlis.live.InterlisLanguageLevel;
import ch.so.agi.lsp.interlis.live.LiveAnalysisService;
import ch.so.agi.lsp.interlis.model.ModelDiscoveryService;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.so.agi.lsp.interlis.server.InterlisLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterlisCompletionProviderImportsTest {

    @Test
    void importCompletionPassesInterlis24LanguageLevelToModelDiscovery() {
        CapturingDiscovery discovery = new CapturingDiscovery();
        List<String> labels = importLabels("""
                INTERLIS 2.4;
                MODEL Demo (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS 
                END Demo.
                """, discovery);

        assertEquals(new InterlisLanguageLevel(2, 4), discovery.lastLanguageLevel);
        assertEquals(List.of("Model24"), labels);
    }

    @Test
    void importCompletionPassesInterlis23LanguageLevelToModelDiscovery() {
        CapturingDiscovery discovery = new CapturingDiscovery();
        List<String> labels = importLabels("""
                INTERLIS 2.3;
                MODEL Demo (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS 
                END Demo.
                """, discovery);

        assertEquals(new InterlisLanguageLevel(2, 3), discovery.lastLanguageLevel);
        assertEquals(List.of("Model23"), labels);
    }

    @Test
    void importCompletionLeavesUnknownHeaderUnfiltered() {
        CapturingDiscovery discovery = new CapturingDiscovery();
        List<String> labels = importLabels("""
                MODEL Demo (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS 
                END Demo.
                """, discovery);

        assertEquals(InterlisLanguageLevel.UNKNOWN, discovery.lastLanguageLevel);
        assertEquals(List.of("Model23", "Model24", "UnknownModel"), labels);
    }

    @Test
    void importCompletionStillExcludesAlreadyImportedModels() {
        CapturingDiscovery discovery = new CapturingDiscovery();
        List<String> labels = importLabels("""
                INTERLIS 2.4;
                MODEL Demo (en) AT "http://example.org" VERSION "2024-01-01" =
                  IMPORTS Model24, 
                END Demo.
                """, discovery);

        assertEquals(List.of(), labels);
    }

    private static List<String> importLabels(String text, CapturingDiscovery discovery) {
        String uri = "file:///ImportsCompletion.ili";
        InterlisLanguageServer server = new InterlisLanguageServer();
        server.setClientSettings(new ClientSettings());
        DocumentTracker documents = new DocumentTracker();
        documents.open(new TextDocumentItem(uri, "interlis", 1, text));
        InterlisCompletionProvider provider = new InterlisCompletionProvider(
                server,
                documents,
                new CompilationCache(),
                (cfg, path) -> null,
                discovery,
                new LiveAnalysisService());

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        int offset;
        int afterComma = text.indexOf("Model24,");
        if (afterComma >= 0) {
            offset = afterComma + "Model24,".length();
        } else {
            offset = text.indexOf("IMPORTS") + "IMPORTS".length();
        }
        params.setPosition(DocumentTracker.positionAt(text, offset));

        Either<List<CompletionItem>, CompletionList> result = provider.complete(params);
        List<String> labels = new ArrayList<>();
        List<CompletionItem> items = result.isLeft() ? result.getLeft() : result.getRight().getItems();
        for (CompletionItem item : items) {
            labels.add(item.getLabel());
        }
        return labels;
    }

    private static final class CapturingDiscovery extends ModelDiscoveryService {
        private InterlisLanguageLevel lastLanguageLevel = InterlisLanguageLevel.UNKNOWN;

        @Override
        public List<String> searchModels(ClientSettings settings,
                                         String prefix,
                                         Set<String> excludeUppercase,
                                         InterlisLanguageLevel languageLevel) {
            lastLanguageLevel = languageLevel != null ? languageLevel : InterlisLanguageLevel.UNKNOWN;
            List<String> candidates;
            if (lastLanguageLevel.equals(new InterlisLanguageLevel(2, 4))) {
                candidates = List.of("Model24");
            } else if (lastLanguageLevel.equals(new InterlisLanguageLevel(2, 3))) {
                candidates = List.of("Model23");
            } else {
                candidates = List.of("Model23", "Model24", "UnknownModel");
            }
            String normalizedPrefix = prefix != null ? prefix.toLowerCase(Locale.ROOT) : "";
            List<String> filtered = new ArrayList<>();
            for (String candidate : candidates) {
                if (excludeUppercase != null && excludeUppercase.contains(candidate.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                if (normalizedPrefix.isBlank() || candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                    filtered.add(candidate);
                }
            }
            return filtered;
        }
    }
}
