package emaki.jiuwu.craft.mobs.config;

public record AppConfig(
        String language,
        String version,
        boolean releaseDefaultData,
        int drainTimeoutSeconds
) {
    static final String CURRENT_VERSION = "1.0.0";

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", CURRENT_VERSION, true, 30);
    }
}
