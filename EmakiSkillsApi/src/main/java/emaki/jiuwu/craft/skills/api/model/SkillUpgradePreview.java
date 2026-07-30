package emaki.jiuwu.craft.skills.api.model;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/** Immutable quote for the next upgrade attempt of one skill. */
public record SkillUpgradePreview(
        @NotNull String skillId,
        int currentLevel,
        int targetLevel,
        int maxLevel,
        double successRate,
        @NotNull List<CurrencyCost> currencies,
        @NotNull List<MaterialCost> materials,
        @NotNull Map<String, String> targetParameters) {

    public SkillUpgradePreview {
        skillId = skillId == null ? "" : skillId;
        currentLevel = Math.max(0, currentLevel);
        targetLevel = Math.max(0, targetLevel);
        maxLevel = Math.max(1, maxLevel);
        successRate = Math.max(0D, Math.min(100D, successRate));
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
        materials = materials == null ? List.of() : List.copyOf(materials);
        targetParameters = targetParameters == null ? Map.of() : Map.copyOf(targetParameters);
    }

    /** One currency component of an upgrade quote. */
    public record CurrencyCost(@NotNull String provider,
                               @NotNull String currencyId,
                               double amount,
                               @NotNull String displayName) {
        public CurrencyCost {
            provider = provider == null ? "" : provider;
            currencyId = currencyId == null ? "" : currencyId;
            amount = Math.max(0D, amount);
            displayName = displayName == null ? "" : displayName;
        }
    }

    /** One material component of an upgrade quote. */
    public record MaterialCost(@NotNull String item,
                               int amount,
                               @NotNull String displayName) {
        public MaterialCost {
            item = item == null ? "" : item;
            amount = Math.max(1, amount);
            displayName = displayName == null ? "" : displayName;
        }
    }
}
