package emaki.jiuwu.craft.strengthen.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AppConfig {

    private final String language;
    private final String configVersion;
    private final int localBroadcastRadius;
    private final Map<Integer, Double> successRates;

    public AppConfig(String language,
            String configVersion,
            int localBroadcastRadius,
            Map<Integer, Double> successRates) {
        this.language = language;
        this.configVersion = configVersion;
        this.localBroadcastRadius = Math.max(1, localBroadcastRadius);
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
        return new AppConfig("zh_CN", "2.1.0", 48, defaults);
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

    public int localBroadcastRadius() {
        return localBroadcastRadius;
    }

    public Map<Integer, Double> successRates() {
        return successRates;
    }
}
