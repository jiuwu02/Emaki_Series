package emaki.jiuwu.craft.strengthen.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import emaki.jiuwu.craft.strengthen.api.model.AttemptOutcome;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;

/**
 * Fired by EmakiStrengthen after a strengthen attempt has been fully resolved.
 *
 * <p>This event reports the final outcome: it fires for successful attempts,
 * failed rolls, and early failures (ineligible, condition not met, rebuild or
 * charge failure). The item has already been rebuilt and any cost charged, so
 * the result cannot be changed by listeners. It is suitable for statistics,
 * announcements and downstream effects.
 *
 * <p><strong>Thread:</strong> fired synchronously on the player's entity-owner thread. On Paper
 * this is the main server thread; on Folia it is the player's region thread.
 */
public final class StrengthenAttemptEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AttemptResult result;

    /**
     * Creates a post-attempt event.
     *
     * @param player the player who performed the attempt
     * @param result the fully resolved attempt result
     */
    public StrengthenAttemptEvent(Player player, AttemptResult result) {
        this.player = player;
        this.result = result;
    }

    /** {@return the player who performed the attempt} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the fully resolved attempt result} */
    public AttemptResult getResult() {
        return result;
    }

    /** {@return whether the strengthen roll succeeded} */
    public boolean isSuccess() {
        return result != null && result.success();
    }

    /** {@return whether the attempt committed an externally visible result} */
    public boolean isCommitted() {
        return result != null && result.committed();
    }

    /** {@return the transaction outcome, or {@code NOT_COMMITTED}} */
    public AttemptOutcome getOutcome() {
        return result == null
                ? AttemptOutcome.NOT_COMMITTED
                : result.outcome();
    }

    /** {@return the operation id used for tracing and idempotency} */
    public String getOperationId() {
        return result == null ? "" : result.operationId();
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
