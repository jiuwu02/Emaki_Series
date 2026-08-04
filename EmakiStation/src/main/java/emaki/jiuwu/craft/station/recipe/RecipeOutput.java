package emaki.jiuwu.craft.station.recipe;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.api.model.PendingOutput;

/**
 * One item a recipe produces per batch.
 *
 * @param source the item identity to produce
 * @param amount the units produced per batch; always positive
 */
public record RecipeOutput(ItemSourceRef source, long amount) {

    /**
     * Creates an output.
     *
     * @param source the item identity to produce
     * @param amount the units produced per batch
     * @throws NullPointerException     when {@code source} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public RecipeOutput {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    /**
     * Computes the total units produced by a batch.
     *
     * <p>Overflow-safe in the same way as the material side, so a saturated batch cannot silently wrap
     * into a small delivery.
     *
     * @param batch how many times the recipe is applied; values below 1 are treated as 1
     * @return the produced units, saturating at {@link Long#MAX_VALUE}
     */
    public long totalFor(long batch) {
        long safeBatch = Math.max(1L, batch);
        try {
            return Math.multiplyExact(amount, safeBatch);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Converts this output into a pending record for a batch.
     *
     * @param batch how many times the recipe is applied
     * @return a pending output owing the batch total
     */
    public PendingOutput toPending(long batch) {
        return new PendingOutput(source, totalFor(batch));
    }
}
