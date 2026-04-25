package emaki.jiuwu.craft.cooking.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "2.1.0";

    private final boolean releaseDefaultData;

    public AppConfig(String language, String configVersion, boolean releaseDefaultData) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", CURRENT_VERSION, true);
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }
}
