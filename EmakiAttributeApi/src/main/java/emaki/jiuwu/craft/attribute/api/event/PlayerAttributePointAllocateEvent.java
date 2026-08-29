package emaki.jiuwu.craft.attribute.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after an attribute-point allocation is validated and before points are spent.
 *
 * <p>Runs synchronously on the player's owner thread. Cancellation leaves the balance and allocation
 * unchanged. Listeners may override the amount; EmakiAttribute revalidates the replacement against the
 * available balance before committing it.
 */
public final class PlayerAttributePointAllocateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String attributeId;
    private final int availablePoints;
    private final int allocatedPoints;
    private int amount;
    private boolean cancelled;

    /**
     * Creates a point allocation event.
     *
     * @param player          the player allocating points
     * @param attributeId     the parent attribute id receiving the points
     * @param amount          the point count that will be allocated unless changed
     * @param availablePoints the player's unspent point balance before allocation
     * @param allocatedPoints the points already allocated to this attribute
     */
    public PlayerAttributePointAllocateEvent(Player player,
            String attributeId,
            int amount,
            int availablePoints,
            int allocatedPoints) {
        this.player = player;
        this.attributeId = attributeId;
        this.amount = amount;
        this.availablePoints = availablePoints;
        this.allocatedPoints = allocatedPoints;
    }

    /** {@return the player allocating points} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the parent attribute id receiving the points} */
    public String getAttributeId() {
        return attributeId;
    }

    /** {@return the player's unspent point balance before allocation} */
    public int getAvailablePoints() {
        return availablePoints;
    }

    /** {@return the points already allocated to this attribute} */
    public int getAllocatedPoints() {
        return allocatedPoints;
    }

    /** {@return the point count that will be allocated unless changed or cancelled} */
    public int getAmount() {
        return amount;
    }

    /**
     * Overrides the point count to allocate. The value is re-validated against
     * the available balance after the event; an amount greater than the balance
     * aborts the allocation.
     *
     * @param amount the new allocation amount
     */
    public void setAmount(int amount) {
        this.amount = amount;
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
