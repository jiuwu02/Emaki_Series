package emaki.jiuwu.craft.station.dismantle;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public record DismantleOutput(ItemSourceRef source, int amount) {

    public DismantleOutput {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }
}
