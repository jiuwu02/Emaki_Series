package emaki.jiuwu.craft.strengthen.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiStrengthen after a star transfer has computed the transferable
 * star count but before the target item is rebuilt.
 *
 * <p>Listeners may inspect the source/target items, the target recipe and the
 * source/transferred stars, override the transferred star count via
 * {@link #setTransferredStar(int)}, or cancel the transfer entirely. A
 * cancelled event stops EmakiStrengthen from rebuilding the target item. This
 * event is fired on the server thread.
 */
public final class StrengthenTransferEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack source;
    private final ItemStack target;
    private final String targetRecipeId;
    private final int sourceStar;
    private final double decayRate;
    private int transferredStar;
    private boolean cancelled;

    /**
     * Creates a transfer event.
     *
     * @param player          the player performing the transfer
     * @param source          the source item the stars come from
     * @param target          the target item receiving the stars
     * @param targetRecipeId  the target item's strengthen recipe id
     * @param sourceStar      the source item's current star count
     * @param transferredStar the computed star count to transfer
     * @param decayRate       the decay rate applied to the transfer
     */
    public StrengthenTransferEvent(Player player,
            ItemStack source,
            ItemStack target,
            String targetRecipeId,
            int sourceStar,
            int transferredStar,
            double decayRate) {
        this.player = player;
        this.source = source;
        this.target = target;
        this.targetRecipeId = targetRecipeId;
        this.sourceStar = sourceStar;
        this.transferredStar = transferredStar;
        this.decayRate = decayRate;
    }

    /** {@return the player performing the transfer} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the source item the stars come from} */
    public ItemStack getSource() {
        return source;
    }

    /** {@return the target item receiving the stars} */
    public ItemStack getTarget() {
        return target;
    }

    /** {@return the target item's strengthen recipe id} */
    public String getTargetRecipeId() {
        return targetRecipeId;
    }

    /** {@return the source item's current star count} */
    public int getSourceStar() {
        return sourceStar;
    }

    /** {@return the decay rate applied to the transfer} */
    public double getDecayRate() {
        return decayRate;
    }

    /** {@return the star count that will be transferred unless changed or cancelled} */
    public int getTransferredStar() {
        return transferredStar;
    }

    /**
     * Overrides the star count to transfer. The value is re-validated by
     * EmakiStrengthen (clamped to the target recipe's max star); a value
     * {@code <= 0} aborts the transfer.
     *
     * @param transferredStar the new transferred star count
     */
    public void setTransferredStar(int transferredStar) {
        this.transferredStar = transferredStar;
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
