package emaki.jiuwu.craft.strengthen.config;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    private final int localBroadcastRadius;
    private final Map<Integer, Double> successRates;

    public AppConfig(String language,
            String configVersion,
            int localBroadcastRadius,
            Map<Integer, Double> successRates) {
        super(language, configVersion, "3.2.0");
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
        return new AppConfig("zh_CN", "3.2.0", 48, defaults);
    }

    public int localBroadcastRadius() {
        return localBroadcastRadius;
    }

    public Map<Integer, Double> successRates() {
        return successRates;
    }
}
