package ch.so.agi.lsp.interlis.model;

import ch.so.agi.lsp.interlis.server.ClientSettings;
import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.LogListener;
import ch.ehi.basics.logging.StdListener;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ilirepository.impl.ModelLister;
import ch.interlis.ilirepository.impl.ModelMetadata;
import ch.interlis.ilirepository.impl.RepositoryAccess;
import ch.interlis.ilirepository.impl.RepositoryAccessException;
import ch.interlis.ilirepository.impl.RepositoryVisitor;
import ch.so.agi.lsp.interlis.live.InterlisLanguageLevel;
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
public class ModelDiscoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(ModelDiscoveryService.class);

    private static final Object LOG_LOCK = new Object();

    private final Map<String, List<ModelMetadata>> modelCache = new ConcurrentHashMap<>();
    private final Map<String, String> displayNameCache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile String lastRepositoryKey = "";

    public void ensureInitialized(ClientSettings settings) {
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

            boolean suppressLogs = settings != null && settings.isSuppressRepositoryLogs();

            for (String repository : repositories) {
                discoverModelsFromRepository(repository, suppressLogs);
            }

            initialized = true;
            lastRepositoryKey = repositoryKey;
            LOG.debug("Model discovery initialized with {} repositories ({} models)", repositories.size(), modelCache.size());
        }
    }

    public List<String> searchModels(ClientSettings settings, String prefix, Set<String> excludeUppercase) {
        return searchModels(settings, prefix, excludeUppercase, InterlisLanguageLevel.UNKNOWN);
    }

    public List<String> searchModels(ClientSettings settings,
                                     String prefix,
                                     Set<String> excludeUppercase,
                                     InterlisLanguageLevel languageLevel) {
        ensureInitialized(settings);
        if (!initialized || modelCache.isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = prefix != null ? prefix.trim().toLowerCase(Locale.ROOT) : "";
        boolean showAll = normalized.isEmpty();
        String targetSchemaLanguage = targetSchemaLanguage(languageLevel);

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<ModelMetadata>> entry : modelCache.entrySet()) {
            String modelKey = entry.getKey();
            String modelName = displayNameCache.get(modelKey);
            if (modelName == null) {
                continue;
            }
            if (excludeUppercase != null && excludeUppercase.contains(modelKey)) {
                continue;
            }
            if (showAll || modelName.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                if (!matchesTargetSchema(entry.getValue(), targetSchemaLanguage)) {
                    continue;
                }
                result.add(modelName);
            }
        }

        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private void discoverModelsFromRepository(String repositoryUrl, boolean suppressLogs) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return;
        }

        Runnable task = () -> {
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
                        storeMetadata(metadata);
                    }
                }
            } catch (RepositoryAccessException ex) {
                LOG.warn("Failed to fetch repository {}: {}", repositoryUrl, ex.getMessage());
            }
        };

        if (suppressLogs) {
            runWithSuppressedLogs(task);
        } else {
            task.run();
        }
    }

    private static final LogListener NULL_LOG_LISTENER = event -> { };

    private static void runWithSuppressedLogs(Runnable action) {
        if (action == null) {
            return;
        }

        synchronized (LOG_LOCK) {
            StdListener std = StdListener.getInstance();
            var logger = EhiLogger.getInstance();
            try {
                std.skipInfo(true);
                std.skipState(true);
                std.skipStateTrace(true);
                std.skipUnusualStateTrace(true);
                std.skipAdaption(true);
                std.skipBackendCmd(true);
                std.skipDebugTrace(true);

                logger.addListener(NULL_LOG_LISTENER);
                logger.removeListener(std);
                action.run();
            } finally {
                logger.addListener(std);
                logger.removeListener(NULL_LOG_LISTENER);

                std.skipInfo(false);
                std.skipState(false);
                std.skipStateTrace(false);
                std.skipUnusualStateTrace(false);
                std.skipAdaption(false);
                std.skipBackendCmd(false);
                std.skipDebugTrace(false);
            }
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

    void replaceModelsForTesting(List<ModelMetadata> metadata) {
        modelCache.clear();
        displayNameCache.clear();
        if (metadata != null) {
            for (ModelMetadata item : metadata) {
                storeMetadata(item);
            }
        }
        initialized = true;
        lastRepositoryKey = buildRepositoryKey(new ClientSettings());
    }

    private void storeMetadata(ModelMetadata metadata) {
        if (metadata == null || metadata.getName() == null || metadata.getName().isBlank()) {
            return;
        }
        String key = metadata.getName().toUpperCase(Locale.ROOT);
        displayNameCache.putIfAbsent(key, metadata.getName());
        modelCache.compute(key, (ignored, existing) -> {
            List<ModelMetadata> updated = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
            updated.add(metadata);
            return updated;
        });
    }

    private static boolean matchesTargetSchema(List<ModelMetadata> variants, String targetSchemaLanguage) {
        if (variants == null || variants.isEmpty() || targetSchemaLanguage == null || targetSchemaLanguage.isBlank()) {
            return true;
        }
        for (ModelMetadata metadata : variants) {
            String schemaLanguage = normalizedSchemaLanguage(metadata);
            if (schemaLanguage == null) {
                return true;
            }
            if (schemaLanguage.equals(targetSchemaLanguage)) {
                return true;
            }
        }
        return false;
    }

    private static String targetSchemaLanguage(InterlisLanguageLevel languageLevel) {
        if (languageLevel == null || languageLevel.equals(InterlisLanguageLevel.UNKNOWN)) {
            return null;
        }
        if (languageLevel.major() == 2 && languageLevel.minor() == 3) {
            return ModelMetadata.ili2_3.toLowerCase(Locale.ROOT);
        }
        if (languageLevel.isAtLeast(2, 4)) {
            return ModelMetadata.ili2_4.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String normalizedSchemaLanguage(ModelMetadata metadata) {
        if (metadata == null || metadata.getSchemaLanguage() == null || metadata.getSchemaLanguage().isBlank()) {
            return null;
        }
        return metadata.getSchemaLanguage().trim().toLowerCase(Locale.ROOT);
    }
}
