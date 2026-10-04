package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PityEffectConfig(
    @NotNull PityEffectTypeEnum type,
    @Nullable Double bonusValue
) {
    public PityEffectConfig {
        if (bonusValue != null && !Double.isFinite(bonusValue)) {
            throw new IllegalArgumentException("加成值必须为有限数");
        }
        if (type == PityEffectTypeEnum.CHANCE_BONUS && (bonusValue == null || bonusValue <= 0)) {
            throw new IllegalArgumentException("CHANCE_BONUS 类型的加成值必须为正数");
        }
    }

    public static @NotNull PityEffectConfig forceSuccess() {
        return new PityEffectConfig(PityEffectTypeEnum.FORCE_SUCCESS, null);
    }

    public static @NotNull PityEffectConfig chanceBonus(double bonusValue) {
        return new PityEffectConfig(PityEffectTypeEnum.CHANCE_BONUS, bonusValue);
    }
}
