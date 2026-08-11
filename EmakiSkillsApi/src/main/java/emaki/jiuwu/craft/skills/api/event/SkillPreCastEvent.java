package emaki.jiuwu.craft.skills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiSkills after a skill cast has passed all cooldown, resource and
 * condition checks but before the skill effect is executed.
 *
 * <p>This is the central pre-cast hook for both active and passive triggers.
 * Listeners may inspect the casting player, the skill id and the trigger id,
 * and cancel the cast to prevent execution. A cancelled event stops EmakiSkills
 * from running the skill (no resources are consumed and no cooldown is
 * recorded). This event is fired synchronously on the casting player's owner thread.
 */
public final class SkillPreCastEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String skillId;
    private final String triggerId;
    private boolean cancelled;

    /**
     * Creates a pre-cast event.
     *
     * @param player    the casting player
     * @param skillId   the id of the skill about to be cast
     * @param triggerId the id of the trigger that initiated the cast
     */
    public SkillPreCastEvent(Player player, String skillId, String triggerId) {
        this.player = player;
        this.skillId = skillId;
        this.triggerId = triggerId;
    }

    /** {@return the player casting the skill} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the id of the skill about to be cast} */
    public String getSkillId() {
        return skillId;
    }

    /** {@return the id of the trigger that initiated the cast} */
    public String getTriggerId() {
        return triggerId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
