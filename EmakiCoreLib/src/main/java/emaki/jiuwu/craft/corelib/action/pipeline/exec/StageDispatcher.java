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
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

public final class StageDispatcher implements AutoCloseable {

    private final ExecutionDispatcher dispatcher;
    private final CapabilityProbe capabilities;
    private final boolean inline;
    private final Map<Plugin, Set<TaskToken>> handlesByOwner = new ConcurrentHashMap<>();
    private final Map<Plugin, Set<CancellationSignal>> signalsByOwner = new ConcurrentHashMap<>();
    private final Consumer<String> dispatchObserver;

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

    public static @NotNull StageDispatcher inline() {
        return new StageDispatcher(null);
    }

    public static @NotNull StageDispatcher counting(@NotNull Consumer<String> observer) {
        return new StageDispatcher(Objects.requireNonNull(observer, "observer"));
    }

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
        AtomicReference<TaskToken> handleReference = new AtomicReference<>();
        registerSignal(owner, cancellation);
        Runnable invocation = () -> invoke(task, cancellation, future);
        Runnable retired = () -> future.completeExceptionally(new StageRetiredException(safeName(taskName)));
        try {
            TaskToken handle = schedule(owner, target, invocation, retired, Math.max(0L, delayTicks));
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
            TaskToken handle = handleReference.get();
            if (handle != null) {
                Set<TaskToken> handles = handlesByOwner.get(owner);
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

    public int cancelOwner(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        Set<CancellationSignal> signals = signalsByOwner.remove(owner);
        if (signals != null) {
            signals.forEach(CancellationSignal::cancel);
        }
        Set<TaskToken> handles = handlesByOwner.remove(owner);
        if (handles == null) {
            return 0;
        }
        int cancelled = 0;
        for (TaskToken handle : List.copyOf(handles)) {
            try {
                handle.cancel();
                cancelled++;
            } catch (RuntimeException ignored) {

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

    private TaskToken schedule(Plugin owner,
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

    public record DispatchTarget(@NotNull ExecutionDomain domain,
            @Nullable Entity entity,
            @Nullable Location location) {

        public DispatchTarget {
            domain = domain == null ? ExecutionDomain.SERVER_GLOBAL : domain;
        }

        public static @NotNull DispatchTarget global() {
            return new DispatchTarget(ExecutionDomain.SERVER_GLOBAL, null, null);
        }

        public static @NotNull DispatchTarget entity(@Nullable Entity entity) {
            return new DispatchTarget(ExecutionDomain.ENTITY, entity, null);
        }

        public static @NotNull DispatchTarget location(@Nullable Location location) {
            return new DispatchTarget(ExecutionDomain.LOCATION_REGION, null, location);
        }

        public static @NotNull DispatchTarget async() {
            return new DispatchTarget(ExecutionDomain.ASYNC_COMPUTE, null, null);
        }

        public static @NotNull DispatchTarget physicalFile() {
            return new DispatchTarget(ExecutionDomain.PHYSICAL_FILE, null, null);
        }

        public boolean valid() {
            return switch (domain) {
                case SERVER_GLOBAL, ASYNC_COMPUTE, PHYSICAL_FILE -> true;
                case ENTITY -> entity != null;
                case LOCATION_REGION -> location != null;
            };
        }
    }

    public static final class StageRetiredException extends RuntimeException {

        private StageRetiredException(String taskName) {
            super("Action group retired before execution: " + taskName + ".");
        }
    }

    public static final class OwnerDisabledException extends RuntimeException {

        private OwnerDisabledException(String ownerName) {
            super("Action owner is disabled: " + ownerName + ".");
        }
    }

}
