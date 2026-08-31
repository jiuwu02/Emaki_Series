package emaki.jiuwu.craft.codex.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "1.0.14";

    private final boolean releaseDefaultData;

    private final boolean advancementEnabled;
    private final boolean announceDefault;
    private final boolean removeOnDisable;
    private final boolean packetCoordinates;
    private final boolean advancementTriggersEnabled;

    private final boolean opBypass;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            boolean advancementEnabled,
            boolean announceDefault,
            boolean removeOnDisable,
            boolean packetCoordinates,
            boolean advancementTriggersEnabled,
            boolean opBypass) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.advancementEnabled = advancementEnabled;
        this.announceDefault = announceDefault;
        this.removeOnDisable = removeOnDisable;
        this.packetCoordinates = packetCoordinates;
        this.advancementTriggersEnabled = advancementTriggersEnabled;
        this.opBypass = opBypass;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                true,
                true,
                false,
                true,
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

    public boolean announceDefault() {
        return announceDefault;
    }

    public boolean removeOnDisable() {
        return removeOnDisable;
    }

    public boolean packetCoordinates() {
        return packetCoordinates;
    }

    public boolean advancementTriggersEnabled() {
        return advancementTriggersEnabled;
    }

    public boolean opBypass() {
        return opBypass;
    }
}
