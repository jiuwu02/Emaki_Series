package emaki.jiuwu.craft.strengthen.enhancement.pity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class PityPersistenceRetryScheduler {

    private static final String DEBUG_MODULE = "pity";

    private final EmakiStrengthenPlugin plugin;
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile TaskToken token;

    public PityPersistenceRetryScheduler(@NotNull EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        stop();
        InMemoryPityStateStore store = plugin.pityStateStore();
        if (store == null || !store.persistent()) {
            return;
        }
        long interval = plugin.appConfig() == null ? 0L : plugin.appConfig().enhancementPityRetryIntervalTicks();
        int maxAttempts = plugin.appConfig() == null ? 0 : plugin.appConfig().enhancementPityRetryMaxAttempts();
        if (interval <= 0L || maxAttempts <= 0) {
            return;
        }
        ExecutionDispatcher dispatcher = plugin.executionDispatcher();
        if (dispatcher == null) {
            return;
        }
        attempts.set(0);
        token = dispatcher.runGlobalTimer(plugin, this::retryPendingFlush, interval, interval);
    }

    public synchronized void stop() {
        TaskToken current = token;
        token = null;
        attempts.set(0);
        if (current != null) {
            current.cancel();
        }
    }

    public boolean running() {
        TaskToken current = token;
        return current != null && !current.cancelled();
    }

    public int attemptCount() {
        return attempts.get();
    }

    private void retryPendingFlush() {
        InMemoryPityStateStore store = plugin.pityStateStore();
        if (store == null) {
            stop();
            return;
        }
        if (!store.isDirty()) {
            attempts.set(0);
            return;
        }
        int attempt = attempts.incrementAndGet();
        boolean flushed = store.flushToDisk();
        debug(Map.of(
                "attempt", attempt,
                "records", store.size(),
                "outcome", flushed ? "flushed" : "failed"));
        if (flushed) {
            attempts.set(0);
            return;
        }
        int maxAttempts = plugin.appConfig() == null ? 0 : plugin.appConfig().enhancementPityRetryMaxAttempts();
        if (attempt >= maxAttempts) {
            warn("保底状态落盘连续失败 " + attempt + " 次，已停止重试，等待下一次生命周期 flush");
            stop();
        }
    }

    private void debug(Map<String, ?> replacements) {
        if (plugin.debugLogger() == null) {
            return;
        }
        plugin.debugLogger().log(DEBUG_MODULE, (Player) null, "debug.pity.retry_flush", replacements);
    }

    private void warn(@Nullable String message) {
        if (plugin.getLogger() != null) {
            plugin.getLogger().warning(message);
        }
    }
}
