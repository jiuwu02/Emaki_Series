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
 * <p><strong>Thread:</strong> the player's entity-owner thread. The event is synchronous.
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

    public @NotNull String getOperationId() {
        return operationId;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ItemStack getEquipment() {
        return equipment;
    }

    /**
     * {@return the opener item consumed for this operation, or {@code null}}
     *
     * 为 {@code null} 表示本次开孔并非由开孔道具触发（例如命令或后台授予）。
     */
    public @Nullable ItemStack getOpenerItem() {
        return openerItem;
    }

    /** {@return the opener id; empty when no opener item was involved} */
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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
