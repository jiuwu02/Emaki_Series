package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after an extraction transaction has reached its terminal journal state.
 *
 * <p>The event follows all runtime entry points that commit an extraction, including the public API,
 * held-item actions, the admin extract command, and the gem GUI. It is not emitted while configured
 * success actions are pending.
 *
 * <p>Informational only: the gem is already out and the outcome is committed, so this event is not
 * cancellable. Unlike the inlay counterpart there is no success flag, because this event only represents
 * completed extractions.
 *
 * <h2>Threading</h2>
 * The transaction may finish on an async chain, but EmakiGem hops back to the thread that owns the
 * player before firing, so listeners may safely touch the player, their inventory, and the surrounding
 * world. On Paper that is the main server thread; on Folia it is the player's region thread.
 *
 * <h2>Coverage — this event is not fired for every extraction</h2>
 * It fires only once the journal reaches {@code COMPLETED}, which means the caller committed the
 * transaction and the configured success actions finished. If those actions fail or never report
 * success, the journal stays in a reward-pending state and no event arrives even though the gem has
 * already been removed and handed over.
 *
 * <p>Nothing is fired when the attempt was rejected during validation, when {@link GemExtractEvent} was
 * cancelled, when the charge failed, when rebuilding the equipment failed, when the caller never
 * commits, when the plugin is disabled, when the player went offline before the thread hop, or when
 * scheduling was retired during shutdown drain.
 *
 * <p>Do not use this as an exhaustive audit trail; treat a missing event as "outcome unknown" rather
 * than "no extraction happened".
 *
 * @see GemExtractEvent
 */
@ApiStatus.Experimental
public final class GemExtractCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String operationId;
    private final Player player;
    private final ItemStack finalEquipment;
    private final ItemStack returnedGem;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private final String returnMode;

    /**
     * Creates an extraction completed event.
     *
     * @param operationId    the id correlating this outcome with the matching {@link GemExtractEvent}
     * @param player         the player whose equipment was modified
     * @param finalEquipment the committed equipment state with the socket emptied, passed by reference
     *                       rather than copied
     * @param returnedGem    the gem handed back, or {@code null} when nothing was returned
     * @param slotIndex      the zero-based socket index that was emptied
     * @param gemId          the canonical lowercase id of the extracted gem
     * @param gemLevel       the level of the extracted gem; values below {@code 1} are floored to
     *                       {@code 1}
     * @param returnMode     the configured return mode that produced this result, one of
     *                       {@code original}, {@code downgrade}, or {@code destroy}
     */
    public GemExtractCompletedEvent(@NotNull String operationId,
                                    @NotNull Player player,
                                    @NotNull ItemStack finalEquipment,
                                    @Nullable ItemStack returnedGem,
                                    int slotIndex,
                                    @NotNull String gemId,
                                    int gemLevel,
                                    @NotNull String returnMode) {
        this.operationId = operationId;
        this.player = player;
        this.finalEquipment = finalEquipment;
        this.returnedGem = returnedGem;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = Math.max(1, gemLevel);
        this.returnMode = returnMode;
    }

    /**
     * {@return the id correlating this outcome with the matching {@link GemExtractEvent}}
     *
     * <p>Use it to pair a pre event with its outcome instead of guessing from player and slot.
     */
    public @NotNull String getOperationId() {
        return operationId;
    }

    /** {@return the player whose equipment was modified} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * {@return the committed equipment state with the socket emptied}
     *
     * <p>This is the runtime's own reference rather than a defensive copy, and the outcome is already
     * committed, so do not mutate it here.
     */
    public @NotNull ItemStack getFinalEquipment() {
        return finalEquipment;
    }

    /**
     * {@return the gem handed back to the player, or {@code null} when nothing was returned}
     *
     * <p>{@code null} means the gem was destroyed: either the return mode is {@code destroy}, or a
     * {@code downgrade} roll pushed the level to zero or below. When non-null the stack has already been
     * given to the player or dropped for them, so this is a record of what they received rather than
     * something the listener needs to deliver.
     *
     * <p>Its level may be lower than {@link #getGemLevel()} when the downgrade roll landed.
     */
    public @Nullable ItemStack getReturnedGem() {
        return returnedGem;
    }

    /** {@return the zero-based socket index that was emptied} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the canonical lowercase id of the extracted gem} */
    public @NotNull String getGemId() {
        return gemId;
    }

    /**
     * {@return the level of the gem as it sat in the socket; the constructor floors this at 1}
     *
     * <p>This is the pre-extraction level. The stack from {@link #getReturnedGem()} may carry a lower
     * level under the {@code downgrade} return mode.
     */
    public int getGemLevel() {
        return gemLevel;
    }

    /**
     * {@return the configured return mode that produced this result}
     *
     * <p>One of {@code original}, {@code downgrade}, or {@code destroy}. Because {@code downgrade} is
     * chance-based, this states the configured policy rather than what happened; compare
     * {@link #getReturnedGem()} against {@link #getGemLevel()} for the actual result.
     */
    public @NotNull String getReturnMode() {
        return returnMode;
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
