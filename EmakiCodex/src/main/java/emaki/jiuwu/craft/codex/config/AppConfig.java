package emaki.jiuwu.craft.codex.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

/**
 * Parsed main configuration for EmakiCodex. Immutable snapshot rebuilt on every reload.
 */
public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "1.0.5";

    private final boolean releaseDefaultData;

    private final boolean advancementEnabled;
    private final String advancementPlatform;
    private final boolean announceDefault;
    private final boolean removeOnDisable;
    private final boolean packetCoordinates;

    private final boolean opBypass;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            boolean advancementEnabled,
            String advancementPlatform,
            boolean announceDefault,
            boolean removeOnDisable,
            boolean packetCoordinates,
            boolean opBypass) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.advancementEnabled = advancementEnabled;
        this.advancementPlatform = advancementPlatform == null || advancementPlatform.isBlank()
                ? "unsafe" : advancementPlatform;
        this.announceDefault = announceDefault;
        this.removeOnDisable = removeOnDisable;
        this.packetCoordinates = packetCoordinates;
        this.opBypass = opBypass;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                true,
                true,
                "unsafe",
                false,
                true,
                true,
                false
        );
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public boolean advancementEnabled() {
        return advancementEnabled;
    }

    public String advancementPlatform() {
        return advancementPlatform;
    }

    public boolean announceDefault() {
        return announceDefault;
    }

    public boolean removeOnDisable() {
        return removeOnDisable;
    }

    /**
     * {@return whether the PacketEvents coordinate channel should inject configured x/y
     * positions into outgoing advancement packets} Only effective when PacketEvents is
     * installed; otherwise per-node {@code x}/{@code y} are ignored and the client
     * auto-lays-out the tree.
     */
    public boolean packetCoordinates() {
        return packetCoordinates;
    }

    public boolean opBypass() {
        return opBypass;
    }
}
