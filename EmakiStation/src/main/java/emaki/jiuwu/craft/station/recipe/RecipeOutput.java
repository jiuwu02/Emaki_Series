package emaki.jiuwu.craft.station.recipe;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.api.model.PendingOutput;

public record RecipeOutput(ItemSourceRef source, long amount) {

    public RecipeOutput {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount 必须为正数: " + amount);
        }
    }

    public long totalFor(long batch) {
        long safeBatch = Math.max(1L, batch);
        try {
            return Math.multiplyExact(amount, safeBatch);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public PendingOutput toPending(long batch) {
        return new PendingOutput(source, totalFor(batch));
    }
}
