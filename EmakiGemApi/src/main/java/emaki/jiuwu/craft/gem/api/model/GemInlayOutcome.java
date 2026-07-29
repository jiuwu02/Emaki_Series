package emaki.jiuwu.craft.gem.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Result of a completed gem inlay.
 *
 * <p>The equipment carried here is the <em>already committed</em> stack: EmakiGem has written the gem
 * layer, charged any cost, run the success actions, and closed its operation journal entry before this
 * value is produced. Place {@link #updatedEquipment()} back where the original came from.
 *
 * @param updatedEquipment the equipment with the gem inlaid
 * @param inputConsumed    whether the supplied gem item was consumed
 * @param slotIndex        the slot the gem was placed into
 * @param gemId            canonical lowercase id of the inlaid gem
 * @param gemLevel         level of the inlaid gem
 */
public record GemInlayOutcome(@NotNull ItemStack updatedEquipment,
                              boolean inputConsumed,
                              int slotIndex,
                              @NotNull String gemId,
                              int gemLevel) {

    /**
     * Validates the equipment and normalises the gem identity.
     *
     * @param updatedEquipment the equipment with the gem inlaid
     * @param inputConsumed    whether the gem item was consumed
     * @param slotIndex        target slot index
     * @param gemId            inlaid gem id
     * @param gemLevel         inlaid gem level
     * @throws NullPointerException when {@code updatedEquipment} is {@code null}
     */
    public GemInlayOutcome {
        if (updatedEquipment == null) {
            throw new NullPointerException("updatedEquipment");
        }
        gemId = gemId == null ? "" : gemId;
        gemLevel = Math.max(1, gemLevel);
    }
}
