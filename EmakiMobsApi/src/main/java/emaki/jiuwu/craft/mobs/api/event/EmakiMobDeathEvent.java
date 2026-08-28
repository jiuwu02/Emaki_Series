package emaki.jiuwu.craft.mobs.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after EmakiMobs has finished applying its experience and configured drop handling for a
 * managed mob death.
 *
 * <p>This completion event is observational and cannot change or cancel the death. It is fired only
 * when the dying entity still has a managed mob id whose definition remains loaded. Entities with a
 * stale PDC id or no managed id do not fire it.
 *
 * <p>The event is fired synchronously on the dying entity's owner thread. On Paper this is normally
 * the main server thread; on Folia it is the entity's region thread.
 */
public final class EmakiMobDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final String mobId;
    private final LivingEntity killer;

    /**
     * Creates a managed mob death event.
     *
     * @param entity the dying managed entity
     * @param mobId the loaded mob definition id
     * @param killer the Bukkit killer, or {@code null} when no living killer is available
     */
    public EmakiMobDeathEvent(@NotNull LivingEntity entity,
                              @NotNull String mobId,
                              @Nullable LivingEntity killer) {
        this.entity = entity;
        this.mobId = mobId;
        this.killer = killer;
    }

    /** {@return the dying managed entity} */
    public @NotNull LivingEntity getEntity() {
        return entity;
    }

    /** {@return the loaded mob definition id} */
    public @NotNull String getMobId() {
        return mobId;
    }

    /** {@return the Bukkit killer, or {@code null} when unavailable} */
    public @Nullable LivingEntity getKiller() {
        return killer;
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
