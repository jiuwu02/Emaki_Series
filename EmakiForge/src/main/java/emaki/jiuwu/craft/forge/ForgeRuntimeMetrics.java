package emaki.jiuwu.craft.forge;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public final class ForgeRuntimeMetrics {

    private final LongAdder reloadCandidates = new LongAdder();
    private final LongAdder reloadInstalled = new LongAdder();
    private final LongAdder reloadFailed = new LongAdder();
    private final LongAdder reloadStale = new LongAdder();
    private final LongAdder guiStale = new LongAdder();
    private final LongAdder executionStale = new LongAdder();
    private final LongAdder guiSettlementFailures = new LongAdder();
    private final AtomicReference<Snapshot> last = new AtomicReference<>(Snapshot.empty());

    void recordCandidate() {
        reloadCandidates.increment();
    }

    void recordResult(ForgeReloadResult result) {
        if (result == null) {
            reloadFailed.increment();
            return;
        }
        if (result.installed()) {
            reloadInstalled.increment();
        }
        switch (result.outcome()) {
            case SUCCESS, WARNING -> {
            }
            case STALE -> reloadStale.increment();
            case FAILED, FAILED_PRESERVED, FAILED_UNAVAILABLE, CLOSED -> reloadFailed.increment();
        }
        last.set(new Snapshot(
                reloadCandidates.sum(),
                reloadInstalled.sum(),
                reloadFailed.sum(),
                reloadStale.sum(),
                guiStale.sum(),
                executionStale.sum(),
                guiSettlementFailures.sum(),
                result.runtimeGeneration(),
                result.recipes(),
                result.recipeReport().issueCount(),
                result.recipeReport().sourceStatusCount(),
                result.durationNanos()
        ));
    }

    public void recordGuiStale() {
        guiStale.increment();
    }

    public void recordExecutionStale() {
        executionStale.increment();
    }

    public void recordGuiSettlementFailure() {
        guiSettlementFailures.increment();
    }

    public Snapshot snapshot() {
        Snapshot previous = last.get();
        return new Snapshot(
                reloadCandidates.sum(),
                reloadInstalled.sum(),
                reloadFailed.sum(),
                reloadStale.sum(),
                guiStale.sum(),
                executionStale.sum(),
                guiSettlementFailures.sum(),
                previous.runtimeGeneration(),
                previous.recipes(),
                previous.issues(),
                previous.sourceStates(),
                previous.lastReloadNanos()
        );
    }

    public record Snapshot(long reloadCandidates,
            long reloadInstalled,
            long reloadFailed,
            long reloadStale,
            long guiStale,
            long executionStale,
            long guiSettlementFailures,
            long runtimeGeneration,
            int recipes,
            int issues,
            int sourceStates,
            long lastReloadNanos) {

        static Snapshot empty() {
            return new Snapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0, 0L);
        }

        public Map<String, Object> debugValues(ForgeRuntimeStatus status, ForgeGuiState guiState) {
            return Map.ofEntries(
                    Map.entry("status", status),
                    Map.entry("generation", runtimeGeneration),
                    Map.entry("gui", guiState),
                    Map.entry("recipes", recipes),
                    Map.entry("issues", issues),
                    Map.entry("source_states", sourceStates),
                    Map.entry("reload_candidates", reloadCandidates),
                    Map.entry("reload_installed", reloadInstalled),
                    Map.entry("reload_failed", reloadFailed),
                    Map.entry("reload_stale", reloadStale),
                    Map.entry("gui_stale", guiStale),
                    Map.entry("execution_stale", executionStale),
                    Map.entry("settlement_failures", guiSettlementFailures),
                    Map.entry("last_ms", String.format(Locale.ROOT, "%.3f", lastReloadNanos / 1_000_000D))
            );
        }
    }
}
