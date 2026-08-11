package emaki.jiuwu.craft.gem.api.model;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of a committed gem extraction.
 *
 * @param operationId      stable id shared by the pre-event and eventual completed event
 * @param updatedEquipment equipment with the socket cleared
 * @param returnedGem      returned gem, or {@code null} when the configured mode destroys it
 * @param slotIndex        socket index used
 * @param gemId            canonical extracted gem id
 * @param gemLevel         level before any configured degradation
 * @param returnMode       configured return mode
 */
public record GemExtractOutcome(@NotNull String operationId,
                                @NotNull ItemStack updatedEquipment,
                                @Nullable ItemStack returnedGem,
                                int slotIndex,
                                @NotNull String gemId,
                                int gemLevel,
                                @NotNull String returnMode) {

    public GemExtractOutcome {
        operationId = operationId == null ? "" : operationId;
        if (updatedEquipment == null) {
            throw new NullPointerException("updatedEquipment");
        }
        gemId = gemId == null ? "" : gemId;
        gemLevel = Math.max(1, gemLevel);
        returnMode = returnMode == null ? "" : returnMode;
    }

    /** {@return the returned gem when the configured mode produced one} */
    public @NotNull Optional<ItemStack> gemItem() {
        return Optional.ofNullable(returnedGem);
    }
}
