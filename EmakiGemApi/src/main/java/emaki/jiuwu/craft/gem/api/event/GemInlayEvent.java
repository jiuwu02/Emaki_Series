package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after inlay validation and immediately before the transaction begins charging or rolling.
 *
 * <p>This is the pre event: nothing has been committed yet. No cost has been taken, no roll has been
 * made, and the operation journal has not been opened. Cancelling stops all of that, and the caller
 * receives a {@code gem.operation.cancelled} failure. Listeners may also replace the success chance via
 * {@link #setSuccessChance(double)}.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the inlaying player, so listeners may touch the player,
 * their inventory, and the surrounding world. On Paper that is the main server thread; on Folia it is
 * the player's region thread.
 *
 * <h2>Coverage — this event is not fired for every inlay call</h2>
 * The gem GUI, the held-item action path, and
 * {@code emaki.jiuwu.craft.gem.api.GemOperations#inlay} all route through the same runtime entry point
 * and fire this event. It is skipped when the runtime rejects the request before reaching this point:
 * an unrecognised or non-socketable equipment item, a socket index that does not exist, is still
 * closed, or is already occupied, an unrecognised gem item, a gem type or socket type the equipment
 * does not accept, the per-type or per-id limits already being met, a dependency or conflict rule
 * rejecting the gem, and the configured plugin conditions failing.
 *
 * <p>It is also skipped, without any error, when the call is made off the owner thread of the player.
 * In that case the inlay may still proceed with the runtime's own success chance, so a listener that
 * relies on {@link #setSuccessChance(double)} cannot assume it always gets a say.
 *
 * @see GemInlayCompletedEvent
 */
public final class GemInlayEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String operationId;
    private final Player player;
    private final ItemStack equipment;
    private final ItemStack gemItem;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private double successChance;
    private boolean cancelled;

    /**
     * Creates an inlay pre event.
     *
     * @param operationId    the id correlating this attempt with its journal entry and completion event
     * @param player         the player performing the inlay
     * @param equipment      the equipment being socketed, passed by reference rather than copied
     * @param gemItem        the loose gem being consumed, passed by reference rather than copied
     * @param slotIndex      the zero-based socket index being filled
     * @param gemId          the canonical lowercase gem id
     * @param gemLevel       the gem level; values below {@code 1} are floored to {@code 1}
     * @param successChance  the runtime's resolved success chance as a percentage; clamped to
     *                       {@code [0, 100]}
     */
    public GemInlayEvent(@NotNull String operationId,
                         @NotNull Player player,
                         @NotNull ItemStack equipment,
                         @NotNull ItemStack gemItem,
                         int slotIndex,
                         @NotNull String gemId,
                         int gemLevel,
                         double successChance) {
        this.operationId = operationId;
        this.player = player;
        this.equipment = equipment;
        this.gemItem = gemItem;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = Math.max(1, gemLevel);
        this.successChance = clamp(successChance);
    }

    /**
     * {@return the id correlating this attempt with its {@link GemInlayCompletedEvent}}
     *
     * <p>Generated per attempt and never blank on this event.
     */
    public @NotNull String getOperationId() {
        return operationId;
    }

    /** {@return the player performing the inlay} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * {@return the equipment about to be socketed}
     *
     * <p>This is the runtime's own stack, not a defensive copy. Read it freely, but mutating it changes
     * what the transaction operates on; cancel the event instead of editing the stack in place.
     */
    public @NotNull ItemStack getEquipment() {
        return equipment;
    }

    /**
     * {@return the loose gem item about to be consumed}
     *
     * <p>As with {@link #getEquipment()}, this is the runtime's own stack rather than a copy.
     */
    public @NotNull ItemStack getGemItem() {
        return gemItem;
    }

    /** {@return the zero-based socket index the gem is being inlaid into} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the canonical lowercase id of the gem being inlaid} */
    public @NotNull String getGemId() {
        return gemId;
    }

    /** {@return the gem level; the constructor floors this at 1} */
    public int getGemLevel() {
        return gemLevel;
    }

    /** {@return the success chance as a percentage in {@code [0, 100]}} */
    public double getSuccessChance() {
        return successChance;
    }

    /**
     * Replaces the success chance used for this inlay.
     *
     * <p>The argument is <strong>clamped to {@code [0, 100]}</strong>, so {@code -5} becomes {@code 0}
     * and {@code 150} becomes {@code 100} without throwing. This is a percentage, not a {@code [0, 1]}
     * probability.
     *
     * <p>When several listeners write in turn the last write wins, and that final value is the one
     * rolled against unless the event is cancelled.
     *
     * @param successChance the desired success chance as a percentage
     */
    public void setSuccessChance(double successChance) {
        this.successChance = clamp(successChance);
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

    private static double clamp(double chance) {
        return Math.max(0D, Math.min(100D, chance));
    }
}
