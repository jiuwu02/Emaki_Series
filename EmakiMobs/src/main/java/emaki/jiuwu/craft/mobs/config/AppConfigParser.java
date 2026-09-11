package emaki.jiuwu.craft.mobs.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AppConfigParser {

    private static final List<String> TRANSITION_ANIMATIONS =
            List.of("idle", "walk", "run", "death", "hurt", "attack", "skill", "interact");

    private AppConfigParser() {
    }

    public static AppConfig parse(YamlSection section) {
        if (section == null) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        return new AppConfig(
                section.getString("language", defaults.language()),
                section.getString("version", AppConfig.CURRENT_VERSION),
                section.getBoolean("release_default_data", defaults.releaseDefaultData()),
                section.getInt("drain_timeout_seconds", defaults.drainTimeoutSeconds()),
                parseModel(section.getSection("model"), defaults.model()));
    }

    private static ModelSettings parseModel(YamlSection section, ModelSettings defaults) {
        if (section == null) {
            return defaults;
        }
        String api = section.getString("api", defaults.api()).trim().toLowerCase(Locale.ROOT);
        double viewDistance = section.getDouble("view_distance", defaults.viewDistance());
        YamlSection lodSection = section.getSection("lod");
        double lodNear = lodSection == null ? defaults.lodNear() : lodSection.getDouble("near", defaults.lodNear());
        double lodMid = lodSection == null ? defaults.lodMid() : lodSection.getDouble("mid", defaults.lodMid());
        double lodFar = lodSection == null ? defaults.lodFar() : lodSection.getDouble("far", defaults.lodFar());
        int movementInterval = section.getInt("movement_check_interval_ticks",
                defaults.movementCheckIntervalTicks());
        int deathDetachDelay = section.getInt("death_detach_delay_ticks", defaults.deathDetachDelayTicks());
        double walkThreshold = section.getDouble("walk_speed_threshold", defaults.walkSpeedThreshold());
        double runThreshold = section.getDouble("run_speed_threshold", defaults.runSpeedThreshold());
        Map<String, Integer> fades = new LinkedHashMap<>();
        YamlSection transitions = section.getSection("transitions");
        for (String animation : TRANSITION_ANIMATIONS) {
            int fallback = defaults.fadeTicks(animation);
            int value = transitions == null
                    ? fallback
                    : transitions.getInt(animation + "_fade_ticks", fallback);
            fades.put(animation, Math.max(0, value));
        }
        return new ModelSettings(api, viewDistance, lodNear, lodMid, lodFar,
                Math.max(1, movementInterval), Math.max(0, deathDetachDelay),
                walkThreshold, runThreshold, Map.copyOf(fades));
    }
}
