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
        super(language, configVersion, "4.5.9");
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
