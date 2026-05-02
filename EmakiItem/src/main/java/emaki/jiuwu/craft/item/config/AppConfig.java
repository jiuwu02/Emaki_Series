package emaki.jiuwu.craft.item.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    private final boolean releaseDefaultData;

    public AppConfig(String language, String configVersion, boolean releaseDefaultData) {
        super(language, configVersion, "1.0.0");
        this.releaseDefaultData = releaseDefaultData;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", "1.0.0", true);
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }
}
