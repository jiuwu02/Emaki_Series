package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;

public record PityCounterConfig(
    @NotNull PityScopeEnum scope,
    @NotNull String group
) {
    public PityCounterConfig {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("保底分组不能为空");
        }
    }
}
