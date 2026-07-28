package emaki.jiuwu.craft.storage.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable breakdown of a player's storage capacity.
 *
 * <p>The four sources are persisted separately so lowering {@code base_slots} never consumes
 * slots a player was granted or purchased. {@code effectiveSlots} is the clamped total actually
 * available to the player.
 *
 * @param baseSlots      slots granted by {@code capacity.base_slots}
 * @param permissionSlots slots granted by the highest {@code emakistorage.slots.<n>} permission
 * @param grantedSlots   slots granted through commands or the API (may be negative)
 * @param purchasedSlots slots bought through the in-GUI unlock flow
 * @param effectiveSlots the clamped total, never negative
 * @param maxSlots       the configured hard ceiling, {@code 0} meaning unlimited
 * @param usedSlots      how many slots are currently occupied. With
 *                       {@code behavior.multi_slot_stacking} enabled one entry may occupy several
 *                       slots, so this is the sum of entry spans rather than the entry count; with
 *                       the option disabled the two are identical
 * @param slotsPerPage   how many storage slots one GUI page renders
 */
public record StorageCapacity(int baseSlots,
        int permissionSlots,
        int grantedSlots,
        int purchasedSlots,
        int effectiveSlots,
        int maxSlots,
        int usedSlots,
        int slotsPerPage) {

    /** {@return an empty capacity used when the plugin is unavailable} */
    public static @NotNull StorageCapacity empty() {
        return new StorageCapacity(0, 0, 0, 0, 0, 0, 0, 1);
    }

    /** {@return remaining free slots, never negative} */
    public int freeSlots() {
        return Math.max(0, effectiveSlots - usedSlots);
    }

    /** {@return whether occupancy currently exceeds the effective capacity} */
    public boolean overflowing() {
        return usedSlots > effectiveSlots;
    }

    /** {@return total pages derived from the effective capacity} */
    public int totalPages() {
        int perPage = Math.max(1, slotsPerPage);
        return Math.max(1, (int) Math.ceil((double) effectiveSlots / perPage));
    }

    /** {@return the last page that actually holds an occupied slot} */
    public int reachablePages() {
        int perPage = Math.max(1, slotsPerPage);
        return Math.max(1, (int) Math.ceil((double) usedSlots / perPage));
    }
}
