package emaki.jiuwu.craft.attribute.config;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ScriptsConfig(boolean enabled,
        long timeoutMs,
        boolean debug) {

    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    public ScriptsConfig {
        if (timeoutMs <= 0L) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
    }

    public static ScriptsConfig defaults() {
        return new ScriptsConfig(true, DEFAULT_TIMEOUT_MS, false);
    }

    public static ScriptsConfig fromConfig(YamlSection configuration) {
        if (configuration == null) {
            return defaults();
        }
        return new ScriptsConfig(
                Boolean.TRUE.equals(configuration.getBoolean("enabled", true)),
                Numbers.tryParseLong(configuration.get("timeout_ms"), DEFAULT_TIMEOUT_MS),
                Boolean.TRUE.equals(configuration.getBoolean("debug", false))
        );
    }
}
