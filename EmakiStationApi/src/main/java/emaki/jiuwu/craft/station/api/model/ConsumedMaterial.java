package emaki.jiuwu.craft.station.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public record ConsumedMaterial(@NotNull String materialId,
        @NotNull String requirementId,
        @NotNull String countKey,
        @NotNull ItemSourceRef source,
        int position,
        @NotNull MaterialChannel channel,
        long amount,
        long refundedAmount,
        @Nullable ItemStack itemSnapshot) {

    public ConsumedMaterial {
        if (materialId == null) {
            throw new NullPointerException("materialId");
        }
        if (requirementId == null) {
            throw new NullPointerException("requirementId");
        }
        if (countKey == null) {
            throw new NullPointerException("countKey");
        }
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        refundedAmount = Math.clamp(refundedAmount, 0L, amount);
        itemSnapshot = itemSnapshot == null ? null : itemSnapshot.clone();
    }

    public ConsumedMaterial(String materialId,
            String requirementId,
            String countKey,
            ItemSourceRef source,
            int position,
            MaterialChannel channel,
            long amount,
            long refundedAmount) {
        this(materialId, requirementId, countKey, source, position, channel, amount, refundedAmount, null);
    }

    public ConsumedMaterial(ItemSourceRef source, long amount, MaterialChannel channel) {
        this("legacy", "legacy", "legacy", source, -1, channel, amount, 0L, null);
    }

    public ConsumedMaterial withRefundedAmount(long value) {
        return new ConsumedMaterial(materialId, requirementId, countKey, source, position, channel, amount, value,
                itemSnapshot);
    }

    public long refundableAmount() {
        return amount - refundedAmount;
    }
}
