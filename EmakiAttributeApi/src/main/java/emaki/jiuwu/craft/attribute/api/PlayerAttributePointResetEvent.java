package emaki.jiuwu.craft.attribute.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiAttribute before allocated parent-attribute points are reset,
 * after the request has passed validation but before any point is refunded.
 *
 * <p>Listeners may inspect the player, the point total that will be refunded,
 * the current available/reset-point balances and whether a reset point will be
 * spent, or cancel the reset entirely. A cancelled event stops EmakiAttribute
 * from refunding points and from clearing the allocations (the reset call
 * reports a failed result).
 *
 * <p>The refunded total is derived from the player's current allocations and
 * cannot be overridden; a partial refund would leave the allocation totals and
 * the point balance inconsistent.
 *
 * <p>This event is fired on the server thread; off-thread reset calls proceed
 * without firing it.
 */
public final class PlayerAttributePointResetEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int refundedPoints;
    private final int availablePoints;
    private final int resetPoints;
    private final boolean consumesResetPoint;
    private boolean cancelled;

    /**
     * Creates a point reset event.
     *
     * @param player             the player resetting allocated points
     * @param refundedPoints     the point total that will be refunded
     * @param availablePoints    the player's unspent point balance before the reset
     * @param resetPoints        the player's reset-point balance before the reset
     * @param consumesResetPoint whether this reset spends one reset point
     */
    public PlayerAttributePointResetEvent(Player player,
            int refundedPoints,
            int availablePoints,
            int resetPoints,
            boolean consumesResetPoint) {
        this.player = player;
        this.refundedPoints = refundedPoints;
        this.availablePoints = availablePoints;
        this.resetPoints = resetPoints;
        this.consumesResetPoint = consumesResetPoint;
    }

    /** {@return the player resetting allocated points} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the point total that will be refunded} */
    public int getRefundedPoints() {
        return refundedPoints;
    }

    /** {@return the player's unspent point balance before the reset} */
    public int getAvailablePoints() {
        return availablePoints;
    }

    /** {@return the player's reset-point balance before the reset} */
    public int getResetPoints() {
        return resetPoints;
    }

    /** {@return whether this reset spends one reset point} */
    public boolean consumesResetPoint() {
        return consumesResetPoint;
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
