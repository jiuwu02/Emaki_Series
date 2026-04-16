package emaki.jiuwu.craft.corelib.config;

public abstract class BaseAppConfig {

    private final String language;
    private final String configVersion;

    protected BaseAppConfig(String language, String configVersion, String defaultConfigVersion) {
        this.language = language == null || language.isBlank() ? "zh_CN" : language;
        this.configVersion = configVersion == null || configVersion.isBlank()
                ? defaultConfigVersion
                : configVersion;
    }

    public String language() {
        return language;
    }

    public String configVersion() {
        return configVersion;
    }
}
