package emaki.jiuwu.craft.station.recipe;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.api.model.MaterialRequirementView;

/**
 * One material requirement of a recipe.
 *
 * <p>{@code sources} is an "any of" set whose counts add up: a requirement for 64 units accepting coal
 * or charcoal is satisfied by 30 coal plus 34 charcoal. Matching never looks at which slot an item came
 * from, which is what makes the whole matcher unordered.
 *
 * @param sources the acceptable item identities; never empty
 * @param amount  the units required per batch; always positive
 * @param consume whether satisfying this requirement debits the materials
 */
public record MaterialRequirement(List<ItemSourceRef> sources, long amount, boolean consume) {

    /**
     * Creates a requirement with a defensively copied source list.
     *
     * @param sources the acceptable item identities
     * @param amount  the units required per batch
     * @param consume whether satisfying this requirement debits the materials
     * @throws IllegalArgumentException when {@code sources} is empty or {@code amount} is not positive
     */
    public MaterialRequirement {
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    /**
     * Computes the total units this requirement needs for a batch.
     *
     * <p>Overflow-safe: a batch large enough to overflow {@code long} reports
     * {@link Long#MAX_VALUE} instead of wrapping to a small number, which would otherwise let an
     * absurd batch appear affordable.
     *
     * @param batch how many times the recipe is applied; values below 1 are treated as 1
     * @return the required units, saturating at {@link Long#MAX_VALUE}
     */
    public long totalFor(long batch) {
        long safeBatch = Math.max(1L, batch);
        try {
            return Math.multiplyExact(amount, safeBatch);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** {@return an API view of this requirement} */
    public MaterialRequirementView toView() {
        return new MaterialRequirementView(sources, amount, consume);
    }
}
