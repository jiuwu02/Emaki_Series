package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiGem after an inlay attempt has passed all validation and cost
 * pre-checks but before the success roll decides the outcome.
 *
 * <p>Listeners may inspect the actor, the equipment, the gem item, the target
 * slot and the resolved gem id/level, override the success chance via
 * {@link #setSuccessChance(double)}, or cancel the inlay entirely. A cancelled
 * event stops EmakiGem from rolling and from assigning the gem. This event is
 * fired on the server thread.
 */
public final class GemInlayEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player actor;
    private final ItemStack equipment;
    private final ItemStack gemItem;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private double successChance;
    private boolean cancelled;

    /**
     * Creates an inlay event.
     *
     * @param actor         the player performing the inlay
     * @param equipment     the equipment receiving the gem
     * @param gemItem       the gem item being inlaid
     * @param slotIndex     the target socket slot index
     * @param gemId         the gem definition id
     * @param gemLevel      the gem level
     * @param successChance the resolved success chance in percent (0-100)
     */
    public GemInlayEvent(Player actor,
            ItemStack equipment,
            ItemStack gemItem,
            int slotIndex,
            String gemId,
            int gemLevel,
            double successChance) {
        this.actor = actor;
        this.equipment = equipment;
        this.gemItem = gemItem;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = gemLevel;
        this.successChance = successChance;
    }

    /** {@return the player performing the inlay} */
    public Player getActor() {
        return actor;
    }

    /** {@return the equipment receiving the gem} */
    public ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the gem item being inlaid} */
    public ItemStack getGemItem() {
        return gemItem;
    }

    /** {@return the target socket slot index} */
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

    /** {@return the success chance in percent (0-100) used for the roll} */
    public double getSuccessChance() {
        return successChance;
    }

    /**
     * Overrides the success chance used for the roll.
     *
     * @param successChance the new success chance in percent (0-100)
     */
    public void setSuccessChance(double successChance) {
        this.successChance = successChance;
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
