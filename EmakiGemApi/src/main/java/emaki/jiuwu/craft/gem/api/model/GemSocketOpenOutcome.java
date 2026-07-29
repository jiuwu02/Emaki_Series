package emaki.jiuwu.craft.gem.api.model;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of opening a socket slot on a piece of equipment.
 *
 * @param updatedEquipment the equipment with the slot opened
 * @param updatedOpener    the opener item after consumption, or {@code null} when fully consumed
 * @param slotIndex        the slot that was opened
 */
public record GemSocketOpenOutcome(@NotNull ItemStack updatedEquipment,
                                   @Nullable ItemStack updatedOpener,
                                   int slotIndex) {

    /**
     * Validates the equipment.
     *
     * @param updatedEquipment the equipment with the slot opened
     * @param updatedOpener    the remaining opener item, or {@code null}
     * @param slotIndex        opened slot index
     * @throws NullPointerException when {@code updatedEquipment} is {@code null}
     */
    public GemSocketOpenOutcome {
        if (updatedEquipment == null) {
            throw new NullPointerException("updatedEquipment");
        }
    }

    /** {@return the remaining opener item when it was not fully consumed} */
    public @NotNull Optional<ItemStack> remainingOpener() {
        return Optional.ofNullable(updatedOpener);
    }
}
