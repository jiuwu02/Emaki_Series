package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after extraction validation and immediately before the extraction transaction begins.
 *
 * <p>This is the pre event: nothing has been committed yet. No cost has been taken, the equipment has
 * not been rebuilt, and no gem has been returned to the player. Cancelling stops all of that, and the
 * caller receives a {@code gem.operation.cancelled} failure.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the player, so listeners may touch the player, their
 * inventory, and the surrounding world. On Paper that is the main server thread; on Folia it is the
 * player's region thread.
 *
 * <h2>Coverage — this event is not fired for every extraction call</h2>
 * The gem GUI, the held-item action path, the admin extract command, and
 * {@code emaki.jiuwu.craft.gem.api.GemOperations#extract} fire this event. It is skipped when the
 * runtime rejects the request first: an unrecognised or non-socketable equipment item, a socket that
 * holds no gem or whose gem definition is no longer loaded, an extraction blocked because another
 * inlaid gem depends on this one, and the configured plugin conditions failing.
 *
 * <p>It is also skipped, without any error, when the call is made off the owner thread of the player,
 * in which case the extraction may still proceed uncancellable. Do not treat this event as a
 * chokepoint that every extraction must pass through.
 *
 * @see GemExtractCompletedEvent
 */
public final class GemExtractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String operationId;
    private final Player player;
    private final ItemStack equipment;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private final String returnMode;
    private boolean cancelled;

    /**
     * Creates an extraction pre event.
     *
     * @param operationId the id correlating this attempt with its journal entry and completion event
     * @param player      the player whose equipment is being modified
     * @param equipment   the equipment holding the gem, passed by reference rather than copied
     * @param slotIndex   the zero-based socket index being emptied
     * @param gemId       the canonical lowercase id of the gem being removed
     * @param gemLevel    the level of the inlaid gem; values below {@code 1} are floored to {@code 1}
     * @param returnMode  the configured return mode for this gem, one of {@code original},
     *                    {@code downgrade}, or {@code destroy}
     */
    public GemExtractEvent(@NotNull String operationId,
                           @NotNull Player player,
                           @NotNull ItemStack equipment,
                           int slotIndex,
                           @NotNull String gemId,
                           int gemLevel,
                           @NotNull String returnMode) {
        this.operationId = operationId;
        this.player = player;
        this.equipment = equipment;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = Math.max(1, gemLevel);
        this.returnMode = returnMode;
    }

    /**
     * {@return the id correlating this attempt with its {@link GemExtractCompletedEvent}}
     *
     * <p>Generated per attempt and never blank on this event.
     */
    public @NotNull String getOperationId() {
        return operationId;
    }

    /** {@return the player whose equipment is being modified} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * {@return the equipment the gem is about to be removed from}
     *
     * <p>This is the runtime's own stack, not a defensive copy. Read it freely, but mutating it changes
     * what the transaction operates on; cancel the event instead of editing the stack in place.
     */
    public @NotNull ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the zero-based socket index the gem is being removed from} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the canonical lowercase id of the gem being removed} */
    public @NotNull String getGemId() {
        return gemId;
    }

    /** {@return the level of the inlaid gem; the constructor floors this at 1} */
    public int getGemLevel() {
        return gemLevel;
    }

    /**
     * {@return the configured return mode for this gem}
     *
     * <p>One of {@code original}, {@code downgrade}, or {@code destroy}. This is the configured intent
     * only: {@code downgrade} is a chance-based roll that happens later, so this value does not tell you
     * what the player will actually get back. Read
     * {@link GemExtractCompletedEvent#getReturnedGem()} for the committed result.
     */
    public @NotNull String getReturnMode() {
        return returnMode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
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
