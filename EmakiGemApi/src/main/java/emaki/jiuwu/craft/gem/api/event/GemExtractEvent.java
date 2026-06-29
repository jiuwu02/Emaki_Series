package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiGem after an extract request has passed validation but before
 * any cost is charged and the gem is removed from its socket.
 *
 * <p>Listeners may inspect the actor, the equipment, the target slot, the
 * resolved gem id/level and the configured return mode, or cancel the extract
 * entirely. A cancelled event stops EmakiGem from charging, rebuilding the
 * equipment and returning the gem. This event is fired on the server thread.
 */
public final class GemExtractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player actor;
    private final ItemStack equipment;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private final String returnMode;
    private boolean cancelled;

    /**
     * Creates an extract event.
     *
     * @param actor      the player performing the extract
     * @param equipment  the equipment the gem is being removed from
     * @param slotIndex  the socket slot index being cleared
     * @param gemId      the gem definition id
     * @param gemLevel   the gem level
     * @param returnMode the configured return mode (destroy/downgrade/return)
     */
    public GemExtractEvent(Player actor,
            ItemStack equipment,
            int slotIndex,
            String gemId,
            int gemLevel,
            String returnMode) {
        this.actor = actor;
        this.equipment = equipment;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = gemLevel;
        this.returnMode = returnMode;
    }

    /** {@return the player performing the extract} */
    public Player getActor() {
        return actor;
    }

    /** {@return the equipment the gem is being removed from} */
    public ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the socket slot index being cleared} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the gem definition id} */
    public String getGemId() {
        return gemId;
    }

    /** {@return the gem level} */
    public int getGemLevel() {
        return gemLevel;
    }

    /** {@return the configured return mode (destroy/downgrade/return)} */
    public String getReturnMode() {
        return returnMode;
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
