package emaki.jiuwu.craft.mobs.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a managed mob trigger resolves at least one pipeline and before any resolved pipeline runs.
 *
 * <p>Runs synchronously on the invoking thread; Bukkit-originated triggers normally use the mob owner's
 * thread. Cancellation suppresses every pipeline for this invocation. No event is emitted when CoreLib is
 * unavailable, the mob definition is absent, or no executable pipeline resolves.
 */
public final class EmakiMobSkillTriggerEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity mob;
    private final String mobId;
    private final String trigger;
    private boolean cancelled;

    /**
     * Creates a managed mob skill trigger event.
     *
     * @param mob the managed entity executing the trigger
     * @param mobId the loaded mob definition id
     * @param trigger the trigger id whose pipelines are about to run
     */
    public EmakiMobSkillTriggerEvent(@NotNull LivingEntity mob,
                                     @NotNull String mobId,
                                     @NotNull String trigger) {
        this.mob = mob;
        this.mobId = mobId;
        this.trigger = trigger;
    }

    /** {@return the managed entity executing the trigger} */
    public @NotNull LivingEntity getMob() {
        return mob;
    }

    /** {@return the loaded mob definition id} */
    public @NotNull String getMobId() {
        return mobId;
    }

    /** {@return the trigger id whose pipelines are about to run} */
    public @NotNull String getTrigger() {
        return trigger;
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
