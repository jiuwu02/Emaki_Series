package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Long-running named tasks that repeat a sequence on an interval.
 *
 * <p>The v2 counterpart of the v1 loop subsystem. It keeps that subsystem's operational guarantees,
 * which exist because a repeating task that outlives its reason is a resource leak: keyed de-duplication,
 * three concurrency ceilings, stop conditions, and cancellation when the owning player leaves.</p>
 *
 * <p>What it deliberately drops is the v1 sync/async split. Reading the v1 service showed the async flag
 * only picked a different minimum interval and ran one extra pre-check; scheduling was identical either
 * way, because the real thread placement comes from each stage's declared domain (requirement R2). So
 * there is one task kind here, and asynchrony remains a per-stage property.</p>
 */
public final class PipelineTaskService implements Listener {

    private final Plugin owner;
    private final ExecutionDispatcher dispatcher;
    private final PipelineRunner runner;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private volatile Limits limits = Limits.defaults();

    /**
     * Creates a service.
     *
     * @param owner the plugin whose scheduler runs the tasks
     * @param dispatcher scheduling bridge
     * @param runner runs one compiled line
     */
    public PipelineTaskService(@NotNull Plugin owner,
            @NotNull ExecutionDispatcher dispatcher,
            @NotNull PipelineRunner runner) {
        this.owner = owner;
        this.dispatcher = dispatcher;
        this.runner = runner;
    }

    /**
     * Applies configured ceilings.
     *
     * @param newLimits the limits, {@code null} restores defaults
     */
    public void configure(@Nullable Limits newLimits) {
        this.limits = newLimits == null ? Limits.defaults() : newLimits;
    }

    /**
     * Starts a task.
     *
     * @param request what to run and how
     * @return the outcome
     */
    public @NotNull Result start(@NotNull Request request) {
        Limits active = limits;
        if (request.body().isEmpty()) {
            return Result.rejected("action.v2.task.empty_body");
        }
        if (request.times() <= 0 || request.times() > active.maxTimes()) {
            return Result.rejected("action.v2.task.times_out_of_range");
        }
        if (request.intervalTicks() < active.minIntervalTicks()) {
            return Result.rejected("action.v2.task.interval_too_small");
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

    /**
     * Cancels tasks by key.
     *
     * @param key exact key, or prefix when {@code prefix} is set
     * @param prefix whether to match by prefix
     * @return how many tasks were cancelled
     */
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

    /**
     * Cancels every task owned by one player.
     *
     * @param playerUuid the player
     * @return how many tasks were cancelled
     */
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

    /** Cancels every task. {@return how many were cancelled} */
    public int stopAll() {
        int size = tasks.size();
        for (Task task : List.copyOf(tasks.values())) {
            task.cancel();
        }
        tasks.clear();
        return size;
    }

    /** {@return how many tasks are active} */
    public int activeCount() {
        return tasks.size();
    }

    /** {@return a point-in-time view of every active task, ordered by key} */
    public @NotNull List<TaskSnapshot> snapshots() {
        return snapshotsMatching(_ -> true);
    }

    /**
     * Lists the tasks a given player owns.
     *
     * @param playerUuid the player
     * @return matching snapshots, empty when {@code playerUuid} is {@code null}
     */
    public @NotNull List<TaskSnapshot> snapshotsByPlayer(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }
        return snapshotsMatching(task -> {
            Player player = task.player();
            return player != null && playerUuid.equals(player.getUniqueId());
        });
    }

    /**
     * Lists the tasks whose key starts with a prefix.
     *
     * <p>Prefix rather than exact match because keys are namespaced by their producer, so a prefix is
     * what lets an operator see one subsystem's tasks without knowing the generated suffixes.</p>
     *
     * @param keyPrefix the prefix
     * @return matching snapshots, empty when {@code keyPrefix} is blank
     */
    public @NotNull List<TaskSnapshot> snapshotsByKey(@Nullable String keyPrefix) {
        if (Texts.isBlank(keyPrefix)) {
            return List.of();
        }
        String target = keyPrefix.trim();
        return snapshotsMatching(task -> task.key.startsWith(target));
    }

    private List<TaskSnapshot> snapshotsMatching(java.util.function.Predicate<Task> filter) {
        List<TaskSnapshot> matches = new java.util.ArrayList<>();
        for (Task task : tasks.values()) {
            if (filter.test(task)) {
                matches.add(task.snapshot());
            }
        }
        matches.sort(java.util.Comparator.comparing(TaskSnapshot::key));
        return List.copyOf(matches);
    }

    /**
     * Cancels a leaving player's tasks.
     *
     * <p>Without this a task keyed to a player keeps ticking against an offline entity until its repeat
     * count runs out, which for a long-running buff loop is effectively forever.</p>
     *
     * @param event the quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (limits.cancelOnQuit()) {
            stopByPlayer(event.getPlayer().getUniqueId());
        }
    }

    private String checkCeilings(Request request, Limits active) {
        if (tasks.size() >= active.maxTotal()) {
            return "action.v2.task.total_limit_reached";
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
            return "action.v2.task.player_limit_reached";
        }
        return perPlugin >= active.maxPerPlugin() ? "action.v2.task.plugin_limit_reached" : null;
    }

    private void schedule(Task task, long delayTicks) {
        Runnable body = () -> tick(task);
        Player player = task.player();
        TaskHandle handle;
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

    /** Runs a compiled body once. */
    public interface PipelineRunner {

        /**
         * Runs every line of a body.
         *
         * @param owner plugin owning the invocation
         * @param body the compiled lines
         * @param context the context for this iteration
         * @param stopOnFailure whether a failing line ends the iteration
         * @return whether the body succeeded
         */
        @NotNull
        java.util.concurrent.CompletableFuture<Boolean> run(@NotNull Plugin owner,
                @NotNull List<CompiledPipeline> body,
                @NotNull PipelineContext context,
                boolean stopOnFailure);
    }

    /** How a duplicate key is handled. */
    public enum Conflict {

        /** Cancel the existing task and start a new one. */
        REPLACE,
        /** Leave the existing task running. */
        IGNORE,
        /** Run both, distinguishing the new one with a suffix. */
        ALLOW_DUPLICATE;

        /**
         * Parses a configured value.
         *
         * @param raw the value
         * @return the parsed conflict policy, defaulting to {@link #REPLACE}
         */
        public static @NotNull Conflict parse(@Nullable String raw) {
            if (raw == null) {
                return REPLACE;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "ignore" -> IGNORE;
                case "allow_duplicate", "allow" -> ALLOW_DUPLICATE;
                default -> REPLACE;
            };
        }
    }

    /**
     * What to run and how.
     *
     * @param body compiled sequence lines
     * @param context the base context, re-derived on every iteration
     * @param key de-duplication key
     * @param conflict how a duplicate key is handled
     * @param times how many iterations
     * @param intervalTicks ticks between iterations
     * @param initialDelayTicks ticks before the first iteration
     * @param stopWhenOffline stop when the owning player is offline
     * @param stopWhenDead stop when the owning entity is dead
     * @param stopCondition stop when this condition stops holding
     * @param stopOnFailure stop when an iteration fails
     * @param parameters extra variables exposed to the body
     */
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

    /**
     * Ceilings and cancellation policy.
     *
     * @param maxTimes largest accepted repeat count
     * @param minIntervalTicks smallest accepted interval
     * @param maxTotal server-wide active task ceiling
     * @param maxPerPlayer per-player ceiling
     * @param maxPerPlugin per-plugin ceiling
     * @param cancelOnQuit whether to cancel a player's tasks when they leave
     */
    public record Limits(int maxTimes,
            long minIntervalTicks,
            int maxTotal,
            int maxPerPlayer,
            int maxPerPlugin,
            boolean cancelOnQuit) {

        /** {@return conservative defaults} */
        public static @NotNull Limits defaults() {
            return new Limits(100, 1L, 200, 10, 100, true);
        }
    }

    /**
     * Outcome of a start attempt.
     *
     * @param key the key the task runs under, {@code null} when rejected
     * @param reasonKey why it was rejected, {@code null} on success
     */
    public record Result(@Nullable String key, @Nullable String reasonKey) {

        static Result started(String key) {
            return new Result(key, null);
        }

        static Result rejected(String reasonKey) {
            return new Result(null, reasonKey);
        }

        /** {@return whether the task started} */
        public boolean successful() {
            return reasonKey == null;
        }
    }

    /**
     * A read-only view of one active task.
     *
     * <p>Copied out rather than exposing {@code Task}, so an operator command cannot cancel or reschedule
     * a task by reaching through a listing.</p>
     *
     * @param id internal sequence id
     * @param key the de-duplication key the task runs under
     * @param pluginName the plugin the invocation belongs to
     * @param playerUuid the owning player, {@code null} for server-side tasks
     * @param index how many iterations have completed
     * @param times how many iterations were requested
     * @param intervalTicks ticks between iterations
     */
    public record TaskSnapshot(@NotNull String id,
            @NotNull String key,
            @NotNull String pluginName,
            @Nullable UUID playerUuid,
            int index,
            int times,
            long intervalTicks) {
    }

    /** One running task. */
    private final class Task {

        private final String id;
        private final String key;
        private final Request request;
        private volatile int index;
        private volatile boolean cancelled;
        private volatile TaskHandle handle;

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
            TaskHandle current = handle;
            if (current != null) {
                current.cancel();
            }
        }
    }
}
