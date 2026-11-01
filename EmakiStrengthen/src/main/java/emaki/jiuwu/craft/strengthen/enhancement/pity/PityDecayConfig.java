package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;

public record PityDecayConfig(
    @NotNull PityDecayTypeEnum type,
    double value
) {
    public PityDecayConfig {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("衰减值必须为有限数");
        }
        if (type == PityDecayTypeEnum.FIXED_DECAY && value < 0) {
            throw new IllegalArgumentException("固定衰减值不能为负数");
        }
        if (type == PityDecayTypeEnum.PROPORTIONAL && (value < 0 || value > 1)) {
            throw new IllegalArgumentException("比例衰减值必须介于 0 与 1 之间");
        }
    }

    public static @NotNull PityDecayConfig reset() {
        return new PityDecayConfig(PityDecayTypeEnum.RESET, 0);
    }

    public static @NotNull PityDecayConfig fixedDecay(double value) {
        return new PityDecayConfig(PityDecayTypeEnum.FIXED_DECAY, value);
    }

    public static @NotNull PityDecayConfig proportional(double ratio) {
        return new PityDecayConfig(PityDecayTypeEnum.PROPORTIONAL, ratio);
    }
}
