package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;

/**
 * {@link EmakiScheduling} 的运行时实现，把窄门面接到内部的 {@link ExecutionDispatcher}
 * 与 {@link ThreadOwnership}。
 *
 * <p>只转发，不新增调度策略；{@link ExecutionDispatcher} 已直接返回 API 侧的 {@link TaskToken}。
 */
public final class DefaultEmakiScheduling implements EmakiScheduling {

    private final ExecutionDispatcher dispatcher;
    private final ThreadOwnership ownership;

    public DefaultEmakiScheduling(ExecutionDispatcher dispatcher, ThreadOwnership ownership) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    @Override
    public boolean ownsGlobal() {
        return ownership.isGlobalOwned();
    }

    @Override
    public boolean ownsEntity(@Nullable Entity entity) {
        return entity != null && ownership.isEntityOwned(entity);
    }

    @Override
    public boolean ownsLocation(@Nullable Location location) {
        return location != null && ownership.isLocationOwned(location);
    }

    @Override
    public @NotNull TaskToken runGlobal(@NotNull Plugin owner, @NotNull Runnable task) {
        return wrap(dispatcher.runGlobal(owner, task));
    }

    @Override
    public @NotNull TaskToken runGlobalLater(@NotNull Plugin owner, @NotNull Runnable task, long delayTicks) {
        return wrap(dispatcher.runGlobalLater(owner, task, delayTicks));
    }

    @Override
    public @NotNull TaskToken runGlobalTimer(@NotNull Plugin owner,
            @NotNull Runnable task,
            long delayTicks,
            long periodTicks) {
        return wrap(dispatcher.runGlobalTimer(owner, task, delayTicks, periodTicks));
    }

    @Override
    public @NotNull TaskToken runForEntity(@NotNull Plugin owner,
            @NotNull Entity entity,
            @NotNull Runnable task,
            @Nullable Runnable retired) {
        return wrap(dispatcher.runEntity(owner, entity, task, retired));
    }

    @Override
    public @NotNull TaskToken runEntityLater(@NotNull Plugin owner,
            @NotNull Entity entity,
            @NotNull Runnable task,
            @Nullable Runnable retired,
            long delayTicks) {
        return wrap(dispatcher.runEntityLater(owner, entity, task, retired, delayTicks));
    }

    @Override
    public @NotNull TaskToken runAtLocation(@NotNull Plugin owner, @NotNull Location location, @NotNull Runnable task) {
        return wrap(dispatcher.runAtLocation(owner, location, task));
    }

    @Override
    public @NotNull TaskToken runAsync(@NotNull Plugin owner, @NotNull Runnable task) {
        return wrap(dispatcher.runAsync(owner, task));
    }

    @Override
    public @NotNull TaskToken runAsyncLater(@NotNull Plugin owner,
            @NotNull Runnable task,
            long delay,
            @NotNull TimeUnit unit) {
        return wrap(dispatcher.runAsyncLater(owner, task, delay, unit));
    }

    @Override
    public @NotNull <T> CompletableFuture<T> submitGlobal(@NotNull Plugin owner, @NotNull Supplier<T> task) {
        return dispatcher.submitGlobal(owner, task);
    }

    private static TaskToken wrap(TaskToken token) {
        return token == null ? TaskToken.UNAVAILABLE : token;
    }
}
