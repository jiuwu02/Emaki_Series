package emaki.jiuwu.craft.corelib.unlock;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;

/**
 * Represents a target that can be unlocked by purchasing additional slots.
 * <p>
 * Used to unify unlock mechanics across Station queues and Storage capacity.
 */
public interface UnlockTarget {

    /**
     * Validates whether the player can unlock the requested number of slots.
     *
     * @param player the player requesting the unlock
     * @param slots  the number of slots to unlock
     * @return null if valid, or a rejection key if the unlock cannot proceed
     */
    @Nullable
    String validate(Player player, int slots);

    /**
     * Returns the current number of already-unlocked slots.
     *
     * @return the count of slots already purchased/unlocked
     */
    int currentCount();

    /**
     * Returns the cost for unlocking the slot at the given ordinal.
     *
     * @param ordinal the 1-based slot ordinal (e.g., 1 for first purchased slot)
     * @return the cost for that slot, or null if no price is defined
     */
    @Nullable
    UnlockSlotCost costAt(int ordinal);

    /**
     * Notifies that an unlock is about to occur, allowing the implementation to fire domain events.
     * <p>
     * Called after validation and cost calculation, but before payment is charged.
     *
     * @param player        the player performing the unlock
     * @param slots         the number of slots being unlocked
     * @param currencyTotal the total currency cost
     * @return true to proceed with the unlock, false to cancel
     */
    boolean notifyUnlock(Player player, int slots, double currencyTotal);

    /**
     * Commits the unlock to persistent storage.
     * <p>
     * Called only after all payments have been successfully charged.
     *
     * @param slots              the number of slots that were unlocked
     * @param currencyTotal      the total currency charged (0 if no currency)
     * @param currencyProviderId the currency provider ID (empty if no currency)
     */
    void commit(int slots, double currencyTotal, String currencyProviderId);
}
