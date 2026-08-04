package emaki.jiuwu.craft.station.config;

import emaki.jiuwu.craft.station.api.model.ProgressMode;

/**
 * Queue defaults from {@code config.yml}, inherited by every station that does not override them.
 *
 * @param baseLength        the baseline queue length
 * @param permissionTiers   whether {@code emakistation.queue.<n>} raises the ceiling
 * @param maxLength         the hard ceiling after permission tiers are applied
 * @param progressMode      the default advance mode
 * @param cancelRefundRate  the fraction refunded on cancellation, in {@code 0.0..1.0}
 * @param tickIntervalTicks how often the ticker runs, in ticks
 * @param speedMultiplier   the default duration multiplier
 */
public record QueueSettings(int baseLength,
        boolean permissionTiers,
        int maxLength,
        ProgressMode progressMode,
        double cancelRefundRate,
        long tickIntervalTicks,
        double speedMultiplier) {

    /** {@return the shipped defaults} */
    public static QueueSettings defaults() {
        return new QueueSettings(3, true, 32, ProgressMode.OFFLINE, 1.0D, 20L, 1.0D);
    }

    /**
     * Clamps every field into its documented range.
     *
     * <p>Applied after parsing so a malformed value degrades to a usable one instead of producing a
     * queue that can never accept work or a ticker that saturates the scheduler.
     *
     * @return a settings record whose values are all in range
     */
    public QueueSettings normalized() {
        int safeBase = Math.clamp(baseLength, 1, 64);
        int safeMax = Math.clamp(maxLength, safeBase, 64);
        double safeRefund = Math.clamp(cancelRefundRate, 0.0D, 1.0D);
        long safeInterval = Math.clamp(tickIntervalTicks, 10L, 100L);
        double safeSpeed = speedMultiplier <= 0.0D ? 1.0D : Math.min(speedMultiplier, 100.0D);
        return new QueueSettings(safeBase,
                permissionTiers,
                safeMax,
                progressMode == null ? ProgressMode.OFFLINE : progressMode,
                safeRefund,
                safeInterval,
                safeSpeed);
    }
}
