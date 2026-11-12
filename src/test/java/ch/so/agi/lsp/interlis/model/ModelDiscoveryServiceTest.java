package ch.so.agi.lsp.interlis.model;

import ch.interlis.ilirepository.impl.ModelMetadata;
import ch.so.agi.lsp.interlis.live.InterlisLanguageLevel;
import ch.so.agi.lsp.interlis.server.ClientSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import ch.ehi.basics.logging.EhiLogger;
import ch.so.agi.lsp.interlis.compiler.NetworkProblems;
import java.util.ArrayList;

class ModelDiscoveryServiceTest {

    @Test
    void searchModelsFiltersByInterlis24WhenMetadataIsKnown() {
        ModelDiscoveryService service = seededService(
                metadata("Base23", ModelMetadata.ili2_3),
                metadata("Base24", ModelMetadata.ili2_4),
                metadata("UnknownVersion", null));

        List<String> results = service.searchModels(new ClientSettings(), "", Set.of(),
                new InterlisLanguageLevel(2, 4));

        assertEquals(List.of("Base24", "UnknownVersion"), results);
    }

    @Test
    void searchModelsFiltersByInterlis23WhenMetadataIsKnown() {
        ModelDiscoveryService service = seededService(
                metadata("Base23", ModelMetadata.ili2_3),
                metadata("Base24", ModelMetadata.ili2_4),
                metadata("UnknownVersion", null));

        List<String> results = service.searchModels(new ClientSettings(), "", Set.of(),
                new InterlisLanguageLevel(2, 3));

        assertEquals(List.of("Base23", "UnknownVersion"), results);
    }

    @Test
    void searchModelsDoesNotFilterWhenDocumentVersionIsUnknown() {
        ModelDiscoveryService service = seededService(
                metadata("Base23", ModelMetadata.ili2_3),
                metadata("Base24", ModelMetadata.ili2_4));

        List<String> results = service.searchModels(new ClientSettings(), "", Set.of(),
                InterlisLanguageLevel.UNKNOWN);

        assertEquals(List.of("Base23", "Base24"), results);
    }

    @Test
    void searchModelsPreservesModelNameWhenMultipleSchemaVariantsExist() {
        ModelDiscoveryService service = seededService(
                metadata("SharedModel", ModelMetadata.ili2_3),
                metadata("SharedModel", ModelMetadata.ili2_4),
                metadata("OtherModel", ModelMetadata.ili2_4));

        List<String> results24 = service.searchModels(new ClientSettings(), "", Set.of(),
                new InterlisLanguageLevel(2, 4));
        List<String> results23 = service.searchModels(new ClientSettings(), "", Set.of(),
                new InterlisLanguageLevel(2, 3));

        assertEquals(List.of("OtherModel", "SharedModel"), results24);
        assertEquals(List.of("SharedModel"), results23);
    }

    @Test
    void reportsSuppressedRepositoryFailuresOncePerInitialization() {
        var reports = new ArrayList<List<NetworkProblems.Problem>>();
        ModelDiscoveryService service = new ModelDiscoveryService(reports::add) {
            @Override List<ModelMetadata> loadRepository(String repository) {
                EhiLogger.logAdaption("repository <" + repository + "> ignored; java.net.UnknownHostException: repo.example");
                EhiLogger.logAdaption("repository <" + repository + "> ignored; java.net.UnknownHostException: repo.example");
                return List.of(metadata("LocalModel", ModelMetadata.ili2_4));
            }
        };
        ClientSettings settings = new ClientSettings();
        settings.setModelRepositories("https://repo.example/");
        settings.setSuppressRepositoryLogs(true);
        assertEquals(List.of("LocalModel"), service.searchModels(settings, "", Set.of()));
        assertEquals(1, reports.size());
        assertEquals(1, reports.get(0).size());
        assertEquals(NetworkProblems.Kind.DNS, reports.get(0).get(0).kind());
        service.ensureInitialized(settings);
        assertEquals(1, reports.size());
        settings.setModelRepositories("https://other.example/");
        service.ensureInitialized(settings);
        assertEquals(2, reports.size());
    }

    private static ModelDiscoveryService seededService(ModelMetadata... metadata) {
        ModelDiscoveryService service = new ModelDiscoveryService();
        service.replaceModelsForTesting(List.of(metadata));
        return service;
    }

    private static ModelMetadata metadata(String name, String schemaLanguage) {
        ModelMetadata metadata = new ModelMetadata();
        metadata.setName(name);
        metadata.setSchemaLanguage(schemaLanguage);
        return metadata;
    }
}
