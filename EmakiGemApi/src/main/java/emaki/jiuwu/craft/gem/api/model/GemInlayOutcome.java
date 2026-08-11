package emaki.jiuwu.craft.gem.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Result of a committed gem inlay.
 *
 * @param operationId     stable id shared by the pre-event and eventual completed event
 * @param updatedEquipment equipment with the gem layer applied
 * @param inputConsumed    whether the caller must consume one input gem
 * @param slotIndex        socket index used
 * @param gemId            canonical gem id
 * @param gemLevel         inlaid gem level
 */
public record GemInlayOutcome(@NotNull String operationId,
                              @NotNull ItemStack updatedEquipment,
                              boolean inputConsumed,
                              int slotIndex,
                              @NotNull String gemId,
                              int gemLevel) {

    public GemInlayOutcome {
        operationId = operationId == null ? "" : operationId;
        if (updatedEquipment == null) {
            throw new NullPointerException("updatedEquipment");
        }
        gemId = gemId == null ? "" : gemId;
        gemLevel = Math.max(1, gemLevel);
    }
}
