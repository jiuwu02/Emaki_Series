package emaki.jiuwu.craft.mobs.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after EmakiMobs has created, marked, and synchronously configured a managed mob, but before
 * its boss bar, deferred health top-up, or {@code on_spawn} action pipelines are registered or run.
 *
 * <p>Cancelling removes the newly created entity and makes the spawning operation return empty. This
 * event only covers entities created through the EmakiMobs mob factory; vanilla entities later marked
 * by type-override rules do not fire it.
 *
 * <p>The event is fired synchronously on the factory caller's entity or location owner thread. On
 * Paper this is normally the main server thread; on Folia it is the region thread owning the spawn
 * location and new entity.
 */
public final class EmakiMobSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final String mobId;
    private boolean cancelled;

    /**
     * Creates a managed mob spawn event.
     *
     * @param entity the newly configured managed entity
     * @param mobId the loaded mob definition id
     */
    public EmakiMobSpawnEvent(@NotNull LivingEntity entity, @NotNull String mobId) {
        this.entity = entity;
        this.mobId = mobId;
    }

    /** {@return the newly configured managed entity} */
    public @NotNull LivingEntity getEntity() {
        return entity;
    }

    /** {@return the loaded mob definition id} */
    public @NotNull String getMobId() {
        return mobId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
