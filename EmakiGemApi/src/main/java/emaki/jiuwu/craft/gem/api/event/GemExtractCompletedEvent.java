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
 * held-item actions, and the gem GUI. It is not emitted while configured success actions are pending.
 *
 * <p><strong>Thread:</strong> the player's entity-owner thread. The event is synchronous and read-only.
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

    public @NotNull String getOperationId() {
        return operationId;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ItemStack getFinalEquipment() {
        return finalEquipment;
    }

    public @Nullable ItemStack getReturnedGem() {
        return returnedGem;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
