package emaki.jiuwu.craft.skills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiSkills before a player's skill slot binding changes (equipping
 * a skill, unequipping a slot or binding a trigger).
 *
 * <p>Listeners may inspect the slot index, the skill id and the trigger id, or
 * cancel the change entirely. A cancelled event stops EmakiSkills from updating
 * the binding (the originating method returns {@code false}). This event is
 * fired on the server thread.
 */
public final class PlayerSkillSlotChangeEvent extends Event implements Cancellable {

    /** The kind of slot change. */
    public enum Action {
        /** A skill is being equipped to the slot. */
        EQUIP,
        /** The slot is being cleared. */
        UNEQUIP,
        /** A trigger is being bound to the slot's skill. */
        BIND_TRIGGER
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int slotIndex;
    private final String skillId;
    private final String triggerId;
    private final Action action;
    private boolean cancelled;

    /**
     * Creates a slot change event.
     *
     * @param player    the player whose slot is changing
     * @param slotIndex the target slot index
     * @param skillId   the skill id involved, or the previously bound skill id
     *                  for {@link Action#UNEQUIP}; may be {@code null}
     * @param triggerId the trigger id for {@link Action#BIND_TRIGGER}, otherwise
     *                  {@code null}
     * @param action    the kind of slot change
     */
    public PlayerSkillSlotChangeEvent(Player player,
            int slotIndex,
            String skillId,
            String triggerId,
            Action action) {
        this.player = player;
        this.slotIndex = slotIndex;
        this.skillId = skillId;
        this.triggerId = triggerId;
        this.action = action;
    }

    /** {@return the player whose slot is changing} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the target slot index} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the skill id involved, may be {@code null}} */
    public String getSkillId() {
        return skillId;
    }

    /** {@return the trigger id for a trigger bind, otherwise {@code null}} */
    public String getTriggerId() {
        return triggerId;
    }

    /** {@return the kind of slot change} */
    public Action getAction() {
        return action;
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
