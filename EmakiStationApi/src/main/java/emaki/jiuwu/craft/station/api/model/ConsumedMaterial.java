package emaki.jiuwu.craft.station.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * One material debit recorded when a craft took materials from a player.
 *
 * <p>Each record names the item identity taken ({@code source}), how much was taken, and the
 * {@link MaterialChannel} it came from, which is also the channel any refund returns to. The
 * identity triple {@code materialId} / {@code requirementId} / {@code countKey} preserves what
 * the recipe requirement declared, or the legacy placeholders for debits produced without a
 * recipe requirement, and {@code position} keeps the inventory slot the stack was taken from
 * when a slot applies ({@code -1} otherwise).
 *
 * <p>The compact constructor rejects a {@code null} {@code materialId}, {@code requirementId},
 * {@code countKey}, {@code source} or {@code channel}, requires a positive {@code amount}, clamps
 * {@code refundedAmount} into {@code [0, amount]}, and clones a non-null {@code itemSnapshot} so
 * the record never shares the caller's stack. Instances are immutable.
 *
 * @param materialId     material identity declared by the requirement, or {@code "legacy"}
 * @param requirementId  requirement identity declared by the recipe, or {@code "legacy"}
 * @param countKey       counting key declared for the requirement, or {@code "legacy"}
 * @param source         the item identity that was taken
 * @param position       the inventory slot the debit was taken from, or {@code -1} when no slot
 *                       applies
 * @param channel        the channel the materials came from; refunds return to this channel
 * @param amount         units taken; must be positive
 * @param refundedAmount units already refunded; clamped into {@code [0, amount]}
 * @param itemSnapshot   defensive copy of the taken stack when one was captured, otherwise
 *                       {@code null}
 */
public record ConsumedMaterial(@NotNull String materialId,
        @NotNull String requirementId,
        @NotNull String countKey,
        @NotNull ItemSourceRef source,
        int position,
        @NotNull MaterialChannel channel,
        long amount,
        long refundedAmount,
        @Nullable ItemStack itemSnapshot) {

    /**
     * Validates the identity fields, clamps the refund, and clones the snapshot.
     *
     * @param materialId     material identity
     * @param requirementId  requirement identity
     * @param countKey       counting key
     * @param source         the item identity taken
     * @param position       the slot the debit was taken from, or {@code -1}
     * @param channel        the channel the materials came from
     * @param amount         units taken
     * @param refundedAmount units already refunded, clamped into {@code [0, amount]}
     * @param itemSnapshot   the stack to copy defensively, or {@code null}
     * @throws NullPointerException     when {@code materialId}, {@code requirementId},
     *                                  {@code countKey}, {@code source} or {@code channel} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
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
            throw new IllegalArgumentException("amount 必须为正数: " + amount);
        }
        refundedAmount = Math.clamp(refundedAmount, 0L, amount);
        itemSnapshot = itemSnapshot == null ? null : itemSnapshot.clone();
    }

    /**
     * Creates a record without an item snapshot.
     *
     * @param materialId     material identity
     * @param requirementId  requirement identity
     * @param countKey       counting key
     * @param source         the item identity taken
     * @param position       the slot the debit was taken from, or {@code -1}
     * @param channel        the channel the materials came from
     * @param amount         units taken
     * @param refundedAmount units already refunded
     * @throws NullPointerException     when an identity field or the channel is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
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

    /**
     * Creates a legacy record for debits produced without a recipe requirement: the identity
     * triple is fixed to {@code "legacy"}, {@code position} to {@code -1}, and nothing has been
     * refunded yet.
     *
     * @param source  the item identity taken
     * @param amount  units taken
     * @param channel the channel the materials came from
     * @throws NullPointerException     when {@code source} or {@code channel} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public ConsumedMaterial(ItemSourceRef source, long amount, MaterialChannel channel) {
        this("legacy", "legacy", "legacy", source, -1, channel, amount, 0L, null);
    }

    /**
     * Returns a copy recording a different refunded amount.
     *
     * @param value the new refunded amount; the constructor clamps it into {@code [0, amount]}
     * @return a new record with {@code refundedAmount} replaced, keeping every other component
     */
    public ConsumedMaterial withRefundedAmount(long value) {
        return new ConsumedMaterial(materialId, requirementId, countKey, source, position, channel, amount, value,
                itemSnapshot);
    }

    /** {@return the units not yet refunded, that is, {@code amount - refundedAmount}} */
    public long refundableAmount() {
        return amount - refundedAmount;
    }
}
