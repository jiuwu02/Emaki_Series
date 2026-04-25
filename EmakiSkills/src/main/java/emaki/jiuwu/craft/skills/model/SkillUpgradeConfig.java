package emaki.jiuwu.craft.skills.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public record SkillUpgradeConfig(
        boolean enabled,
        int maxLevel,
        String guiTemplate,
        EconomyConfig economy,
        Map<Integer, Double> successRates,
        String failurePenalty,
        Map<Integer, SkillUpgradeLevel> levels
) {

    public SkillUpgradeConfig {
        maxLevel = Math.max(1, maxLevel);
        guiTemplate = Texts.isBlank(guiTemplate) ? "upgrade/default" : Texts.toStringSafe(guiTemplate);
        economy = economy == null ? EconomyConfig.disabled() : economy;
        successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
        failurePenalty = Texts.isBlank(failurePenalty) ? "none" : Texts.lower(failurePenalty);
        levels = levels == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(levels));
    }

    public static SkillUpgradeConfig disabled() {
        return new SkillUpgradeConfig(false, 1, "upgrade/default",
                EconomyConfig.disabled(), Map.of(), "none", Map.of());
    }

    public double successRateFor(int targetLevel) {
        SkillUpgradeLevel level = levels.get(targetLevel);
        if (level != null && level.successRate() != null) {
            return clampSuccessRate(level.successRate());
        }
        return clampSuccessRate(successRates.getOrDefault(targetLevel, 100D));
    }

    public List<CurrencyEntry> effectiveCurrencies(int targetLevel) {
        SkillUpgradeLevel level = levels.get(targetLevel);
        if (level != null && level.economyOverride() != null) {
            return level.economyOverride().enabled() ? level.economyOverride().currencies() : List.of();
        }
        return economy.enabled() ? economy.currencies() : List.of();
    }

    private static double clampSuccessRate(double value) {
        return Math.max(0D, Math.min(100D, value));
    }

    public record CurrencyEntry(
            String provider,
            String currencyId,
            double baseCost,
            String costFormula,
            String displayName
    ) {

        public CurrencyEntry {
            provider = Texts.isBlank(provider) ? "auto" : Texts.lower(provider);
            currencyId = Texts.toStringSafe(currencyId);
            baseCost = Math.max(0D, baseCost);
            costFormula = Texts.isBlank(costFormula) ? "{base_cost}" : Texts.toStringSafe(costFormula);
            displayName = Texts.toStringSafe(displayName);
        }
    }

    public record EconomyConfig(boolean enabled, List<CurrencyEntry> currencies) {

        public EconomyConfig {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }

        public static EconomyConfig disabled() {
            return new EconomyConfig(false, List.of());
        }
    }

    public record EconomyOverride(boolean enabled, List<CurrencyEntry> currencies) {

        public EconomyOverride {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }
    }

    public record MaterialCost(String item, int amount, boolean optional, boolean protection) {

        public MaterialCost {
            item = Texts.toStringSafe(item);
            amount = amount == 0 ? 1 : Math.max(1, amount);
        }
    }

    public record SkillUpgradeLevel(
            int targetLevel,
            List<MaterialCost> materials,
            EconomyOverride economyOverride,
            Double successRate,
            Map<String, SkillParameterDefinition> parameters,
            List<String> successActions,
            List<String> failureActions
    ) {

        public SkillUpgradeLevel {
            targetLevel = Math.max(1, targetLevel);
            materials = materials == null ? List.of() : List.copyOf(materials);
            parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
            successActions = successActions == null ? List.of() : List.copyOf(successActions);
            failureActions = failureActions == null ? List.of() : List.copyOf(failureActions);
        }
    }
}
