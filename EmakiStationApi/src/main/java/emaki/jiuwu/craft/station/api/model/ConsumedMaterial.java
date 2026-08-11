package emaki.jiuwu.craft.station.api.model;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

/**
 * One material that was actually debited for a queued craft, together with the channel it came from.
 *
 * <p>This is the receipt EmakiStation keeps for materials it already took. Because consumption happens
 * at submit time rather than on completion, the queue entry itself is the only proof the player paid,
 * and this record is what a cancellation refunds and what a manual reconciliation reads after a crash.
 * The channel is stored per material rather than per entry so a refund returns each material to the
 * place it was taken from.
 *
 * @param source the item identity that was debited
 * @param amount the debited units; always positive
 * @param channel where the units were taken from
 */
public record ConsumedMaterial(@NotNull ItemSourceRef source, long amount, @NotNull MaterialChannel channel) {

    /**
     * Creates a consumed-material receipt.
     *
     * @param source  the item identity that was debited
     * @param amount  the debited units
     * @param channel where the units were taken from
     * @throws NullPointerException     when {@code source} or {@code channel} is {@code null}
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public ConsumedMaterial {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }
}
