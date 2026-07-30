package emaki.jiuwu.craft.strengthen.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;

/**
 * Fired after a strengthen-star transfer has rebuilt the target item successfully.
 *
 * <p>This is the read-only post event paired with {@link StrengthenTransferEvent}. The returned item
 * has already been produced and listeners cannot alter the transfer result.
 *
 * <p><strong>Thread:</strong> fired synchronously on the player's entity-owner thread. On Paper this
 * is the main server thread; on Folia it is the player's region thread.
 */
public final class StrengthenTransferCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack source;
    private final ItemStack target;
    private final String targetRecipeId;
    private final int sourceStar;
    private final double decayRate;
    private final StrengthenTransferOutcome outcome;

    /** Creates a completed transfer event. */
    public StrengthenTransferCompletedEvent(Player player,
            ItemStack source,
            ItemStack target,
            String targetRecipeId,
            int sourceStar,
            double decayRate,
            StrengthenTransferOutcome outcome) {
        this.player = player;
        this.source = cloneItem(source);
        this.target = cloneItem(target);
        this.targetRecipeId = targetRecipeId == null ? "" : targetRecipeId;
        this.sourceStar = Math.max(0, sourceStar);
        this.decayRate = decayRate;
        this.outcome = outcome;
    }

    /** {@return the player who performed the transfer} */
    public Player getPlayer() {
        return player;
    }

    /** {@return a defensive copy of the source item} */
    public ItemStack getSource() {
        return cloneItem(source);
    }

    /** {@return a defensive copy of the target item before rebuilding} */
    public ItemStack getTarget() {
        return cloneItem(target);
    }

    /** {@return the target item's strengthen recipe id} */
    public String getTargetRecipeId() {
        return targetRecipeId;
    }

    /** {@return the source item's star level before transfer} */
    public int getSourceStar() {
        return sourceStar;
    }

    /** {@return the applied transfer decay rate} */
    public double getDecayRate() {
        return decayRate;
    }

    /** {@return the immutable completed transfer outcome} */
    public StrengthenTransferOutcome getOutcome() {
        return outcome;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? null : itemStack.clone();
    }
}
