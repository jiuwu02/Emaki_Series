package emaki.jiuwu.craft.corelib.api;

import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;

/**
 * Scheduling view returned when EmakiCoreLib is not installed. Nothing is scheduled and every token
 * reports itself as already cancelled, so callers that store tokens need no null checks.
 */
final class UnavailableScheduling implements EmakiScheduling {

    static final UnavailableScheduling INSTANCE = new UnavailableScheduling();

    private UnavailableScheduling() {
    }

    @Override
    public boolean ownsGlobal() {
        return false;
    }

    @Override
    public boolean ownsEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean ownsLocation(Location location) {
        return false;
    }

    @Override
    public TaskToken runGlobal(Plugin owner, Runnable task) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runGlobalLater(Plugin owner, Runnable task, long delayTicks) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runGlobalTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runForEntity(Plugin owner, Entity entity, Runnable task, Runnable retired) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runEntityLater(Plugin owner, Entity entity, Runnable task, Runnable retired, long delayTicks) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAtLocation(Plugin owner, Location location, Runnable task) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAsync(Plugin owner, Runnable task) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAsyncLater(Plugin owner, Runnable task, long delay, TimeUnit unit) {
        return TaskToken.UNAVAILABLE;
    }
}
