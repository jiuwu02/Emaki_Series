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
 * <p><strong>Thread:</strong> the player's entity-owner thread. The event is synchronous.
 * Cancellation prevents the transaction. Listeners may also replace the success chance.
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

    public @NotNull String getOperationId() {
        return operationId;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ItemStack getEquipment() {
        return equipment;
    }

    public @NotNull ItemStack getGemItem() {
        return gemItem;
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

    public double getSuccessChance() {
        return successChance;
    }

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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static double clamp(double chance) {
        return Math.max(0D, Math.min(100D, chance));
    }
}
