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
 * <p><strong>Thread:</strong> the player's entity-owner thread. The event is synchronous.
 * Cancellation prevents charges and item changes.
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

    public @NotNull String getOperationId() {
        return operationId;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ItemStack getEquipment() {
        return equipment;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public @NotNull String getGemId() {
        return gemId;
    }

    public int getGemLevel() {
        return gemLevel;
    }

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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
