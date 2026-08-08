package emaki.jiuwu.craft.station.dismantle;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * One weighted entry in a dismantle recipe's output pool.
 *
 * <p>Each roll against the pool independently picks one entry by weight. A higher weight means a
 * proportionally higher chance of being selected. When all weights are equal every entry has the
 * same probability.
 *
 * @param source the item to produce if this entry is selected
 * @param amount how many of the item to give per successful roll
 * @param weight the relative weight for this entry (must be &gt; 0)
 */
public record DismantlePoolEntry(ItemSourceRef source, AmountRange amount, double weight) {

    /**
     * Creates a validated entry.
     *
     * @throws NullPointerException     when {@code source} or {@code amount} is {@code null}
     * @throws IllegalArgumentException when {@code weight} is not strictly positive
     */
    public DismantlePoolEntry {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (amount == null) {
            throw new NullPointerException("amount");
        }
        if (weight <= 0.0) {
            throw new IllegalArgumentException("weight must be positive: " + weight);
        }
    }
}
