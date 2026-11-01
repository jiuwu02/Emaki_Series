package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.quantity.Quantity;

public record PityTriggerConfig(
    @Nullable Integer threshold,
    @Nullable Quantity formula
) {
    public PityTriggerConfig {
        if (threshold == null && formula == null) {
            throw new IllegalArgumentException("必须提供阈值或公式之一");
        }
        if (threshold != null && threshold <= 0) {
            throw new IllegalArgumentException("阈值必须为正数");
        }
    }

    public static @NotNull PityTriggerConfig threshold(int threshold) {
        return new PityTriggerConfig(threshold, null);
    }

    public static @NotNull PityTriggerConfig formula(@NotNull Quantity formula) {
        return new PityTriggerConfig(null, formula);
    }
}
