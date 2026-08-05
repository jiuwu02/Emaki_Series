package emaki.jiuwu.craft.accessory.config;

/**
 * Runtime configuration for EmakiAccessory.
 *
 * @param version            configuration file version, synchronised with the module POM
 * @param language           language file name under {@code lang/}
 * @param releaseDefaultData whether bundled example data is written on first start
 * @param dropOnDeath        whether accessories drop on death; a single global switch by design
 * @param unique             whether the same accessory may occupy only one slot
 * @param autosaveSeconds    interval between periodic saves; {@code 0} disables the timer
 * @param drainTimeoutSeconds how long shutdown waits for pending writes
 */
public record AppConfig(String version,
        String language,
        boolean releaseDefaultData,
        boolean dropOnDeath,
        boolean unique,
        int autosaveSeconds,
        int drainTimeoutSeconds) {

    /** {@return the built-in defaults used before the file is read and when it fails to parse} */
    public static AppConfig defaults() {
        return new AppConfig("1.0.0", "zh_CN", true, false, true, 300, 10);
    }

    /** Canonical constructor; clamps the two timing values to sane ranges. */
    public AppConfig {
        version = version == null ? "" : version;
        language = language == null || language.isBlank() ? "zh_CN" : language;
        autosaveSeconds = Math.max(0, autosaveSeconds);
        drainTimeoutSeconds = Math.max(1, drainTimeoutSeconds);
    }
}
