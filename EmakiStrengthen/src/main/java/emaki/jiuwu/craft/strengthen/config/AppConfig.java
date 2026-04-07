package emaki.jiuwu.craft.strengthen.config;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.math.Numbers;

public final class AppConfig {

    private final String language;
    private final String configVersion;
    private final boolean releaseDefaultData;
    private final String defaultGuiTemplate;
    private final int maxStar;
    private final int maxCrack;
    private final double crackChanceBonusPerLevel;
    private final double successChanceCap;
    private final int localBroadcastRadius;
    private final String economyProvider;
    private final String economyCurrencyId;
    private final String economyCurrencyName;
    private final Map<Integer, Double> successRates;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            String defaultGuiTemplate,
            int maxStar,
            int maxCrack,
            double crackChanceBonusPerLevel,
            double successChanceCap,
            int localBroadcastRadius,
            String economyProvider,
            String economyCurrencyId,
            String economyCurrencyName,
            Map<Integer, Double> successRates) {
        this.language = language;
        this.configVersion = configVersion;
        this.releaseDefaultData = releaseDefaultData;
        this.defaultGuiTemplate = defaultGuiTemplate;
        this.maxStar = Math.max(1, maxStar);
        this.maxCrack = Math.max(0, maxCrack);
        this.crackChanceBonusPerLevel = Math.max(0D, crackChanceBonusPerLevel);
        this.successChanceCap = Numbers.clamp(successChanceCap, 0D, 100D);
        this.localBroadcastRadius = Math.max(1, localBroadcastRadius);
        this.economyProvider = economyProvider;
        this.economyCurrencyId = economyCurrencyId;
        this.economyCurrencyName = economyCurrencyName;
        this.successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
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
        return new AppConfig(
                "zh_CN",
                "1.0",
                true,
                "strengthen_gui",
                12,
                4,
                5D,
                90D,
                48,
                "auto",
                "",
                "金币",
                defaults
        );
    }

    public double successRateForTargetStar(int targetStar) {
        return successRates.getOrDefault(targetStar, 0D);
    }

    public String language() {
        return language;
    }

    public String configVersion() {
        return configVersion;
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public String defaultGuiTemplate() {
        return defaultGuiTemplate;
    }

    public int maxStar() {
        return maxStar;
    }

    public int maxCrack() {
        return maxCrack;
    }

    public double crackChanceBonusPerLevel() {
        return crackChanceBonusPerLevel;
    }

    public double successChanceCap() {
        return successChanceCap;
    }

    public int localBroadcastRadius() {
        return localBroadcastRadius;
    }

    public String economyProvider() {
        return economyProvider;
    }

    public String economyCurrencyId() {
        return economyCurrencyId;
    }

    public String economyCurrencyName() {
        return economyCurrencyName;
    }

    public Map<Integer, Double> successRates() {
        return successRates;
    }
}
