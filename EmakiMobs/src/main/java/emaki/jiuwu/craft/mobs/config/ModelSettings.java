package emaki.jiuwu.craft.mobs.config;

import java.util.Map;

public record ModelSettings(
        String api,
        double viewDistance,
        double lodNear,
        double lodMid,
        double lodFar,
        int movementCheckIntervalTicks,
        int deathDetachDelayTicks,
        double walkSpeedThreshold,
        double runSpeedThreshold,
        Map<String, Integer> transitionFadeTicks
) {

    public static final String API_AUTO = "auto";
    public static final String API_MODEL_ENGINE = "model_engine";
    public static final String API_BETTER_MODEL = "better_model";

    public static ModelSettings defaults() {
        return new ModelSettings(API_AUTO, 48.0, 16.0, 32.0, 48.0, 5, 40, 0.12, 0.28,
                Map.of("idle", 5, "walk", 5, "run", 5, "death", 0, "hurt", 2,
                        "attack", 3, "skill", 3, "interact", 3));
    }

    public boolean validApi() {
        return API_AUTO.equals(api) || API_MODEL_ENGINE.equals(api) || API_BETTER_MODEL.equals(api);
    }

    public int fadeTicks(String animation) {
        return transitionFadeTicks.getOrDefault(animation, 0);
    }
}
