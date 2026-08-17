package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PityEffectConfig(
    @NotNull PityEffectTypeEnum type,
    @Nullable Double bonusValue
) {
    public PityEffectConfig {
        if (type == PityEffectTypeEnum.CHANCE_BONUS && (bonusValue == null || bonusValue <= 0)) {
            throw new IllegalArgumentException("Bonus value must be positive for CHANCE_BONUS type");
        }
    }

    public static @NotNull PityEffectConfig forceSuccess() {
        return new PityEffectConfig(PityEffectTypeEnum.FORCE_SUCCESS, null);
    }

    public static @NotNull PityEffectConfig chanceBonus(double bonusValue) {
        return new PityEffectConfig(PityEffectTypeEnum.CHANCE_BONUS, bonusValue);
    }
}
