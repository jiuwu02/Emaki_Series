package emaki.jiuwu.craft.strengthen.service;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.model.StrengthenMaterial;
import emaki.jiuwu.craft.strengthen.model.StrengthenProfile;

public final class ChanceCalculator {

    public record FailureResolution(int resultingStar, int resultingCrack, boolean downgraded, boolean protectionApplied) {

    }

    public double calculateSuccessRate(AppConfig config,
            int currentStar,
            int crackLevel,
            StrengthenMaterial supportMaterial) {
        if (config == null) {
            return 0D;
        }
        int targetStar = currentStar + 1;
        double chance = config.successRateForTargetStar(targetStar);
        chance += crackLevel * config.crackChanceBonusPerLevel();
        if (supportMaterial != null) {
            chance += supportMaterial.successBonus();
        }
        double cap = config.successChanceCap();
        if (supportMaterial != null && supportMaterial.successCap() > 0D) {
            cap = Math.min(cap, supportMaterial.successCap());
        }
        return Numbers.clamp(chance, 0D, cap);
    }

    public FailureResolution resolveFailure(AppConfig config,
            int currentStar,
            int crackLevel,
            boolean protectionApplied) {
        if (config == null) {
            return new FailureResolution(currentStar, crackLevel, false, protectionApplied);
        }
        int targetStar = currentStar + 1;
        int crackGain = targetStar <= 8 ? 1 : 2;
        boolean downgrade = targetStar >= 6 && !protectionApplied;
        int resultingStar = downgrade ? Math.max(0, currentStar - 1) : currentStar;
        int resultingCrack = Numbers.clamp(crackLevel + crackGain, 0, config.maxCrack());
        return new FailureResolution(resultingStar, resultingCrack, downgrade, protectionApplied);
    }

    public long calculateCurrencyCost(StrengthenProfile profile, int targetStar) {
        if (profile == null) {
            return 0L;
        }
        long factor = (long) (targetStar + 1) * (targetStar + 1);
        return Math.max(0L, profile.baseCost() * factor);
    }
}
