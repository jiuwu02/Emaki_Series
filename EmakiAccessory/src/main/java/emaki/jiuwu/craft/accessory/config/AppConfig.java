package emaki.jiuwu.craft.accessory.config;

public record AppConfig(String version,
        String language,
        boolean releaseDefaultData,
        boolean dropOnDeath,
        boolean unique,
        int autosaveSeconds,
        int drainTimeoutSeconds) {

    public static AppConfig defaults() {
        return new AppConfig("1.0.0", "zh_CN", true, false, true, 300, 10);
    }

    public AppConfig {
        version = version == null ? "" : version;
        language = language == null || language.isBlank() ? "zh_CN" : language;
        autosaveSeconds = Math.max(0, autosaveSeconds);
        drainTimeoutSeconds = Math.max(1, drainTimeoutSeconds);
    }
}
