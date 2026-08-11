package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired immediately before a socket-open state change is applied.
 *
 * <p>This is the pre event: the target socket has been resolved and validated, but the equipment has
 * not been rebuilt and no opener item has been consumed. Cancelling stops both, and the caller receives
 * a {@code gem.error.condition_not_met} failure.
 *
 * <p>There is no paired completion event for socket opening. Once this event returns uncancelled the
 * runtime applies the change synchronously, so a successful open has no separate post notification.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the player, so listeners may touch the player, their
 * inventory, and the surrounding world. On Paper that is the main server thread; on Folia it is the
 * player's region thread.
 *
 * <h2>Coverage — this event is not fired for every open attempt</h2>
 * It is skipped when the runtime rejects the request before a socket is resolved: an unknown or disabled
 * opener configuration, an unrecognised or non-socketable equipment item, the configured plugin
 * conditions failing, the required opener item not being held, no closed socket matching the opener's
 * supported types, and a requested socket index that does not exist or is already open.
 *
 * <p>It is also skipped, without any error, when the call is made off the owner thread of the player, in
 * which case the socket may still be opened uncancellable.
 */
public final class GemSocketOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String operationId;
    private final Player player;
    private final ItemStack equipment;
    private final ItemStack openerItem;
    private final String openerId;
    private final int slotIndex;
    private final String itemDefinitionId;
    private boolean cancelled;

    /**
     * Creates a socket-open pre event.
     *
     * @param operationId      a per-call trace id
     * @param player           the player whose equipment is being modified
     * @param equipment        the equipment being modified, passed by reference rather than copied
     * @param openerItem       the opener stack backing this open, or {@code null} when none was required
     * @param openerId         the id of the socket opener configuration authorising this open
     * @param slotIndex        the zero-based index of the socket about to be opened
     * @param itemDefinitionId the EmakiItem definition id of the equipment
     */
    public GemSocketOpenEvent(@NotNull String operationId,
                              @NotNull Player player,
                              @NotNull ItemStack equipment,
                              @Nullable ItemStack openerItem,
                              @NotNull String openerId,
                              int slotIndex,
                              @NotNull String itemDefinitionId) {
        this.operationId = operationId;
        this.player = player;
        this.equipment = equipment;
        this.openerItem = openerItem;
        this.openerId = openerId;
        this.slotIndex = slotIndex;
        this.itemDefinitionId = itemDefinitionId;
    }

    /** {@return a per-call trace id for this open attempt} */
    public @NotNull String getOperationId() {
        return operationId;
    }

    /** {@return the player whose equipment is being modified} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * {@return the equipment whose socket is about to be opened}
     *
     * <p>This is the runtime's own stack, not a defensive copy. Read it freely, but mutating it changes
     * what the operation applies to; cancel the event instead of editing the stack in place.
     */
    public @NotNull ItemStack getEquipment() {
        return equipment;
    }

    /**
     * {@return the opener stack backing this operation, or {@code null} when none was required}
     *
     * <p>May also be air. Both cases mean the open was authorised without a physical opener being
     * matched, which happens on paths that bypass the opener-item requirement such as administrative
     * commands.
     *
     * <p>This is the stack as it stands <em>before</em> consumption, and it is the runtime's own
     * reference rather than a copy. Whether it is actually consumed depends on the opener configuration.
     */
    public @Nullable ItemStack getOpenerItem() {
        return openerItem;
    }

    /** {@return the id of the socket opener configuration authorising this open} */
    public @NotNull String getOpenerId() {
        return openerId;
    }

    /** {@return the zero-based index of the socket about to be opened} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the EmakiItem definition id of the equipment being modified} */
    public @NotNull String getItemDefinitionId() {
        return itemDefinitionId;
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
