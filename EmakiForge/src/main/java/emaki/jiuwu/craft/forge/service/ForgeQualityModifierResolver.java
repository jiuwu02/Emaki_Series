package emaki.jiuwu.craft.forge.service;

import java.util.Collection;

import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;

final class ForgeQualityModifierResolver {

    QualitySettings.QualityTier applyModifiers(QualitySettings settings,
            QualitySettings.QualityTier baseTier,
            Collection<ForgeMaterial.QualityModifier> modifiers) {
        if (settings == null) {
            return baseTier;
        }
        QualitySettings.QualityTier highestForce = null;
        QualitySettings.QualityTier highestMinimum = null;
        if (modifiers != null) {
            for (ForgeMaterial.QualityModifier modifier : modifiers) {
                if (modifier == null) {
                    continue;
                }
                QualitySettings.QualityTier tier = settings.findTier(modifier.tier());
                if (tier == null) {
                    continue;
                }
                if (modifier.forceMode()) {
                    highestForce = settings.higherTier(highestForce, tier);
                    continue;
                }
                if (modifier.minimumMode()) {
                    highestMinimum = settings.higherTier(highestMinimum, tier);
                }
            }
        }
        if (highestForce != null) {
            return highestForce;
        }
        if (highestMinimum == null) {
            return baseTier;
        }
        return settings.higherTier(baseTier, highestMinimum);
    }

    boolean hasForceModifier(Collection<ForgeMaterial.QualityModifier> modifiers) {
        if (modifiers == null) {
            return false;
        }
        for (ForgeMaterial.QualityModifier modifier : modifiers) {
            if (modifier != null && modifier.forceMode()) {
                return true;
            }
        }
        return false;
    }
}
