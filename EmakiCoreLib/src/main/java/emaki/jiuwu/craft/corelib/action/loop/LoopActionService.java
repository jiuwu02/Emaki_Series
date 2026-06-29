package emaki.jiuwu.craft.corelib.action.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class LoopActionService implements Listener {

    private final Plugin owner;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, LoopTaskRecord> tasks = new ConcurrentHashMap<>();
    private final ActionLineParser lineParser = new ActionLineParser();
    private volatile CoreLibConfig.LoopConfig config = CoreLibConfig.LoopConfig.defaults();
    private volatile ActionTemplateRegistry templateRegistry;
    private volatile ActionRegistry actionRegistry;
    private volatile Supplier<ActionExecutor> executorSupplier;

    public LoopActionService(Plugin owner) {
        this.owner = owner;
    }

    public void configure(CoreLibConfig.LoopConfig config,
            ActionTemplateRegistry templateRegistry,
            ActionRegistry actionRegistry,
            Supplier<ActionExecutor> executorSupplier) {
        this.config = config == null ? CoreLibConfig.LoopConfig.defaults() : config;
        this.templateRegistry = templateRegistry;
        this.actionRegistry = actionRegistry;
        this.executorSupplier = executorSupplier;
    }

    public ActionResult start(ActionContext context, Map<String, String> arguments, boolean async) {
        CoreLibConfig.LoopConfig loopConfig = config == null ? CoreLibConfig.LoopConfig.defaults() : config;
        if (!loopConfig.enabled()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Loop actions are disabled.");
        }
        String template = Texts.trim(arguments.get("template"));
        List<String> lines = templateRegistry == null ? null : templateRegistry.get(template);
        if (lines == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Loop template does not exist: " + template);
        }
        int times = parseInt(arguments.get("times"), -1);
        if (times <= 0 || times > loopConfig.maxTimes()) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Loop times must be between 1 and " + loopConfig.maxTimes() + ".");
        }
        long intervalTicks = parseTicks(arguments.get("interval"), -1L);
        long minInterval = async ? loopConfig.minAsyncIntervalTicks() : loopConfig.minSyncIntervalTicks();
        if (intervalTicks < minInterval) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Loop interval is lower than the configured minimum.");
        }
        long initialDelayTicks = parseTicks(arguments.get("initial_delay"), 0L);
        if (initialDelayTicks < 0L) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Loop initial_delay is invalid.");
        }
        if (async && !isAsyncSafeTemplate(lines)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "loopasync template contains non-async-safe actions.");
        }
        String key = Texts.trim(arguments.get("key"));
        if (Texts.isBlank(key)) {
            key = "loop:" + owner.getName() + ":" + sequence.incrementAndGet();
        }
        LoopTaskMode mode = LoopTaskMode.parse(arguments.get("mode"));
        if (mode != LoopTaskMode.ALLOW_DUPLICATE) {
            LoopTaskRecord existing = tasks.get(key);
            if (existing != null) {
                if (mode == LoopTaskMode.IGNORE) {
                    return ActionResult.ok();
                }
                existing.cancel();
                tasks.remove(key);
            }
        } else {
            key = key + "#" + sequence.incrementAndGet();
        }
        ActionResult limit = checkLimits(context, key, loopConfig);
        if (!limit.success()) {
            return limit;
        }
        LoopTaskRecord record = new LoopTaskRecord(
                String.valueOf(sequence.incrementAndGet()),
                key,
                template,
                lines,
                context,
                async,
                times,
                intervalTicks,
                parseBoolean(arguments.get("stop_if_offline"), true),
                parseBoolean(arguments.get("stop_if_dead"), false),
                Texts.toStringSafe(arguments.get("stop_if_condition")),
                parseBoolean(arguments.get("stop_on_failure"), false),
                dynamicArguments(arguments)
        );
        tasks.put(record.key, record);
        schedule(record, initialDelayTicks);
        return ActionResult.ok();
    }

    public ActionResult cancel(String key, String match, boolean silent) {
        if (Texts.isBlank(key)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Loop key cannot be blank.");
        }
        int cancelled = 0;
        boolean prefix = "prefix".equalsIgnoreCase(Texts.trim(match));
        for (LoopTaskRecord record : List.copyOf(tasks.values())) {
            boolean matches = prefix ? record.key.startsWith(key) : record.key.equals(key);
            if (matches) {
                record.cancel();
                tasks.remove(record.key);
                cancelled++;
            }
        }
        if (cancelled == 0 && !silent) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "No loop task matched key: " + key);
        }
        return ActionResult.ok();
    }

    public int cancelByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        int cancelled = 0;
        for (LoopTaskRecord record : List.copyOf(tasks.values())) {
            Player player = record.context.player();
            if (player != null && playerUuid.equals(player.getUniqueId())) {
                record.cancel();
                tasks.remove(record.key);
                cancelled++;
            }
        }
        return cancelled;
    }

    public int cancelAll() {
        int size = tasks.size();
        for (LoopTaskRecord record : List.copyOf(tasks.values())) {
            record.cancel();
        }
        tasks.clear();
        return size;
    }

    public List<LoopTaskSnapshot> snapshots() {
        List<LoopTaskSnapshot> snapshots = new ArrayList<>();
        for (LoopTaskRecord record : tasks.values()) {
            snapshots.add(record.snapshot());
        }
        return List.copyOf(snapshots);
    }

    public List<LoopTaskSnapshot> snapshotsByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }
        List<LoopTaskSnapshot> snapshots = new ArrayList<>();
        for (LoopTaskRecord record : tasks.values()) {
            Player player = record.context.player();
            if (player != null && playerUuid.equals(player.getUniqueId())) {
                snapshots.add(record.snapshot());
            }
        }
        return List.copyOf(snapshots);
    }

    public List<LoopTaskSnapshot> snapshotsByKey(String key) {
        if (Texts.isBlank(key)) {
            return List.of();
        }
        List<LoopTaskSnapshot> snapshots = new ArrayList<>();
        for (LoopTaskRecord record : tasks.values()) {
            if (record.key.equals(key)) {
                snapshots.add(record.snapshot());
            }
        }
        return List.copyOf(snapshots);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        CoreLibConfig.LoopConfig loopConfig = config == null ? CoreLibConfig.LoopConfig.defaults() : config;
        if (loopConfig.cancelPlayerLoopsOnQuit()) {
            cancelByPlayer(event.getPlayer().getUniqueId());
        }
    }

    private void schedule(LoopTaskRecord record, long delayTicks) {
        Runnable task = () -> tick(record);
        TaskHandle handle = record.async
                ? FoliaSchedulerAdapter.runAsyncLater(owner, task, Math.max(0L, delayTicks) * 50L, TimeUnit.MILLISECONDS)
                : FoliaSchedulerAdapter.runTaskLater(owner, task, delayTicks);
        record.handle = handle;
    }

    private void tick(LoopTaskRecord record) {
        if (!tasks.containsKey(record.key) || record.cancelled) {
            return;
        }
        if (shouldStop(record)) {
            remove(record);
            return;
        }
        int nextIndex = record.index + 1;
        ActionContext loopContext = record.context.withPlaceholders(Map.of(
                "loop_id", record.id,
                "loop_key", record.key,
                "loop_index", String.valueOf(nextIndex),
                "loop_times", String.valueOf(record.times),
                "loop_remaining", String.valueOf(Math.max(0, record.times - nextIndex)),
                "loop_interval", record.intervalTicks + "t"
        )).withPlaceholders(record.withArguments);
        ActionExecutor executor = executorSupplier == null ? null : executorSupplier.get();
        if (executor == null) {
            remove(record);
            return;
        }
        executor.executeAll(loopContext, record.lines, record.stopOnFailure).whenComplete((batch, throwable) -> {
            boolean failed = throwable != null || (batch != null && !batch.success());
            record.index = nextIndex;
            if (record.cancelled || !tasks.containsKey(record.key)) {
                return;
            }
            if (failed && record.stopOnFailure) {
                remove(record);
                return;
            }
            if (record.index >= record.times) {
                remove(record);
                return;
            }
            schedule(record, record.intervalTicks);
        });
    }

    private boolean shouldStop(LoopTaskRecord record) {
        Player player = record.context.player();
        if (record.stopIfOffline && (player == null || !player.isOnline())) {
            return true;
        }
        if (record.stopIfDead && player != null && player.isDead()) {
            return true;
        }
        if (Texts.isNotBlank(record.stopIfCondition)) {
            Boolean passes = ConditionEvaluator.evaluateSingle(renderCondition(record), value -> Texts.toStringSafe(value));
            return passes == null || !passes;
        }
        return false;
    }

    private void remove(LoopTaskRecord record) {
        record.cancel();
        tasks.remove(record.key);
    }

    private String renderCondition(LoopTaskRecord record) {
        String rendered = record.stopIfCondition;
        for (Map.Entry<String, String> entry : record.context.placeholders().entrySet()) {
            rendered = rendered.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return rendered;
    }

    private ActionResult checkLimits(ActionContext context, String key, CoreLibConfig.LoopConfig loopConfig) {
        if (tasks.size() >= loopConfig.maxActiveLoopsTotal()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Active loop total limit reached.");
        }
        Player player = context.player();
        String pluginName = context.sourcePlugin() == null ? owner.getName() : context.sourcePlugin().getName();
        int playerLoops = 0;
        int pluginLoops = 0;
        for (LoopTaskRecord record : tasks.values()) {
            Player recordPlayer = record.context.player();
            if (player != null && recordPlayer != null && player.getUniqueId().equals(recordPlayer.getUniqueId())) {
                playerLoops++;
            }
            String recordPlugin = record.context.sourcePlugin() == null ? owner.getName() : record.context.sourcePlugin().getName();
            if (pluginName.equals(recordPlugin)) {
                pluginLoops++;
            }
        }
        if (player != null && playerLoops >= loopConfig.maxActiveLoopsPerPlayer()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Active loop per-player limit reached.");
        }
        if (pluginLoops >= loopConfig.maxActiveLoopsPerPlugin()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Active loop per-plugin limit reached.");
        }
        return ActionResult.ok();
    }

    private boolean isAsyncSafeTemplate(List<String> lines) {
        if (actionRegistry == null) {
            return false;
        }
        for (int index = 0; index < lines.size(); index++) {
            try {
                ParsedActionLine parsed = lineParser.parse(index + 1, lines.get(index));
                if (parsed == null) {
                    continue;
                }
                if ("usetemplate".equals(parsed.actionId())) {
                    return false;
                }
                Action action = actionRegistry.get(parsed.actionId());
                if (action == null || action.executionMode() != ActionExecutionMode.ASYNC_IO) {
                    return false;
                }
            } catch (ActionSyntaxException exception) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> dynamicArguments(Map<String, String> arguments) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (entry.getKey().startsWith("with.")) {
                values.put(entry.getKey().substring("with.".length()), entry.getValue());
            }
        }
        return Map.copyOf(values);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Texts.isBlank(raw) ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long parseTicks(String raw, long fallback) {
        long parsed = emaki.jiuwu.craft.corelib.action.ActionParsers.parseTicks(raw);
        return parsed < 0L ? fallback : parsed;
    }

    private boolean parseBoolean(String raw, boolean fallback) {
        Boolean parsed = emaki.jiuwu.craft.corelib.action.ActionParsers.parseBoolean(raw);
        return parsed == null ? fallback : parsed;
    }

    private final class LoopTaskRecord {

        private final String id;
        private final String key;
        private final String template;
        private final List<String> lines;
        private final ActionContext context;
        private final boolean async;
        private final int times;
        private final long intervalTicks;
        private final boolean stopIfOffline;
        private final boolean stopIfDead;
        private final String stopIfCondition;
        private final boolean stopOnFailure;
        private final Map<String, String> withArguments;
        private volatile int index;
        private volatile boolean cancelled;
        private volatile TaskHandle handle;

        private LoopTaskRecord(String id,
                String key,
                String template,
                List<String> lines,
                ActionContext context,
                boolean async,
                int times,
                long intervalTicks,
                boolean stopIfOffline,
                boolean stopIfDead,
                String stopIfCondition,
                boolean stopOnFailure,
                Map<String, String> withArguments) {
            this.id = id;
            this.key = key;
            this.template = template;
            this.lines = List.copyOf(lines);
            this.context = context;
            this.async = async;
            this.times = times;
            this.intervalTicks = intervalTicks;
            this.stopIfOffline = stopIfOffline;
            this.stopIfDead = stopIfDead;
            this.stopIfCondition = stopIfCondition;
            this.stopOnFailure = stopOnFailure;
            this.withArguments = withArguments == null ? Map.of() : Map.copyOf(withArguments);
        }

        private void cancel() {
            cancelled = true;
            FoliaSchedulerAdapter.cancelTask(handle);
        }

        private LoopTaskSnapshot snapshot() {
            Player player = context.player();
            String plugin = context.sourcePlugin() == null ? owner.getName() : context.sourcePlugin().getName();
            return new LoopTaskSnapshot(id, key, template, plugin, player == null ? null : player.getUniqueId(), async, index, times, intervalTicks);
        }
    }
}
