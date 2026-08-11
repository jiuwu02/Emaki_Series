package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * The only class that talks to {@link ExecutionDispatcher}.
 *
 * <p>It owns pending handles by plugin, applies the one and only timeout layer, and propagates owner
 * disable/timeout into the cooperative cancellation token. The interpreter deals only in immutable
 * inputs and futures and therefore cannot accidentally invent a second scheduler policy.</p>
 */
public final class StageDispatcher implements AutoCloseable {

    private final ExecutionDispatcher dispatcher;
    private final CapabilityProbe capabilities;
    private final boolean inline;
    private final Map<Plugin, Set<TaskHandle>> handlesByOwner = new ConcurrentHashMap<>();
    private final Map<Plugin, Set<CancellationSignal>> signalsByOwner = new ConcurrentHashMap<>();
    private final Consumer<String> dispatchObserver;

    /**
     * Creates the production dispatcher.
     *
     * @param dispatcher platform scheduler bridge
     * @param capabilities detected platform capabilities
     */
    public StageDispatcher(@NotNull ExecutionDispatcher dispatcher,
            @NotNull CapabilityProbe capabilities) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.inline = false;
        this.dispatchObserver = null;
    }

    private StageDispatcher(Consumer<String> dispatchObserver) {
        this.dispatcher = null;
        this.capabilities = null;
        this.inline = true;
        this.dispatchObserver = dispatchObserver;
    }

    /**
     * Creates a dispatcher that executes immediately on the calling thread.
     *
     * <p>This exists for the pure interpreter tests required by phase 2. It is deliberately not a
     * fallback used by the production constructor.</p>
     *
     * @return the inline dispatcher
     */
    public static @NotNull StageDispatcher inline() {
        return new StageDispatcher(null);
    }

    /**
     * Creates an inline dispatcher that reports each dispatch to {@code observer}.
     *
     * <p>Exists so tests can assert the same-domain merging rule, which is a statement about how many
     * dispatches a pipeline costs rather than about its result.</p>
     *
     * @param observer receives the task name of every dispatch
     * @return the observing dispatcher
     */
    public static @NotNull StageDispatcher counting(@NotNull Consumer<String> observer) {
        return new StageDispatcher(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * Dispatches one same-domain stage group.
     *
     * @param owner plugin that owns the triggering pipeline
     * @param target explicit scheduler target
     * @param delayTicks delay before invocation
     * @param taskName diagnostic task name
     * @param timeoutMillis timeout applied once to this group
     * @param cancellation shared pipeline cancellation signal
     * @param task group body
     * @param <T> group result type
     * @return completion future
     */
    public <T> @NotNull CompletableFuture<T> dispatch(@NotNull Plugin owner,
            @NotNull DispatchTarget target,
            long delayTicks,
            @Nullable String taskName,
            long timeoutMillis,
            @NotNull CancellationSignal cancellation,
            @NotNull Supplier<T> task) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(task, "task");

        if (!owner.isEnabled()) {
            cancellation.cancel();
            return CompletableFuture.failedFuture(new OwnerDisabledException(owner.getName()));
        }
        if (!target.valid()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Invalid execution target for " + target.domain() + "."));
        }
        if (inline) {
            if (dispatchObserver != null) {
                // Delay is reported rather than slept through: the tests need to assert that `after 10t`
                // asks for 10 ticks, without making the suite wait for real time to pass.
                dispatchObserver.accept(delayTicks > 0L
                        ? safeName(taskName) + "@" + delayTicks
                        : safeName(taskName));
            }
            return invokeInline(cancellation, task);
        }
        if (!capabilities.supports(target.domain())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Unsupported execution domain: " + target.domain() + "."));
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicReference<TaskHandle> handleReference = new AtomicReference<>();
        registerSignal(owner, cancellation);
        Runnable invocation = () -> invoke(task, cancellation, future);
        Runnable retired = () -> future.completeExceptionally(new StageRetiredException(safeName(taskName)));
        try {
            TaskHandle handle = schedule(owner, target, invocation, retired, Math.max(0L, delayTicks));
            handleReference.set(handle);
            if (handle == null) {
                future.completeExceptionally(new IllegalStateException(
                        "Scheduler rejected action group " + safeName(taskName) + "."));
            } else {
                handlesByOwner.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(handle);
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        long safeTimeout = Math.max(1L, timeoutMillis);
        future.orTimeout(safeTimeout, TimeUnit.MILLISECONDS).whenComplete((result, throwable) -> {
            TaskHandle handle = handleReference.get();
            if (handle != null) {
                Set<TaskHandle> handles = handlesByOwner.get(owner);
                if (handles != null) {
                    handles.remove(handle);
                }
            }
            unregisterSignal(owner, cancellation);
            if (unwrap(throwable) instanceof TimeoutException) {
                cancellation.cancel();
                if (handle != null) {
                    handle.cancel();
                }
            }
        });
        return future;
    }

    /**
     * Cancels all pending groups owned by a plugin.
     *
     * @param owner plugin being disabled or reloaded
     * @return how many scheduler handles were cancelled
     */
    public int cancelOwner(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        Set<CancellationSignal> signals = signalsByOwner.remove(owner);
        if (signals != null) {
            signals.forEach(CancellationSignal::cancel);
        }
        Set<TaskHandle> handles = handlesByOwner.remove(owner);
        if (handles == null) {
            return 0;
        }
        int cancelled = 0;
        for (TaskHandle handle : List.copyOf(handles)) {
            try {
                handle.cancel();
                cancelled++;
            } catch (RuntimeException ignored) {
                // Continue cancelling the rest. Shutdown must not strand handles because one backend
                // implementation threw while cancelling.
            }
        }
        return cancelled;
    }

    @Override
    public void close() {
        Set<Plugin> owners = ConcurrentHashMap.newKeySet();
        owners.addAll(handlesByOwner.keySet());
        owners.addAll(signalsByOwner.keySet());
        owners.forEach(this::cancelOwner);
    }

    private <T> CompletableFuture<T> invokeInline(CancellationSignal cancellation, Supplier<T> task) {
        if (cancellation.cancelled()) {
            return CompletableFuture.failedFuture(new CancellationException("Pipeline was cancelled."));
        }
        try {
            return CompletableFuture.completedFuture(task.get());
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private <T> void invoke(Supplier<T> task,
            CancellationSignal cancellation,
            CompletableFuture<T> future) {
        if (future.isDone()) {
            return;
        }
        if (cancellation.cancelled()) {
            future.completeExceptionally(new CancellationException("Pipeline was cancelled."));
            return;
        }
        try {
            future.complete(task.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private TaskHandle schedule(Plugin owner,
            DispatchTarget target,
            Runnable task,
            Runnable retired,
            long delayTicks) {
        return switch (target.domain()) {
            case SERVER_GLOBAL -> delayTicks > 0L
                    ? dispatcher.runGlobalLater(owner, task, delayTicks)
                    : dispatcher.runGlobal(owner, task);
            case ENTITY -> delayTicks > 0L
                    ? dispatcher.runEntityLater(owner, target.entity(), task, retired, delayTicks)
                    : dispatcher.runEntity(owner, target.entity(), task, retired);
            case LOCATION_REGION -> delayTicks > 0L
                    ? dispatcher.runAtLocationLater(owner, target.location(), task, delayTicks)
                    : dispatcher.runAtLocation(owner, target.location(), task);
            case ASYNC_COMPUTE, PHYSICAL_FILE -> delayTicks > 0L
                    ? dispatcher.runAsyncLater(owner, task, Math.multiplyExact(delayTicks, 50L),
                            TimeUnit.MILLISECONDS)
                    : dispatcher.runAsync(owner, task);
        };
    }

    private void registerSignal(Plugin owner, CancellationSignal signal) {
        signalsByOwner.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(signal);
    }

    private void unregisterSignal(Plugin owner, CancellationSignal signal) {
        Set<CancellationSignal> signals = signalsByOwner.get(owner);
        if (signals != null) {
            signals.remove(signal);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && (current instanceof CompletionException
                || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeName(String taskName) {
        return taskName == null || taskName.isBlank() ? "unknown" : taskName.trim();
    }

    /**
     * Concrete scheduler destination for a group.
     *
     * @param domain explicit thread domain
     * @param entity entity owner for {@link ExecutionDomain#ENTITY}
     * @param location region owner for {@link ExecutionDomain#LOCATION_REGION}
     */
    public record DispatchTarget(@NotNull ExecutionDomain domain,
            @Nullable Entity entity,
            @Nullable Location location) {

        public DispatchTarget {
            domain = domain == null ? ExecutionDomain.SERVER_GLOBAL : domain;
        }

        /** {@return a server-global target} */
        public static @NotNull DispatchTarget global() {
            return new DispatchTarget(ExecutionDomain.SERVER_GLOBAL, null, null);
        }

        /** {@return an entity-owned target} */
        public static @NotNull DispatchTarget entity(@Nullable Entity entity) {
            return new DispatchTarget(ExecutionDomain.ENTITY, entity, null);
        }

        /** {@return a region-owned target} */
        public static @NotNull DispatchTarget location(@Nullable Location location) {
            return new DispatchTarget(ExecutionDomain.LOCATION_REGION, null, location);
        }

        /** {@return an asynchronous compute target} */
        public static @NotNull DispatchTarget async() {
            return new DispatchTarget(ExecutionDomain.ASYNC_COMPUTE, null, null);
        }

        /** {@return a physical-file target on the asynchronous scheduler} */
        public static @NotNull DispatchTarget physicalFile() {
            return new DispatchTarget(ExecutionDomain.PHYSICAL_FILE, null, null);
        }

        /** {@return whether this target contains the owner required by its domain} */
        public boolean valid() {
            return switch (domain) {
                case SERVER_GLOBAL, ASYNC_COMPUTE, PHYSICAL_FILE -> true;
                case ENTITY -> entity != null;
                case LOCATION_REGION -> location != null && location.getWorld() != null;
            };
        }
    }

    /** Entity retired or otherwise disappeared before its delayed group could run. */
    public static final class StageRetiredException extends RuntimeException {

        private StageRetiredException(String taskName) {
            super("Action group retired before execution: " + taskName + ".");
        }
    }

    /** Pipeline owner was disabled before a group could run. */
    public static final class OwnerDisabledException extends RuntimeException {

        private OwnerDisabledException(String ownerName) {
            super("Action owner is disabled: " + ownerName + ".");
        }
    }

}
