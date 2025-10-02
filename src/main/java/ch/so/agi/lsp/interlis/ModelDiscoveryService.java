package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ilirepository.impl.ModelLister;
import ch.interlis.ilirepository.impl.ModelMetadata;
import ch.interlis.ilirepository.impl.RepositoryAccess;
import ch.interlis.ilirepository.impl.RepositoryAccessException;
import ch.interlis.ilirepository.impl.RepositoryVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Discovers available INTERLIS models from configured repositories so completion can offer
 * suggestions in IMPORTS clauses.
 */
final class ModelDiscoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(ModelDiscoveryService.class);

    private final Map<String, ModelMetadata> modelCache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile String lastRepositoryKey = "";

    void ensureInitialized(ClientSettings settings) {
        String repositoryKey = buildRepositoryKey(settings);
        if (initialized && repositoryKey.equals(lastRepositoryKey)) {
            return;
        }

        synchronized (this) {
            repositoryKey = buildRepositoryKey(settings);
            if (initialized && repositoryKey.equals(lastRepositoryKey)) {
                return;
            }

            List<String> repositories = parseRepositories(settings);
            modelCache.clear();

            for (String repository : repositories) {
                discoverModelsFromRepository(repository);
            }

            initialized = true;
            lastRepositoryKey = repositoryKey;
            LOG.debug("Model discovery initialized with {} repositories ({} models)", repositories.size(), modelCache.size());
        }
    }

    List<String> searchModels(ClientSettings settings, String prefix, Set<String> excludeUppercase) {
        ensureInitialized(settings);
        if (!initialized || modelCache.isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = prefix != null ? prefix.trim().toLowerCase(Locale.ROOT) : "";
        boolean showAll = normalized.isEmpty();

        List<String> result = new ArrayList<>();
        for (String modelName : modelCache.keySet()) {
            if (modelName == null) {
                continue;
            }
            if (excludeUppercase != null && excludeUppercase.contains(modelName.toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (showAll || modelName.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(modelName);
            }
        }

        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private void discoverModelsFromRepository(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return;
        }

        RepositoryAccess repoAccess = new RepositoryAccess();
        ModelLister modelLister = new ModelLister();
        modelLister.setIgnoreDuplicates(true);

        try {
            RepositoryVisitor visitor = new RepositoryVisitor(repoAccess, modelLister);
            visitor.setRepositories(new String[]{repositoryUrl});
            visitor.visitRepositories();

            List<ModelMetadata> merged = modelLister.getResult2();
            List<ModelMetadata> latest = RepositoryAccess.getLatestVersions2(merged);

            for (ModelMetadata metadata : latest) {
                if (metadata != null && metadata.getName() != null) {
                    modelCache.put(metadata.getName(), metadata);
                }
            }
        } catch (RepositoryAccessException ex) {
            LOG.warn("Failed to fetch repository {}: {}", repositoryUrl, ex.getMessage());
        }
    }

    private static List<String> parseRepositories(ClientSettings settings) {
        String repos = settings != null ? settings.getModelRepositories() : null;
        if (repos == null || repos.isBlank()) {
            repos = Ili2cSettings.DEFAULT_ILIDIRS;
        }
        String[] parts = repos.split("[;,]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.equalsIgnoreCase("%ILI_DIR") || value.equalsIgnoreCase("%JAR_DIR")) {
                continue;
            }
            result.add(value);
        }
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    private static String buildRepositoryKey(ClientSettings settings) {
        List<String> repos = parseRepositories(settings);
        return String.join(";", repos);
    }
}
