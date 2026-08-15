package emaki.jiuwu.craft.accessory.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class AppConfigParser {

    private AppConfigParser() {
    }

    public static AppConfig parse(YamlSection section) {
        AppConfig defaults = AppConfig.defaults();
        if (section == null) {
            return defaults;
        }
        return new AppConfig(
                section.getString("version", defaults.version()),
                section.getString("language", defaults.language()),
                section.getBoolean("release_default_data", defaults.releaseDefaultData()),
                section.getBoolean("drop_on_death", defaults.dropOnDeath()),
                section.getBoolean("unique", defaults.unique()),
                section.getInt("persistence.autosave_seconds", defaults.autosaveSeconds()),
                section.getInt("persistence.drain_timeout_seconds", defaults.drainTimeoutSeconds())
        );
    }
}
