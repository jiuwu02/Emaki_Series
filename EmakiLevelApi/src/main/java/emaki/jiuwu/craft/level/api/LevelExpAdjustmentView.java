package emaki.jiuwu.craft.level.api;

import org.jetbrains.annotations.NotNull;

/** Side-effect-free view of multiplier and daily-quota experience adjustment. */
public record LevelExpAdjustmentView(double originalAmount,
        double multiplier,
        double multipliedAmount,
        double dailyLimit,
        double gainedToday,
        double actualAmount,
        @NotNull String reason) {

    public LevelExpAdjustmentView {
        reason = reason == null ? "" : reason;
    }
}
