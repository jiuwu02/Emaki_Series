package emaki.jiuwu.craft.station.recipe;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.api.model.MaterialRequirementView;

public record MaterialRequirement(List<ItemSourceRef> sources, long amount, boolean consume) {

    public MaterialRequirement {
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
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

    public MaterialRequirementView toView() {
        return new MaterialRequirementView(sources, amount, consume);
    }
}
