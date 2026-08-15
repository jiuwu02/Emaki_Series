package emaki.jiuwu.craft.station.dismantle;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public record DismantlePoolEntry(ItemSourceRef source, AmountRange amount, double weight) {

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
