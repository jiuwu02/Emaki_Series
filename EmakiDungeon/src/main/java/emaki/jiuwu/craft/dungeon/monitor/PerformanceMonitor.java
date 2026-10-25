package emaki.jiuwu.craft.dungeon.monitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import java.util.Locale;
import java.util.Set;

public final class PerformanceMonitor implements AutoCloseable {

    private static final long PROBE_PERIOD_TICKS = 20L;
    private static final int MSPT_WINDOW = 60;
    private static final long MSPT_ALERT_COOLDOWN_MS = 60_000L;
    private static final long MEMORY_WINDOW_MS = 300_000L;
    private static final long MEMORY_ALERT_COOLDOWN_MS = 600_000L;
    private static final long BACKLOG_ALERT_COOLDOWN_MS = 60_000L;

    private final Plugin plugin;
    private final InstanceRegistry registry;
    private final MessageService messages;
    private final AuditService audit;
    private final Supplier<AppConfig> config;

    private final ArrayDeque<Double> msptSamples = new ArrayDeque<>();
    private final Map<String, Long> backlogSince = new HashMap<>();
    private final Map<String, Long> backlogLastAlert = new HashMap<>();

    private volatile TaskToken probeTask = TaskToken.UNAVAILABLE;
    private long memoryAnchorAt;
    private long memoryAnchorUsed;
    private long lastMsptAlert;
    private long lastMemoryAlert;

    public PerformanceMonitor(Plugin plugin,
            ExecutionDispatcher dispatcher,
            InstanceRegistry registry,
            MessageService messages,
            AuditService audit,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.registry = registry;
        this.messages = messages;
        this.audit = audit;
        this.config = config;
        this.memoryAnchorAt = System.currentTimeMillis();
        this.memoryAnchorUsed = usedHeapBytes();
        this.probeTask = dispatcher.runGlobalTimer(plugin, this::probe,
                PROBE_PERIOD_TICKS, PROBE_PERIOD_TICKS);
    }

    private void probe() {
        probeMspt();
        probeMemorySlope();
        probeQueueBacklog();
    }

    private void probeMspt() {
        int threshold = config.get().monitorMsptP95Ms();
        if (threshold <= 0) {
            return;
        }
        double[] tps = Bukkit.getTPS();
        if (tps.length == 0 || tps[0] <= 0.0D) {
            return;
        }
        double sample = 1000.0D / Math.min(tps[0], 20.0D);
        msptSamples.addLast(sample);
        while (msptSamples.size() > MSPT_WINDOW) {
            msptSamples.removeFirst();
        }
        double p95 = percentile(new ArrayList<>(msptSamples), 0.95D);
        long now = System.currentTimeMillis();
        if (p95 <= threshold || now - lastMsptAlert < MSPT_ALERT_COOLDOWN_MS) {
            return;
        }
        lastMsptAlert = now;
        Map<String, String> fields = Map.of(
                "p95", String.format(Locale.ROOT, "%.1f", p95),
                "threshold", String.valueOf(threshold));
        messages.warning("console.monitor_mspt_high", fields);
        audit.write("monitor", merge("kind", "mspt_p95", fields));
    }

    private void probeMemorySlope() {
        int threshold = config.get().monitorMemorySlopeMbPerMin();
        if (threshold <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - memoryAnchorAt;
        if (elapsed < MEMORY_WINDOW_MS) {
            return;
        }
        long used = usedHeapBytes();
        double slopeMbPerMin = (used - memoryAnchorUsed) / 1024.0D / 1024.0D
                / (elapsed / 60_000.0D);
        memoryAnchorAt = now;
        memoryAnchorUsed = used;
        if (slopeMbPerMin <= threshold || now - lastMemoryAlert < MEMORY_ALERT_COOLDOWN_MS) {
            return;
        }
        lastMemoryAlert = now;
        Map<String, String> fields = Map.of(
                "slope", String.format(Locale.ROOT, "%.1f", slopeMbPerMin),
                "threshold", String.valueOf(threshold));
        messages.warning("console.monitor_memory_slope", fields);
        audit.write("monitor", merge("kind", "memory_slope", fields));
    }

    private void probeQueueBacklog() {
        int threshold = config.get().monitorQueueBacklogSeconds();
        if (threshold <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (DungeonInstance instance : registry.all()) {
            WorldCommandQueue queue = instance.commands();
            if (queue == null || queue.pending() <= 0) {
                continue;
            }
            String instanceId = instance.instanceId();
            seen.add(instanceId);
            backlogSince.putIfAbsent(instanceId, now);
            long since = backlogSince.get(instanceId);
            if (now - since < threshold * 1000L) {
                continue;
            }
            Long lastAlert = backlogLastAlert.get(instanceId);
            if (lastAlert != null && now - lastAlert < BACKLOG_ALERT_COOLDOWN_MS) {
                continue;
            }
            backlogLastAlert.put(instanceId, now);
            long seconds = (now - since) / 1000L;
            Map<String, String> fields = Map.of(
                    "instance", instanceId,
                    "seconds", String.valueOf(seconds),
                    "threshold", String.valueOf(threshold));
            messages.warning("console.monitor_queue_backlog", fields);
            audit.write("monitor", merge("kind", "queue_backlog", fields));
        }
        backlogSince.keySet().removeIf(instanceId -> !seen.contains(instanceId));
        backlogLastAlert.keySet().removeIf(instanceId -> !seen.contains(instanceId));
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double percentile(List<Double> samples, double ratio) {
        if (samples.isEmpty()) {
            return 0.0D;
        }
        Collections.sort(samples);
        int index = (int) Math.ceil(ratio * samples.size()) - 1;
        return samples.get(Math.max(0, Math.min(index, samples.size() - 1)));
    }

    private static Map<String, String> merge(String key, String value, Map<String, String> fields) {
        Map<String, String> merged = new HashMap<>(fields);
        merged.put(key, value);
        return merged;
    }

    @Override
    public void close() {
        TaskToken current = probeTask;
        probeTask = TaskToken.UNAVAILABLE;
        if (current != null) {
            current.cancel();
        }
        synchronized (this) {
            backlogSince.clear();
            backlogLastAlert.clear();
            msptSamples.clear();
        }
    }
}
