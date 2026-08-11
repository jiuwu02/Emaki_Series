package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after an inlay transaction has reached its terminal journal state.
 *
 * <p>This is not emitted merely because the equipment state was prepared or because a pending commit
 * action was started. It fires only after configured success actions finish and the persistent operation
 * journal reaches {@code COMPLETED}; a rolled failure whose compensation is complete may also fire with
 * {@link #isSuccessful()} false.
 *
 * <p>Informational only: the outcome is already committed, so this event is not cancellable. Use
 * {@link #isSuccessful()} to tell a successful inlay from a resolved failure, and
 * {@link #getReasonKey()} to see why a failure ended the way it did.
 *
 * <h2>Threading</h2>
 * The transaction may finish on an async chain, but EmakiGem hops back to the thread that owns the
 * player before firing, so listeners may safely touch the player, their inventory, and the surrounding
 * world. On Paper that is the main server thread; on Folia it is the player's region thread.
 *
 * <h2>Coverage — this event is not fired for every attempt</h2>
 * Successful inlays fire only once the caller commits the transaction and the journal reaches
 * {@code COMPLETED}, which means configured success actions finished. If those actions fail or never
 * report success the journal stays in a reward-pending state and no event arrives, even though the
 * equipment was already rebuilt.
 *
 * <p>Resolved failures fire only when their compensation finished cleanly: a failed charge whose
 * compensation completed, a lost chance roll whose refund or no-op settled, and an apply failure whose
 * refund succeeded. A failure whose refund or compensation is still outstanding deliberately stays
 * silent.
 *
 * <p>Nothing is fired when the attempt was rejected during validation, when
 * {@link GemInlayEvent} was cancelled, when the caller never commits a successful transaction, when the
 * plugin is disabled, when the player went offline before the thread hop, or when scheduling was retired
 * during shutdown drain.
 *
 * <p>Do not use this as an exhaustive audit trail. Treat a missing event as "outcome unknown" rather
 * than "no attempt happened", and note in particular that a missing event can mean an inlay that
 * physically succeeded but whose reward stage is unsettled.
 *
 * @see GemInlayEvent
 */
@ApiStatus.Experimental
public final class GemInlayCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String operationId;
    private final Player player;
    private final boolean successful;
    private final ItemStack finalEquipment;
    private final boolean inputConsumed;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private final String reasonKey;

    /**
     * Creates an inlay completed event.
     *
     * @param operationId    the id correlating this outcome with the matching {@link GemInlayEvent}
     * @param player         the player who performed the attempt
     * @param successful     whether the gem was actually inlaid
     * @param finalEquipment the committed equipment state, passed by reference rather than copied
     * @param inputConsumed  whether the gem item was consumed
     * @param slotIndex      the zero-based socket index targeted by the attempt
     * @param gemId          the canonical lowercase gem id
     * @param gemLevel       the gem level; values below {@code 1} are floored to {@code 1}
     * @param reasonKey      the language key explaining a failure; empty on success
     */
    public GemInlayCompletedEvent(@NotNull String operationId,
                                  @NotNull Player player,
                                  boolean successful,
                                  @NotNull ItemStack finalEquipment,
                                  boolean inputConsumed,
                                  int slotIndex,
                                  @NotNull String gemId,
                                  int gemLevel,
                                  @NotNull String reasonKey) {
        this.operationId = operationId;
        this.player = player;
        this.successful = successful;
        this.finalEquipment = finalEquipment;
        this.inputConsumed = inputConsumed;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = Math.max(1, gemLevel);
        this.reasonKey = reasonKey;
    }

    /**
     * {@return the id correlating this outcome with the matching {@link GemInlayEvent}}
     *
     * <p>Use it to pair a pre event with its outcome instead of guessing from player and slot.
     */
    public @NotNull String getOperationId() {
        return operationId;
    }

    /** {@return the player who performed the attempt} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * {@return whether the gem was actually inlaid}
     *
     * <p>{@code false} covers a lost chance roll, a failed charge, and a failed state apply alike;
     * read {@link #getReasonKey()} to tell them apart.
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * {@return the committed equipment state}
     *
     * <p>On success this is the rebuilt stack carrying the new gem. On failure it is the unchanged
     * equipment the attempt started from, not {@code null}. This is the runtime's own reference rather
     * than a defensive copy, and the outcome is already committed, so do not mutate it here.
     */
    public @NotNull ItemStack getFinalEquipment() {
        return finalEquipment;
    }

    /**
     * {@return whether the gem item was consumed}
     *
     * <p>Always {@code true} on success. On a lost chance roll it follows the configured failure
     * action, so a failed attempt can still have destroyed the gem.
     */
    public boolean isInputConsumed() {
        return inputConsumed;
    }

    /** {@return the zero-based socket index targeted by the attempt} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the canonical lowercase id of the gem involved} */
    public @NotNull String getGemId() {
        return gemId;
    }

    /** {@return the gem level; the constructor floors this at 1} */
    public int getGemLevel() {
        return gemLevel;
    }

    /**
     * {@return the language key explaining a failure, empty on success}
     *
     * <p>Useful for logging and diagnostics. Treat it as an open set of stable strings, not an
     * exhaustive enumeration: branch on {@link #isSuccessful()} rather than on specific keys.
     */
    public @NotNull String getReasonKey() {
        return reasonKey;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
