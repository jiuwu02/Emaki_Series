package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public record MaterialRequirementView(@NotNull String materialId,
        @NotNull String requirementId,
        @NotNull String countKey,
        @NotNull List<ItemSourceRef> sources,
        long amount,
        boolean consume) {

    public MaterialRequirementView {
        materialId = materialId == null ? "" : materialId;
        requirementId = requirementId == null ? materialId : requirementId;
        countKey = countKey == null ? materialId : countKey;
        if (sources == null) {
            throw new NullPointerException("sources");
        }
        sources = List.copyOf(sources);
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    public MaterialRequirementView(List<ItemSourceRef> sources, long amount, boolean consume) {
        this("", "", "", sources, amount, consume);
    }
}
