package emaki.jiuwu.craft.gem.api.model;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of a completed gem extraction.
 *
 * <p>As with {@link GemInlayOutcome}, the equipment carried here is already committed. The returned gem
 * may be absent: the configured return mode can destroy the gem, and a degraded return can yield a
 * lower level than was inlaid.
 *
 * @param updatedEquipment the equipment with the gem removed
 * @param returnedGem      the gem item handed back, or {@code null} when the return mode destroys it
 * @param slotIndex        the slot the gem was taken from
 * @param gemId            canonical lowercase id of the extracted gem
 * @param gemLevel         level of the extracted gem before any degradation
 * @param returnMode       the configured return mode that produced this outcome
 */
public record GemExtractOutcome(@NotNull ItemStack updatedEquipment,
                                @Nullable ItemStack returnedGem,
                                int slotIndex,
                                @NotNull String gemId,
                                int gemLevel,
                                @NotNull String returnMode) {

    /**
     * Validates the equipment and normalises the gem identity.
     *
     * @param updatedEquipment the equipment with the gem removed
     * @param returnedGem      the returned gem item, or {@code null}
     * @param slotIndex        source slot index
     * @param gemId            extracted gem id
     * @param gemLevel         extracted gem level
     * @param returnMode       configured return mode
     * @throws NullPointerException when {@code updatedEquipment} is {@code null}
     */
    public GemExtractOutcome {
        if (updatedEquipment == null) {
            throw new NullPointerException("updatedEquipment");
        }
        gemId = gemId == null ? "" : gemId;
        gemLevel = Math.max(1, gemLevel);
        returnMode = returnMode == null ? "" : returnMode;
    }

    /** {@return the returned gem item when the return mode produced one} */
    public @NotNull Optional<ItemStack> gemItem() {
        return Optional.ofNullable(returnedGem);
    }
}
