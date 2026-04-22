package emaki.jiuwu.craft.strengthen.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    private final int localBroadcastRadius;
    private final Set<Integer> localBroadcastStars;
    private final Set<Integer> globalBroadcastStars;
    private final Map<Integer, Double> successRates;

    public AppConfig(String language,
            String configVersion,
            int localBroadcastRadius,
            List<Integer> localBroadcastStars,
            List<Integer> globalBroadcastStars,
            Map<Integer, Double> successRates) {
        super(language, configVersion, "3.3.0");
        this.localBroadcastRadius = Math.max(1, localBroadcastRadius);
        this.localBroadcastStars = toStarSet(localBroadcastStars);
        this.globalBroadcastStars = toStarSet(globalBroadcastStars);
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
        return new AppConfig("zh_CN", "3.3.0", 48, List.of(8), List.of(10, 12), defaults);
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
