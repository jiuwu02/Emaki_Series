package emaki.jiuwu.craft.strengthen.service;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

public final class ChanceCalculator {

    public record FailureResolution(int resultingStar,
            int resultingTemper,
            boolean downgraded,
            boolean protectionApplied) {

    }

    public double calculateSuccessRate(AppConfig config,
            StrengthenRecipe recipe,
            int currentStar,
            int temperLevel,
            int appliedTemperBonus) {
        if (config == null || recipe == null) {
            return 0D;
        }
        int targetStar = currentStar + 1;
        StrengthenRecipe.Limits limits = recipe.limits();
        int effectiveTemper = Numbers.clamp(temperLevel + Math.max(0, appliedTemperBonus), 0, limits.maxTemper());
        double chance = recipe.successRateForTargetStar(config.successRates(), targetStar);
        chance += effectiveTemper * limits.temperChanceBonusPerLevel();
        return Numbers.clamp(chance, 0D, limits.successChanceCap());
    }

    public FailureResolution resolveFailure(StrengthenRecipe recipe,
            int currentStar,
            int temperLevel,
            int appliedTemperBonus,
            boolean protectionApplied) {
        if (recipe == null) {
            return new FailureResolution(currentStar, temperLevel, false, protectionApplied);
        }
        StrengthenRecipe.Limits limits = recipe.limits();
        int targetStar = currentStar + 1;
        int crackGain = targetStar <= 8 ? 1 : 2;
        boolean downgrade = targetStar >= 6 && !protectionApplied;
        int resultingStar = downgrade ? Math.max(0, currentStar - 1) : currentStar;
        int effectiveTemper = Numbers.clamp(temperLevel + Math.max(0, appliedTemperBonus), 0, limits.maxTemper());
        int resultingTemper = Numbers.clamp(effectiveTemper + crackGain, 0, limits.maxTemper());
        return new FailureResolution(resultingStar, resultingTemper, downgrade, protectionApplied);
    }
}
