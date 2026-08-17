package emaki.jiuwu.craft.strengthen.enhancement.recipe;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.quantity.Quantity;
import emaki.jiuwu.craft.strengthen.enhancement.cost.CurrencyConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityCounterConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityTriggerConfig;

public record EnhancementRecipe(
        @NotNull String id,
        @NotNull String mode,
        @NotNull TargetConfig target,
        @NotNull List<MaterialSlotConfig> materials,
        @NotNull List<CurrencyConfig> costs,
        @NotNull Quantity chance,
        @Nullable PityConfig pity,
        @NotNull Map<String, List<String>> actions
) {

    public EnhancementRecipe {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Recipe id cannot be null or blank");
        }
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("Recipe mode cannot be null or blank");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target config cannot be null");
        }
        materials = materials == null ? List.of() : List.copyOf(materials);
        costs = costs == null ? List.of() : List.copyOf(costs);
        if (chance == null) {
            throw new IllegalArgumentException("Chance cannot be null");
        }
        actions = actions == null ? Map.of() : Map.copyOf(actions);
    }

    public record TargetConfig(
            @NotNull String provider,
            @Nullable Map<String, Object> filter
    ) {
        public TargetConfig {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("Target provider cannot be null or blank");
            }
            filter = filter == null ? null : Map.copyOf(filter);
        }
    }

    public record PityConfig(
            @NotNull PityCounterConfig counter,
            @NotNull PityTriggerConfig trigger,
            @NotNull PityEffectConfig effect,
            @Nullable PityDecayConfig decay
    ) {
        public PityConfig {
            if (counter == null) {
                throw new IllegalArgumentException("Pity counter cannot be null");
            }
            if (trigger == null) {
                throw new IllegalArgumentException("Pity trigger cannot be null");
            }
            if (effect == null) {
                throw new IllegalArgumentException("Pity effect cannot be null");
            }
        }
    }
}
