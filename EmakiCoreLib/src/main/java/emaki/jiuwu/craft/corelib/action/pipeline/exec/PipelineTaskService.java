package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class PipelineTaskService implements Listener {

    private final Plugin owner;
    private final ExecutionDispatcher dispatcher;
    private final PipelineRunner runner;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private volatile Limits limits = Limits.defaults();

    public PipelineTaskService(@NotNull Plugin owner,
            @NotNull ExecutionDispatcher dispatcher,
            @NotNull PipelineRunner runner) {
        this.owner = owner;
        this.dispatcher = dispatcher;
        this.runner = runner;
    }

    public void configure(@Nullable Limits newLimits) {
        this.limits = newLimits == null ? Limits.defaults() : newLimits;
    }

    public @NotNull Result start(@NotNull Request request) {
        Limits active = limits;
        if (request.body().isEmpty()) {
            return Result.rejected("action.task.empty_body");
        }
        if (request.times() <= 0 || request.times() > active.maxTimes()) {
            return Result.rejected("action.task.times_out_of_range");
        }
        if (request.intervalTicks() < active.minIntervalTicks()) {
            return Result.rejected("action.task.interval_too_small");
        }
        String key = Texts.isBlank(request.key())
                ? "task:" + owner.getName() + ":" + sequence.incrementAndGet()
                : request.key().trim();
        switch (request.conflict()) {
            case REPLACE -> {
                Task existing = tasks.remove(key);
                if (existing != null) {
                    existing.cancel();
                }
            }
            case IGNORE -> {
                if (tasks.containsKey(key)) {
                    return Result.started(key);
                }
            }
            case ALLOW_DUPLICATE -> key = key + "#" + sequence.incrementAndGet();
        }
        String rejection = checkCeilings(request, active);
        if (rejection != null) {
            return Result.rejected(rejection);
        }
        Task task = new Task(String.valueOf(sequence.incrementAndGet()), key, request);
        tasks.put(key, task);
        schedule(task, request.initialDelayTicks());
        return Result.started(key);
    }

    public int stop(@Nullable String key, boolean prefix) {
        if (Texts.isBlank(key)) {
            return 0;
        }
        String target = key.trim();
        int cancelled = 0;
        for (Task task : List.copyOf(tasks.values())) {
            boolean matches = prefix ? task.key.startsWith(target) : task.key.equals(target);
            if (matches) {
                task.cancel();
                tasks.remove(task.key);
                cancelled++;
            }
        }
        return cancelled;
    }

    public int stopByPlayer(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        int cancelled = 0;
        for (Task task : List.copyOf(tasks.values())) {
            Player player = task.player();
            if (player != null && playerUuid.equals(player.getUniqueId())) {
                task.cancel();
                tasks.remove(task.key);
                cancelled++;
            }
        }
        return cancelled;
    }

    public int stopAll() {
        int size = tasks.size();
        for (Task task : List.copyOf(tasks.values())) {
            task.cancel();
        }
        tasks.clear();
        return size;
    }

    public int activeCount() {
        return tasks.size();
    }

    public @NotNull List<TaskSnapshot> snapshots() {
        return snapshotsMatching(_ -> true);
    }

    public @NotNull List<TaskSnapshot> snapshotsByPlayer(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }
        return snapshotsMatching(task -> {
            Player player = task.player();
            return player != null && playerUuid.equals(player.getUniqueId());
        });
    }

    public @NotNull List<TaskSnapshot> snapshotsByKey(@Nullable String keyPrefix) {
        if (Texts.isBlank(keyPrefix)) {
            return List.of();
        }
        String target = keyPrefix.trim();
        return snapshotsMatching(task -> task.key.startsWith(target));
    }

    private List<TaskSnapshot> snapshotsMatching(Predicate<Task> filter) {
        List<TaskSnapshot> matches = new ArrayList<>();
        for (Task task : tasks.values()) {
            if (filter.test(task)) {
                matches.add(task.snapshot());
            }
        }
        matches.sort(Comparator.comparing(TaskSnapshot::key));
        return List.copyOf(matches);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (limits.cancelOnQuit()) {
            stopByPlayer(event.getPlayer().getUniqueId());
        }
    }

    private String checkCeilings(Request request, Limits active) {
        if (tasks.size() >= active.maxTotal()) {
            return "action.task.total_limit_reached";
        }
        Player player = playerOf(request.context());
        String pluginName = request.context().sourcePlugin() == null
                ? owner.getName()
                : request.context().sourcePlugin().getName();
        int perPlayer = 0;
        int perPlugin = 0;
        for (Task task : tasks.values()) {
            Player taskPlayer = task.player();
            if (player != null && taskPlayer != null
                    && player.getUniqueId().equals(taskPlayer.getUniqueId())) {
                perPlayer++;
            }
            Plugin source = task.request.context().sourcePlugin();
            if (pluginName.equals(source == null ? owner.getName() : source.getName())) {
                perPlugin++;
            }
        }
        if (player != null && perPlayer >= active.maxPerPlayer()) {
            return "action.task.player_limit_reached";
        }
        return perPlugin >= active.maxPerPlugin() ? "action.task.plugin_limit_reached" : null;
    }

    private void schedule(Task task, long delayTicks) {
        Runnable body = () -> tick(task);
        Player player = task.player();
        TaskToken handle;
        try {
            handle = player == null
                    ? dispatcher.runGlobalLater(owner, body, delayTicks)
                    : dispatcher.runEntityLater(owner, player, body, () -> remove(task), delayTicks);
        } catch (RuntimeException | LinkageError exception) {
            remove(task);
            return;
        }
        task.handle = handle;
        if (handle == null) {
            remove(task);
        }
    }

    private void tick(Task task) {
        if (!tasks.containsKey(task.key) || task.cancelled) {
            return;
        }
        if (shouldStop(task)) {
            remove(task);
            return;
        }
        int nextIndex = task.index + 1;
        PipelineContext context = task.request.context()
                .withVariables(loopVariables(task, nextIndex))
                .withVariables(task.request.parameters());
        runner.run(owner, task.request.body(), context, task.request.stopOnFailure())
                .whenComplete((success, throwable) -> {
                    task.index = nextIndex;
                    if (task.cancelled || !tasks.containsKey(task.key)) {
                        return;
                    }
                    boolean failed = throwable != null || Boolean.FALSE.equals(success);
                    if (failed && task.request.stopOnFailure()) {
                        remove(task);
                        return;
                    }
                    if (task.index >= task.request.times()) {
                        remove(task);
                        return;
                    }
                    schedule(task, task.request.intervalTicks());
                });
    }

    private Map<String, String> loopVariables(Task task, int nextIndex) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("task_id", task.id);
        values.put("task_key", task.key);
        values.put("task_index", String.valueOf(nextIndex));
        values.put("task_times", String.valueOf(task.request.times()));
        values.put("task_remaining", String.valueOf(Math.max(0, task.request.times() - nextIndex)));
        values.put("task_interval", task.request.intervalTicks() + "t");
        return values;
    }

    private boolean shouldStop(Task task) {
        Player player = task.player();
        if (task.request.stopWhenOffline() && (player == null || !player.isOnline())) {
            return true;
        }
        if (task.request.stopWhenDead() && player != null && player.isDead()) {
            return true;
        }
        Entity entity = task.request.context().caster().entityOrNull();
        if (task.request.stopWhenDead() && entity instanceof LivingEntity living && living.isDead()) {
            return true;
        }
        String condition = task.request.stopCondition();
        if (Texts.isBlank(condition)) {
            return false;
        }
        String rendered = task.request.context().render(condition);
        Boolean passes = ConditionEvaluator.evaluateSingle(rendered, Texts::toStringSafe);
        return passes == null || !passes;
    }

    private void remove(Task task) {
        task.cancel();
        tasks.remove(task.key);
    }

    private static Player playerOf(PipelineContext context) {
        return context.caster().entityOrNull() instanceof Player player ? player : null;
    }

    public interface PipelineRunner {

        @NotNull
        CompletableFuture<Boolean> run(@NotNull Plugin owner,
                @NotNull List<CompiledPipeline> body,
                @NotNull PipelineContext context,
                boolean stopOnFailure);
    }

    public enum Conflict {

        REPLACE,

        IGNORE,

        ALLOW_DUPLICATE;

        public static @NotNull Conflict parse(@Nullable String raw) {
            if (raw == null) {
                return REPLACE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "ignore" -> IGNORE;
                case "allow_duplicate", "allow" -> ALLOW_DUPLICATE;
                default -> REPLACE;
            };
        }
    }

    public record Request(@NotNull List<CompiledPipeline> body,
            @NotNull PipelineContext context,
            @Nullable String key,
            @NotNull Conflict conflict,
            int times,
            long intervalTicks,
            long initialDelayTicks,
            boolean stopWhenOffline,
            boolean stopWhenDead,
            @Nullable String stopCondition,
            boolean stopOnFailure,
            @NotNull Map<String, String> parameters) {

        public Request {
            body = body == null ? List.of() : List.copyOf(body);
            conflict = conflict == null ? Conflict.REPLACE : conflict;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record Limits(int maxTimes,
            long minIntervalTicks,
            int maxTotal,
            int maxPerPlayer,
            int maxPerPlugin,
            boolean cancelOnQuit) {

        public static @NotNull Limits defaults() {
            return new Limits(100, 1L, 200, 10, 100, true);
        }
    }

    public record Result(@Nullable String key, @Nullable String reasonKey) {

        static Result started(String key) {
            return new Result(key, null);
        }

        static Result rejected(String reasonKey) {
            return new Result(null, reasonKey);
        }

        public boolean successful() {
            return reasonKey == null;
        }
    }

    public record TaskSnapshot(@NotNull String id,
            @NotNull String key,
            @NotNull String pluginName,
            @Nullable UUID playerUuid,
            int index,
            int times,
            long intervalTicks) {
    }

    private final class Task {

        private final String id;
        private final String key;
        private final Request request;
        private volatile int index;
        private volatile boolean cancelled;
        private volatile TaskToken handle;

        private Task(String id, String key, Request request) {
            this.id = id;
            this.key = key;
            this.request = request;
        }

        private Player player() {
            return playerOf(request.context());
        }

        private TaskSnapshot snapshot() {
            Player player = player();
            Plugin source = request.context().sourcePlugin();
            return new TaskSnapshot(id, key,
                    source == null ? owner.getName() : source.getName(),
                    player == null ? null : player.getUniqueId(),
                    index, request.times(), request.intervalTicks());
        }

        private void cancel() {
            cancelled = true;
            TaskToken current = handle;
            if (current != null) {
                current.cancel();
            }
        }
    }
}
