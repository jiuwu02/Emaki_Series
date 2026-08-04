package emaki.jiuwu.craft.strengthen.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiStrengthen after a strengthen attempt has been previewed and
 * passed its condition checks, but before the success roll and before any cost
 * is charged.
 *
 * <p>Listeners may inspect the player, the target item, the recipe and the
 * current/target stars, override the success rate via
 * {@link #setSuccessRate(double)}, or cancel the attempt entirely. A cancelled
 * event stops EmakiStrengthen from rolling, rebuilding the item or charging any
 * cost.
 *
 * <p><strong>Thread:</strong> fired synchronously on the player's entity-owner thread. On Paper
 * this is the main server thread; on Folia it is the player's region thread.
 *
 * <h2>Coverage — this event is not fired for every attempt</h2>
 * It is skipped when the runtime rejects the attempt before reaching the roll: an ineligible or missing
 * target item, a missing strengthen recipe, a star level already at maximum, unsatisfied material
 * inputs, the recipe's own condition group failing, a conflicting in-flight operation on the same
 * operation id, the service no longer accepting attempts during shutdown, and an internal error.
 *
 * <p>It is also skipped, without any error, when the attempt is invoked off the owner thread of the
 * player. The public API rejects that case with {@code WRONG_THREAD}, but a listener should still not
 * assume {@link #setSuccessRate(double)} is consulted on every roll.
 *
 * @see StrengthenAttemptEvent
 */
public final class StrengthenPreAttemptEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack targetItem;
    private final String recipeId;
    private final int currentStar;
    private final int targetStar;
    private final String operationId;
    private double successRate;
    private boolean cancelled;

    /**
     * Creates a pre-attempt event.
     *
     * @param player      the player performing the attempt
     * @param targetItem  the item being strengthened, may be {@code null}
     * @param recipeId    the strengthen recipe id, may be {@code null}
     * @param currentStar the star level before the attempt
     * @param targetStar  the star level targeted on success
     * @param successRate the resolved success rate in percent (0-100)
     */
    public StrengthenPreAttemptEvent(Player player,
            ItemStack targetItem,
            String recipeId,
            int currentStar,
            int targetStar,
            double successRate) {
        this(player, targetItem, recipeId, currentStar, targetStar, successRate, "");
    }

    /**
     * Creates a pre-attempt event with an operation id.
     *
     * @param player      the player performing the attempt
     * @param targetItem  the item being strengthened, may be {@code null}; stored as a defensive copy,
     *                    and an empty stack is normalised to {@code null}
     * @param recipeId    the strengthen recipe id, may be {@code null}
     * @param currentStar the star level before the attempt
     * @param targetStar  the star level targeted on success
     * @param successRate the resolved success rate in percent (0-100); non-finite values become
     *                    {@code 0} and out-of-range values are clamped
     * @param operationId the id used for tracing and idempotency; {@code null} becomes empty and
     *                    surrounding whitespace is trimmed
     */
    public StrengthenPreAttemptEvent(Player player,
            ItemStack targetItem,
            String recipeId,
            int currentStar,
            int targetStar,
            double successRate,
            String operationId) {
        this.player = player;
        this.targetItem = cloneItem(targetItem);
        this.recipeId = recipeId;
        this.currentStar = currentStar;
        this.targetStar = targetStar;
        this.successRate = sanitizeRate(successRate);
        this.operationId = operationId == null ? "" : operationId.trim();
    }

    /** {@return the player performing the attempt} */
    public Player getPlayer() {
        return player;
    }

    /**
     * {@return a defensive copy of the item being strengthened, or {@code null} when the attempt
     * carried no usable target stack}
     *
     * <p>Each call returns a fresh copy, so mutating it does not change the real attempt input. To stop
     * an attempt, cancel the event.
     */
    public ItemStack getTargetItem() {
        return cloneItem(targetItem);
    }

    /** {@return the strengthen recipe id, or {@code null}} */
    public String getRecipeId() {
        return recipeId;
    }

    /** {@return the star level before the attempt} */
    public int getCurrentStar() {
        return currentStar;
    }

    /** {@return the star level targeted on success} */
    public int getTargetStar() {
        return targetStar;
    }

    /** {@return the success rate in percent (0-100) used for the roll} */
    public double getSuccessRate() {
        return successRate;
    }

    /**
     * Overrides the success rate used for the roll.
     *
     * <p>The argument is a percentage, not a {@code [0, 1]} probability. It is clamped to
     * {@code [0, 100]} and any non-finite value becomes {@code 0}, so this never throws. When several
     * listeners write in turn the last write wins, and that final value is the one rolled against unless
     * the event is cancelled.
     *
     * @param successRate the new success rate in percent (0-100)
     */
    public void setSuccessRate(double successRate) {
        this.successRate = sanitizeRate(successRate);
    }

    /**
     * {@return the operation id used for tracing and idempotency, empty when none was supplied}
     *
     * <p>Reusing an id lets the runtime return an earlier result instead of rolling twice, so it is the
     * reliable way to correlate this event with the matching {@link StrengthenAttemptEvent}.
     */
    public String getOperationId() {
        return operationId;
    }

    private static double sanitizeRate(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(100D, value));
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? null : itemStack.clone();
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
