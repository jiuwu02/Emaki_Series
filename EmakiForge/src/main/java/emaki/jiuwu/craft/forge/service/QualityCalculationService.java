package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class QualityCalculationService {

    record QualityRollPlan(QualitySettings.QualityTier rolledTier,
            QualitySettings.QualityTier finalTier,
            boolean forceApplied,
            String qualityName,
            double multiplier) {

    }

    interface GuaranteeCounterStore {

        int counter(UUID playerId, String key);

        void increment(UUID playerId, String key);

        void reset(UUID playerId, String key);
    }

    private record GuaranteePolicy(boolean enabled, int threshold, String minimumName) {

    }

    private final Supplier<QualitySettings> qualitySettingsSupplier;
    private final GuaranteeCounterStore guaranteeCounterStore;
    private final BiFunction<Recipe, GuiItems, List<ForgeMaterial.QualityModifier>> materialQualityModifierResolver;
    private final ForgeQualityModifierResolver qualityModifierResolver = new ForgeQualityModifierResolver();

    QualityCalculationService(Supplier<QualitySettings> qualitySettingsSupplier,
            GuaranteeCounterStore guaranteeCounterStore,
            BiFunction<Recipe, GuiItems, List<ForgeMaterial.QualityModifier>> materialQualityModifierResolver) {
        this.qualitySettingsSupplier = qualitySettingsSupplier;
        this.guaranteeCounterStore = guaranteeCounterStore;
        this.materialQualityModifierResolver = materialQualityModifierResolver;
    }

    QualityRollPlan resolveQualityRoll(UUID playerId, Recipe recipe, GuiItems guiItems, String rollKey) {
        QualitySettings settings = settings();
        Recipe.QualityConfig qualityConfig = recipe == null ? Recipe.QualityConfig.defaults() : recipe.quality();
        List<QualitySettings.QualityTier> tiers = resolveQualityPool(qualityConfig, settings);
        List<ForgeMaterial.QualityModifier> materialModifiers = resolveMaterialQualityModifiers(recipe, guiItems);
        boolean forceApplied = qualityModifierResolver.hasForceModifier(materialModifiers);
        GuaranteePolicy guaranteePolicy = resolveGuaranteePolicy(qualityConfig, settings);
        QualitySettings.QualityTier rolled;
        if (!forceApplied && guaranteePolicy.enabled() && playerId != null && recipe != null) {
            int counter = guaranteeCounterStore.counter(playerId, recipe.id());
            if (counter >= guaranteePolicy.threshold() - 1) {
                QualitySettings.QualityTier guaranteed = findMinimumTier(qualityConfig.customPool(), guaranteePolicy.minimumName());
                if (guaranteed == null) {
                    guaranteed = settings.minimumTier();
                }
                QualitySettings.QualityTier resolved = qualityModifierResolver.applyModifiers(settings, guaranteed, materialModifiers);
                return new QualityRollPlan(guaranteed, resolved, false, resolved.name(), resolved.multiplier());
            }
        }
        rolled = deterministicWeightedTier(tiers, rollKey);
        if (rolled == null) {
            rolled = settings.defaultTier();
        }
        QualitySettings.QualityTier resolved = qualityModifierResolver.applyModifiers(settings, rolled, materialModifiers);
        return new QualityRollPlan(rolled, resolved, forceApplied, resolved.name(), resolved.multiplier());
    }

    void applyGuaranteeOutcome(UUID playerId, Recipe recipe, QualitySettings.QualityTier rolledTier, boolean forceApplied) {
        if (playerId == null || recipe == null || rolledTier == null || forceApplied) {
            return;
        }
        QualitySettings settings = settings();
        GuaranteePolicy guaranteePolicy = resolveGuaranteePolicy(recipe.quality(), settings);
        if (!guaranteePolicy.enabled()) {
            return;
        }
        int counter = guaranteeCounterStore.counter(playerId, recipe.id());
        if (counter >= guaranteePolicy.threshold() - 1) {
            guaranteeCounterStore.reset(playerId, recipe.id());
            return;
        }
        List<QualitySettings.QualityTier> tiers = resolveQualityPool(recipe.quality(), settings);
        QualitySettings.QualityTier minimumTier = findMinimumTier(recipe.quality().customPool(), guaranteePolicy.minimumName());
        if (minimumTier == null) {
            minimumTier = settings.minimumTier();
        }
        if (tierIndex(tiers, rolledTier) < tierIndex(tiers, minimumTier)) {
            guaranteeCounterStore.increment(playerId, recipe.id());
        } else {
            guaranteeCounterStore.reset(playerId, recipe.id());
        }
    }

    private QualitySettings settings() {
        QualitySettings settings = qualitySettingsSupplier == null ? null : qualitySettingsSupplier.get();
        return settings == null ? QualitySettings.defaults() : settings;
    }

    private List<QualitySettings.QualityTier> resolveQualityPool(Recipe.QualityConfig qualityConfig, QualitySettings settings) {
        List<QualitySettings.QualityTier> tiers = new ArrayList<>();
        if (qualityConfig != null && qualityConfig.enabled() && !qualityConfig.customPool().isEmpty()) {
            for (String entry : qualityConfig.customPool()) {
                QualitySettings.QualityTier tier = QualitySettings.QualityTier.fromString(entry);
                if (tier != null) {
                    tiers.add(tier);
                }
            }
        }
        if (tiers.isEmpty()) {
            tiers.addAll(settings.tiers());
        }
        return tiers;
    }

    private GuaranteePolicy resolveGuaranteePolicy(Recipe.QualityConfig qualityConfig, QualitySettings settings) {
        boolean enabled = qualityConfig != null && qualityConfig.enabled() && qualityConfig.guaranteeEnabled();
        int threshold = qualityConfig == null ? 0 : qualityConfig.guaranteeAttempts();
        String minimum = qualityConfig == null ? "" : qualityConfig.guaranteeMinimum();
        if (!enabled && settings.guaranteeEnabled()) {
            enabled = true;
            threshold = settings.guaranteeThreshold();
            minimum = settings.minimumTier().name();
        }
        return new GuaranteePolicy(enabled, Math.max(1, threshold), minimum);
    }

    private List<ForgeMaterial.QualityModifier> resolveMaterialQualityModifiers(Recipe recipe, GuiItems guiItems) {
        List<ForgeMaterial.QualityModifier> modifiers = materialQualityModifierResolver == null
                ? List.of()
                : materialQualityModifierResolver.apply(recipe, guiItems);
        return modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    private QualitySettings.QualityTier deterministicWeightedTier(List<QualitySettings.QualityTier> tiers, String rollKey) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        double totalWeight = 0D;
        for (QualitySettings.QualityTier tier : tiers) {
            if (tier != null && tier.weight() > 0D) {
                totalWeight += tier.weight();
            }
        }
        if (totalWeight <= 0D) {
            return null;
        }
        double ratio = deterministicRatio(rollKey);
        double roll = ratio * totalWeight;
        double cumulative = 0D;
        for (QualitySettings.QualityTier tier : tiers) {
            if (tier == null || tier.weight() <= 0D) {
                continue;
            }
            cumulative += tier.weight();
            if (roll <= cumulative) {
                return tier;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    private double deterministicRatio(String rollKey) {
        String signature = SignatureUtil.sha256(Texts.isBlank(rollKey) ? "forge" : rollKey);
        String sample = signature.substring(0, Math.min(12, signature.length()));
        try {
            long value = Long.parseLong(sample, 16);
            long max = (1L << (sample.length() * 4)) - 1L;
            return max <= 0L ? 0D : value / (double) max;
        } catch (Exception ignored) {
            return 0.5D;
        }
    }

    private QualitySettings.QualityTier findMinimumTier(List<String> pool, String minimumName) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        QualitySettings.QualityTier first = null;
        for (String entry : pool) {
            QualitySettings.QualityTier tier = QualitySettings.QualityTier.fromString(entry);
            if (tier == null) {
                continue;
            }
            if (first == null) {
                first = tier;
            }
            if (Texts.lower(tier.name()).equals(Texts.lower(minimumName))) {
                return tier;
            }
        }
        return first;
    }

    private int tierIndex(List<QualitySettings.QualityTier> tiers, QualitySettings.QualityTier target) {
        for (int index = 0; index < tiers.size(); index++) {
            if (tiers.get(index).name().equals(target.name())) {
                return index;
            }
        }
        return -1;
    }
}
