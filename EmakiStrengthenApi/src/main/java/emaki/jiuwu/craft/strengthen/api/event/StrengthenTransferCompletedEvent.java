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
 * is the main server thread; on Folia it is the player's region thread. The runtime rejects off-thread
 * transfers before reaching this point, so the owner thread is guaranteed here.
 *
 * <h2>Coverage — this event is not fired for every transfer call</h2>
 * It is skipped when the transfer never produced a rebuilt item: a missing player or request, a
 * {@code null}/air source or target, a call made off the player's owner thread, a source carrying no
 * stars, an ineligible target, a target with no loaded recipe, a cancelled {@link StrengthenTransferEvent},
 * a transferred star count that resolved to zero or below, a pending branch-fork selection, and a failed
 * rebuild.
 *
 * <p>Exceptions thrown by listeners are caught and logged by the runtime rather than failing the
 * transfer, which has already been committed by then.
 *
 * <p>Do not treat this as an exhaustive audit trail; a missing event means the transfer did not complete,
 * not necessarily that nothing was attempted.
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

    /**
     * Creates a completed transfer event.
     *
     * @param player         the player who performed the transfer
     * @param source         the item that donated the stars; stored as a defensive copy, and an empty
     *                       stack is normalised to {@code null}
     * @param target         the target item as it stood <em>before</em> rebuilding; stored as a defensive
     *                       copy, and an empty stack is normalised to {@code null}
     * @param targetRecipeId the target item's strengthen recipe id; {@code null} becomes empty
     * @param sourceStar     the source item's star level before the transfer; negative values are
     *                       floored to {@code 0}
     * @param decayRate      the decay rate the runtime applied, as a surviving fraction in
     *                       {@code [0, 1]}
     * @param outcome        the committed transfer outcome carrying the rebuilt target
     */
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

    /**
     * {@return a defensive copy of the source item, or {@code null} when no usable source stack was
     * recorded}
     *
     * <p>This is the source as it stood at transfer time. The runtime does not consume or clear it as
     * part of the transfer, so what happens to the donor item is up to the caller that invoked it.
     */
    public ItemStack getSource() {
        return cloneItem(source);
    }

    /**
     * {@return a defensive copy of the target item before rebuilding, or {@code null} when no usable
     * target stack was recorded}
     *
     * <p>For the rebuilt result read {@link #getOutcome()} instead.
     */
    public ItemStack getTarget() {
        return cloneItem(target);
    }

    /** {@return the target item's strengthen recipe id, empty when none was recorded} */
    public String getTargetRecipeId() {
        return targetRecipeId;
    }

    /**
     * {@return the source item's star level before the transfer; the constructor floors this at 0}
     *
     * <p>The stars actually applied to the target are on {@link #getOutcome()} and are usually lower,
     * because decay and the target recipe's cap are applied, and a listener of
     * {@link StrengthenTransferEvent} may have adjusted the count.
     */
    public int getSourceStar() {
        return sourceStar;
    }

    /**
     * {@return the applied transfer decay rate as a surviving fraction in {@code [0, 1]}}
     *
     * <p>This is the rate the runtime worked from, not a recomputation of the final star count; the
     * committed count is on {@link #getOutcome()}.
     */
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
