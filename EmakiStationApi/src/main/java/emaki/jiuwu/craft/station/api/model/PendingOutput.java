package emaki.jiuwu.craft.station.api.model;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * One output that a finished craft still owes the player.
 *
 * <p>Outputs are stored as an identity plus a {@code long} count, never as rendered
 * {@link org.bukkit.inventory.ItemStack}s. Delivery rebuilds the stacks from the identity at hand-out
 * time and splits them by the item's own maximum stack size, which is what keeps display-only lore and
 * rewritten stack-size components from ever being burned into an item a player receives.
 *
 * @param source the item identity to deliver
 * @param amount the units still owed; always positive
 */
public record PendingOutput(@NotNull ItemSourceRef source, long amount) {

    /**
     * Creates a pending-output record.
     *
     * @param source the item identity to deliver
     * @param amount the units still owed
     * @throws NullPointerException     when {@code source} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public PendingOutput {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    /**
     * Returns a copy owing a different amount.
     *
     * @param newAmount the remaining units
     * @return a new record carrying {@code newAmount}
     */
    public @NotNull PendingOutput withAmount(long newAmount) {
        return new PendingOutput(source, newAmount);
    }
}
