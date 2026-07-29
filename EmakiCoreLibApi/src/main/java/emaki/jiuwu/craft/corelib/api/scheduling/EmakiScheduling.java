package emaki.jiuwu.craft.corelib.api.scheduling;

import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Narrow scheduling view that lets third-party plugins run work on the correct owner thread on both
 * Paper and Folia without depending on EmakiCoreLib internals.
 *
 * <p>Emaki resolves the backend once at startup. On Folia every method routes to the matching
 * regionised scheduler; on Paper they route to the Bukkit scheduler. Callers write the same code for
 * both.
 *
 * <h2>Why this exists</h2>
 * Folia has no single main thread. Touching an entity or a block from the wrong region thread throws
 * or corrupts state. Most Emaki write operations therefore report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} instead of guessing. Use
 * the {@code owns*} predicates to check, and the {@code run*} methods to hop.
 *
 * <p>This interface is deliberately smaller than EmakiCoreLib's internal dispatcher. It will not
 * grow to mirror it.
 */
@ApiStatus.NonExtendable
public interface EmakiScheduling {

    /** {@return whether the calling thread may touch global server state such as worlds and config} */
    boolean ownsGlobal();

    /**
     * @param entity the entity to test; {@code null} yields {@code false}
     * @return whether the calling thread owns the given entity's region
     */
    boolean ownsEntity(@Nullable Entity entity);

    /**
     * @param location the location to test; {@code null} yields {@code false}
     * @return whether the calling thread owns the given location's region
     */
    boolean ownsLocation(@Nullable Location location);

    /**
     * Runs a task on the global region as soon as possible.
     *
     * @param owner the plugin that owns the task lifecycle
     * @param task  the work to perform
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runGlobal(@NotNull Plugin owner, @NotNull Runnable task);

    /**
     * Runs a task on the global region after a tick delay.
     *
     * @param owner      the plugin that owns the task lifecycle
     * @param task       the work to perform
     * @param delayTicks the delay in ticks; values below one are treated as one
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runGlobalLater(@NotNull Plugin owner, @NotNull Runnable task, long delayTicks);

    /**
     * Runs a repeating task on the global region.
     *
     * @param owner       the plugin that owns the task lifecycle
     * @param task        the work to perform each period
     * @param delayTicks  the initial delay in ticks
     * @param periodTicks the period in ticks; values below one are treated as one
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runGlobalTimer(@NotNull Plugin owner, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs a task on the region that owns the given entity.
     *
     * @param owner   the plugin that owns the task lifecycle
     * @param entity  the entity whose region should execute the task
     * @param task    the work to perform
     * @param retired invoked instead of {@code task} when the entity was removed before execution;
     *                may be {@code null}
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runForEntity(@NotNull Plugin owner,
                           @NotNull Entity entity,
                           @NotNull Runnable task,
                           @Nullable Runnable retired);

    /**
     * Runs a task on the region that owns the given location.
     *
     * @param owner    the plugin that owns the task lifecycle
     * @param location the location whose region should execute the task
     * @param task     the work to perform
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runAtLocation(@NotNull Plugin owner, @NotNull Location location, @NotNull Runnable task);

    /**
     * Runs a task off any region thread. Never touch Bukkit state from the task body.
     *
     * @param owner the plugin that owns the task lifecycle
     * @param task  the work to perform
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runAsync(@NotNull Plugin owner, @NotNull Runnable task);

    /**
     * Runs a task off any region thread after a wall-clock delay.
     *
     * @param owner the plugin that owns the task lifecycle
     * @param task  the work to perform
     * @param delay the delay amount
     * @param unit  the delay unit
     * @return a cancellable token, or {@link TaskToken#UNAVAILABLE} when EmakiCoreLib is unusable
     */
    @NotNull
    TaskToken runAsyncLater(@NotNull Plugin owner, @NotNull Runnable task, long delay, @NotNull TimeUnit unit);
}
