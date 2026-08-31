package emaki.jiuwu.craft.mobs.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class AppConfigParser {

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
                section.getInt("drain_timeout_seconds", defaults.drainTimeoutSeconds()));
    }
}
