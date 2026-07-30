package emaki.jiuwu.craft.attribute.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiAttribute before a custom resource (mana, rage, etc.) is
 * consumed through the public consume entry point.
 *
 * <p>Listeners may inspect the player, the resource id and the current
 * value/max, override the consumed amount via {@link #setAmount(double)}, or
 * cancel the consumption entirely. A cancelled event stops EmakiAttribute from
 * spending the resource and the public operation returns an
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult.Failure} with
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#CANCELLED}. The amount is
 * re-validated against the current balance after the event.
 *
 * <p><strong>Thread:</strong> fired synchronously on the player's owner thread. On Paper this is the main
 * server thread; on Folia this is the player's entity scheduler thread. Public calls from other threads
 * return {@code WRONG_THREAD} before this event is created.
 */
public final class PlayerResourceConsumeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String resourceId;
    private final double currentValue;
    private final double currentMax;
    private double amount;
    private boolean cancelled;

    /**
     * Creates a resource consume event.
     *
     * @param player       the player whose resource is being consumed
     * @param resourceId   the resource id
     * @param amount       the amount that will be consumed unless changed
     * @param currentValue the resource's current value before consumption
     * @param currentMax   the resource's current maximum
     */
    public PlayerResourceConsumeEvent(Player player,
            String resourceId,
            double amount,
            double currentValue,
            double currentMax) {
        this.player = player;
        this.resourceId = resourceId;
        this.amount = amount;
        this.currentValue = currentValue;
        this.currentMax = currentMax;
    }

    /** {@return the player whose resource is being consumed} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the resource id} */
    public String getResourceId() {
        return resourceId;
    }

    /** {@return the resource's current value before consumption} */
    public double getCurrentValue() {
        return currentValue;
    }

    /** {@return the resource's current maximum} */
    public double getCurrentMax() {
        return currentMax;
    }

    /** {@return the amount that will be consumed unless changed or cancelled} */
    public double getAmount() {
        return amount;
    }

    /**
     * Overrides the amount to consume. The value is re-validated against the
     * current balance after the event; an amount greater than the balance
     * aborts the consumption.
     *
     * @param amount the new consume amount
     */
    public void setAmount(double amount) {
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
