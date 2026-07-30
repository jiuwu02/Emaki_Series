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
 * <p><strong>Thread:</strong> the player's entity-owner thread. The event is synchronous and read-only.
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

    public @NotNull String getOperationId() {
        return operationId;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public @NotNull ItemStack getFinalEquipment() {
        return finalEquipment;
    }

    public boolean isInputConsumed() {
        return inputConsumed;
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

    public @NotNull String getReasonKey() {
        return reasonKey;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
