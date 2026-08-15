package emaki.jiuwu.craft.station.config;

import emaki.jiuwu.craft.station.api.model.ProgressMode;

public record QueueSettings(int baseLength,
        boolean permissionTiers,
        int maxLength,
        ProgressMode progressMode,
        double cancelRefundRate,
        long tickIntervalTicks,
        double speedMultiplier) {

    public static QueueSettings defaults() {
        return new QueueSettings(3, true, 32, ProgressMode.OFFLINE, 1.0D, 20L, 1.0D);
    }

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
