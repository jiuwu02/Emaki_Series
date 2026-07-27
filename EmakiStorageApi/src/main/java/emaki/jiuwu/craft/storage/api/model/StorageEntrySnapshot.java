package emaki.jiuwu.craft.storage.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of a single storage entry.
 *
 * <p>{@code template} is always a defensive copy with {@code amount == 1}. Third parties may
 * mutate the returned copy freely; it is never the instance used as the internal map key.
 *
 * @param slotIndex  logical slot index, {@code 0}-based and gap-free
 * @param template   the stored item template, amount normalised to one
 * @param amount     how many units are stored
 * @param stackLimit the effective per-slot ceiling applied to this entry
 */
public record StorageEntrySnapshot(int slotIndex, @NotNull ItemStack template, long amount, long stackLimit) {

    public StorageEntrySnapshot(int slotIndex, @NotNull ItemStack template, long amount, long stackLimit) {
        this.slotIndex = slotIndex;
        this.template = template.clone();
        this.amount = amount;
        this.stackLimit = stackLimit;
    }

    /** {@return a fresh copy of the stored template; safe to mutate} */
    @Override
    public @NotNull ItemStack template() {
        return template.clone();
    }

    /** {@return remaining room in this entry before {@link #stackLimit} is reached} */
    public long remainingCapacity() {
        if (stackLimit <= 0L) {
            return Long.MAX_VALUE - amount;
        }
        return Math.max(0L, stackLimit - amount);
    }

    /** {@return the occupancy ratio in {@code 0..1}, or {@code 0} when unlimited} */
    public double fillRatio() {
        if (stackLimit <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) amount / (double) stackLimit);
    }
}
