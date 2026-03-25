package ch.so.agi.lsp.interlis.live;

import ch.interlis.ili2c.metamodel.TransferDescription;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class LiveAnalysisService {
    private static final long DEFAULT_DEBOUNCE_MILLIS = 150L;

    private final InterlisLiveAnalyzer analyzer = new InterlisLiveAnalyzer();
    private final Map<String, LiveParseResult> results = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "interlis-live-analysis");
        thread.setDaemon(true);
        return thread;
    });
    private final long debounceMillis;

    public LiveAnalysisService() {
        this(DEFAULT_DEBOUNCE_MILLIS);
    }

    LiveAnalysisService(long debounceMillis) {
        this.debounceMillis = debounceMillis;
    }

    public LiveParseResult analyze(DocumentSnapshot snapshot) {
        return analyze(snapshot, null);
    }

    public LiveParseResult analyze(DocumentSnapshot snapshot, TransferDescription authoritativeTd) {
        if (snapshot == null || snapshot.uri() == null) {
            return null;
        }
        LiveParseResult cached = results.get(snapshot.uri());
        if (isCurrent(cached, snapshot, authoritativeTd != null)) {
            return cached;
        }
        LiveParseResult result = analyzer.analyze(snapshot, authoritativeTd);
        results.put(snapshot.uri(), result);
        return result;
    }

    public void schedule(DocumentSnapshot snapshot, Consumer<LiveParseResult> onResult) {
        schedule(snapshot, null, onResult);
    }

    public void schedule(DocumentSnapshot snapshot,
                         TransferDescription authoritativeTd,
                         Consumer<LiveParseResult> onResult) {
        if (snapshot == null || snapshot.uri() == null) {
            return;
        }
        ScheduledFuture<?> existing = pendingTasks.remove(snapshot.uri());
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                LiveParseResult result = analyze(snapshot, authoritativeTd);
                if (onResult != null) {
                    onResult.accept(result);
                }
            } finally {
                pendingTasks.remove(snapshot.uri());
            }
        }, debounceMillis, TimeUnit.MILLISECONDS);
        pendingTasks.put(snapshot.uri(), future);
    }

    public LiveParseResult cached(String uri) {
        return uri != null ? results.get(uri) : null;
    }

    public void remove(String uri) {
        if (uri == null) {
            return;
        }
        ScheduledFuture<?> existing = pendingTasks.remove(uri);
        if (existing != null) {
            existing.cancel(false);
        }
        results.remove(uri);
    }

    private static boolean isCurrent(LiveParseResult result,
                                     DocumentSnapshot snapshot,
                                     boolean requireAuthoritativeFallback) {
        if (result == null || result.snapshot() == null) {
            return false;
        }
        if (requireAuthoritativeFallback && !result.authoritativeFallbackEnabled()) {
            return false;
        }
        if (snapshot.version() != null && result.snapshot().version() != null) {
            return snapshot.version().equals(result.snapshot().version());
        }
        return snapshot.text().equals(result.snapshot().text());
    }
}
