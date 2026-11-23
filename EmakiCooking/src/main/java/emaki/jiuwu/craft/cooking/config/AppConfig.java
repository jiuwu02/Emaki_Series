package emaki.jiuwu.craft.cooking.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "3.3.14";

    private final boolean releaseDefaultData;
    private final String storageBackend;

    public AppConfig(String language, String configVersion, boolean releaseDefaultData, String storageBackend) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.storageBackend = storageBackend == null ? "file" : storageBackend;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", CURRENT_VERSION, true, "file");
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public String storageBackend() {
        return storageBackend;
    }

    public boolean databaseBackend() {
        return "database".equalsIgnoreCase(storageBackend);
    }
}
