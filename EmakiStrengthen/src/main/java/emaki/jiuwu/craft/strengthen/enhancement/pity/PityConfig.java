package emaki.jiuwu.craft.strengthen.enhancement.pity;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public record PityConfig(
    @NotNull PityCounterConfig counter,
    @NotNull PityTriggerConfig trigger,
    @NotNull PityDecayConfig decay,
    @NotNull List<PityEffectConfig> effects
) {
    public PityConfig {
        if (effects == null || effects.isEmpty()) {
            throw new IllegalArgumentException("保底效果列表不能为空");
        }
        effects = List.copyOf(effects);
    }
}
