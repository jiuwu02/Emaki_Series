package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;

public record PityDecayConfig(
    @NotNull PityDecayTypeEnum type,
    double value
) {
    public PityDecayConfig {
        if (type == PityDecayTypeEnum.FIXED_DECAY && value < 0) {
            throw new IllegalArgumentException("Fixed decay value cannot be negative");
        }
        if (type == PityDecayTypeEnum.PROPORTIONAL && (value < 0 || value > 1)) {
            throw new IllegalArgumentException("Proportional decay value must be between 0 and 1");
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
