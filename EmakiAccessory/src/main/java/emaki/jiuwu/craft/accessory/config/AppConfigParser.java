package emaki.jiuwu.craft.accessory.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * Parses {@code config.yml} into an {@link AppConfig}.
 *
 * <p>Every read passes an explicit default, because {@code YamlSection}'s single-argument getters
 * return boxed types defaulting to {@code null} and would unbox into a {@link NullPointerException}.
 */
public final class AppConfigParser {

    private AppConfigParser() {
    }

    /**
     * Parses a configuration document.
     *
     * @param section the loaded document; {@code null} yields the defaults
     * @return the parsed configuration
     */
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
