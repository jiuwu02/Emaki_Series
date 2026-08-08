package emaki.jiuwu.craft.station.dismantle;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * One resolved output item from a dismantle roll.
 *
 * <p>Instances are produced at roll-time by {@link DismantleService} and passed to the delivery
 * layer.
 *
 * @param source the item identity to deliver
 * @param amount how many of the item to deliver
 */
public record DismantleOutput(ItemSourceRef source, int amount) {

    /**
     * Creates a validated output.
     *
     * @throws NullPointerException     when {@code source} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public DismantleOutput {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }
}
