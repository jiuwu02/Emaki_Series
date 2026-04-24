package emaki.jiuwu.craft.cooking.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_CONFIG_VERSION = "2.1.0";

    private final boolean releaseDefaultData;
    private final String oldDirectory;

    public AppConfig(String language, String configVersion, boolean releaseDefaultData, String oldDirectory) {
        super(language, configVersion, "2.0.0");
        this.releaseDefaultData = releaseDefaultData;
        this.oldDirectory = oldDirectory == null || oldDirectory.isBlank() ? "old" : oldDirectory.trim();
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", CURRENT_CONFIG_VERSION, true, "old");
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public String oldDirectory() {
        return oldDirectory;
    }
}
