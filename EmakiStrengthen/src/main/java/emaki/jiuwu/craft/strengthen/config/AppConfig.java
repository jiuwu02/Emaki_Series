package emaki.jiuwu.craft.strengthen.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    private final boolean releaseDefaultData;
    private final int localBroadcastRadius;
    private final Set<Integer> localBroadcastStars;
    private final Set<Integer> globalBroadcastStars;
    private final Map<Integer, Double> successRates;
    private final int affixMaxLevel;
    private final int affixCapacityMax;
    private final int affixCapacityCostPerLevel;
    private final double affixBonusPerLevel;
    private final boolean enhancementRejectContainerTarget;
    private final EnhancementTuning enhancementTuning;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            int localBroadcastRadius,
            List<Integer> localBroadcastStars,
            List<Integer> globalBroadcastStars,
            Map<Integer, Double> successRates) {
        this(language, configVersion, releaseDefaultData, localBroadcastRadius, localBroadcastStars,
                globalBroadcastStars, successRates, 10, 100, 10, 1.0D);
    }

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            int localBroadcastRadius,
            List<Integer> localBroadcastStars,
            List<Integer> globalBroadcastStars,
            Map<Integer, Double> successRates,
            int affixMaxLevel,
            int affixCapacityMax,
            int affixCapacityCostPerLevel,
            double affixBonusPerLevel) {
        this(language, configVersion, releaseDefaultData, localBroadcastRadius, localBroadcastStars,
                globalBroadcastStars, successRates, affixMaxLevel, affixCapacityMax,
                affixCapacityCostPerLevel, affixBonusPerLevel, false);
    }

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            int localBroadcastRadius,
            List<Integer> localBroadcastStars,
            List<Integer> globalBroadcastStars,
            Map<Integer, Double> successRates,
            int affixMaxLevel,
            int affixCapacityMax,
            int affixCapacityCostPerLevel,
            double affixBonusPerLevel,
            boolean enhancementRejectContainerTarget) {
        this(language, configVersion, releaseDefaultData, localBroadcastRadius, localBroadcastStars,
                globalBroadcastStars, successRates, affixMaxLevel, affixCapacityMax,
                affixCapacityCostPerLevel, affixBonusPerLevel, enhancementRejectContainerTarget,
                EnhancementTuning.inert());
    }

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            int localBroadcastRadius,
            List<Integer> localBroadcastStars,
            List<Integer> globalBroadcastStars,
            Map<Integer, Double> successRates,
            int affixMaxLevel,
            int affixCapacityMax,
            int affixCapacityCostPerLevel,
            double affixBonusPerLevel,
            boolean enhancementRejectContainerTarget,
            EnhancementTuning enhancementTuning) {
        super(language, configVersion, "4.5.9");
        this.enhancementRejectContainerTarget = enhancementRejectContainerTarget;
        this.enhancementTuning = enhancementTuning == null ? EnhancementTuning.inert() : enhancementTuning;
        this.releaseDefaultData = releaseDefaultData;
        this.localBroadcastRadius = Math.max(1, localBroadcastRadius);
        this.localBroadcastStars = toStarSet(localBroadcastStars);
        this.globalBroadcastStars = toStarSet(globalBroadcastStars);
        this.successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
        this.affixMaxLevel = Math.max(0, affixMaxLevel);
        this.affixCapacityMax = Math.max(0, affixCapacityMax);
        this.affixCapacityCostPerLevel = Math.max(0, affixCapacityCostPerLevel);
        this.affixBonusPerLevel = Double.isFinite(affixBonusPerLevel) ? affixBonusPerLevel : 0D;
    }

    public static AppConfig defaults() {
        Map<Integer, Double> defaults = new LinkedHashMap<>();
        defaults.put(1, 100D);
        defaults.put(2, 100D);
        defaults.put(3, 95D);
        defaults.put(4, 85D);
        defaults.put(5, 75D);
        defaults.put(6, 60D);
        defaults.put(7, 45D);
        defaults.put(8, 32D);
        defaults.put(9, 22D);
        defaults.put(10, 14D);
        defaults.put(11, 8D);
        defaults.put(12, 4D);
        return new AppConfig("zh_CN", "4.2.9", true, 48, List.of(8), List.of(10, 12), defaults);
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public int localBroadcastRadius() {
        return localBroadcastRadius;
    }

    public Set<Integer> localBroadcastStars() {
        return localBroadcastStars;
    }

    public Set<Integer> globalBroadcastStars() {
        return globalBroadcastStars;
    }

    public Map<Integer, Double> successRates() {
        return successRates;
    }

    /** {@return 单条词条的强化等级上限；0 表示不限} */
    public int affixMaxLevel() {
        return affixMaxLevel;
    }

    /** {@return 装备的词条容量上限，写入词条层时作为缺省值} */
    public int affixCapacityMax() {
        return affixCapacityMax;
    }

    /** {@return 每提升一级词条占用的容量} */
    public int affixCapacityCostPerLevel() {
        return affixCapacityCostPerLevel;
    }

    /** {@return 每提升一级词条获得的属性增量} */
    public double affixBonusPerLevel() {
        return affixBonusPerLevel;
    }

    public boolean enhancementRejectContainerTarget() {
        return enhancementRejectContainerTarget;
    }

    public EnhancementTuning enhancementTuning() {
        return enhancementTuning;
    }

    public boolean enhancementRejectContainerMaterial() {
        return enhancementTuning.rejectContainerMaterial();
    }

    public double enhancementDiminishingPerLevel() {
        return enhancementTuning.diminishingPerLevel();
    }

    public int enhancementDiminishingStartLevel() {
        return enhancementTuning.diminishingStartLevel();
    }

    public double enhancementCostGrowthPerLevel() {
        return enhancementTuning.costGrowthPerLevel();
    }

    public double enhancementCostGrowthMaxMultiplier() {
        return enhancementTuning.costGrowthMaxMultiplier();
    }

    public double enhancementMinSuccessRate() {
        return enhancementTuning.minSuccessRate();
    }

    public int enhancementFailureDemotionLevels() {
        return enhancementTuning.failureDemotionLevels();
    }

    public int enhancementFailureDemotionFloor() {
        return enhancementTuning.failureDemotionFloor();
    }

    public long enhancementPityRetryIntervalTicks() {
        return enhancementTuning.pityRetryIntervalTicks();
    }

    public int enhancementPityRetryMaxAttempts() {
        return enhancementTuning.pityRetryMaxAttempts();
    }

    public double enhancementMasteryExpPerAttempt() {
        return enhancementTuning.masteryExpPerAttempt();
    }

    public double enhancementMasteryExpPerSuccess() {
        return enhancementTuning.masteryExpPerSuccess();
    }

    public int enhancementMasterySoftCap() {
        return enhancementTuning.masterySoftCap();
    }

    public record EnhancementTuning(boolean rejectContainerMaterial,
            double diminishingPerLevel,
            int diminishingStartLevel,
            double costGrowthPerLevel,
            double costGrowthMaxMultiplier,
            double minSuccessRate,
            int failureDemotionLevels,
            int failureDemotionFloor,
            long pityRetryIntervalTicks,
            int pityRetryMaxAttempts,
            double masteryExpPerAttempt,
            double masteryExpPerSuccess,
            int masterySoftCap) {

        public EnhancementTuning {
            diminishingPerLevel = normalizedRatio(diminishingPerLevel);
            diminishingStartLevel = Math.max(1, diminishingStartLevel);
            costGrowthPerLevel = nonNegative(costGrowthPerLevel);
            costGrowthMaxMultiplier = nonNegative(costGrowthMaxMultiplier);
            minSuccessRate = normalizedRatio(minSuccessRate);
            failureDemotionLevels = Math.max(0, failureDemotionLevels);
            failureDemotionFloor = Math.max(0, failureDemotionFloor);
            pityRetryIntervalTicks = Math.max(0L, pityRetryIntervalTicks);
            pityRetryMaxAttempts = Math.max(0, pityRetryMaxAttempts);
            masteryExpPerAttempt = nonNegative(masteryExpPerAttempt);
            masteryExpPerSuccess = nonNegative(masteryExpPerSuccess);
            masterySoftCap = Math.max(0, masterySoftCap);
        }

        public static EnhancementTuning inert() {
            return new EnhancementTuning(false, 0D, 1, 0D, 0D, 0D, 0, 0, 0L, 0, 0D, 0D, 0);
        }

        private static double normalizedRatio(double value) {
            if (!Double.isFinite(value) || value <= 0D) {
                return 0D;
            }
            return Math.min(1D, value);
        }

        private static double nonNegative(double value) {
            return Double.isFinite(value) && value > 0D ? value : 0D;
        }
    }

    private static Set<Integer> toStarSet(List<Integer> stars) {
        if (stars == null || stars.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer star : stars) {
            if (star != null && star > 0) {
                normalized.add(star);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }
}
