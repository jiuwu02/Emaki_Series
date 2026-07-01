package emaki.jiuwu.craft.codex.config;

import java.util.List;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

/**
 * Parsed main configuration for EmakiCodex. Immutable snapshot rebuilt on every reload.
 */
public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "1.0.0";

    private final boolean releaseDefaultData;

    private final boolean recipeBridgeEnabled;
    private final boolean defaultUnlockAll;
    private final boolean syncOnJoin;
    private final boolean resyncOnReload;
    private final List<String> globalBlacklist;
    private final List<String> unlockWhitelist;
    private final boolean channelVanillaBook;
    private final boolean channelPacketEvents;
    private final boolean channelJeiMessage;

    private final boolean advancementEnabled;
    private final String advancementPlatform;
    private final boolean announceDefault;
    private final boolean removeOnDisable;
    private final boolean packetCoordinates;

    private final int autoSaveIntervalSeconds;
    private final boolean opBypass;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            boolean recipeBridgeEnabled,
            boolean defaultUnlockAll,
            boolean syncOnJoin,
            boolean resyncOnReload,
            List<String> globalBlacklist,
            List<String> unlockWhitelist,
            boolean channelVanillaBook,
            boolean channelPacketEvents,
            boolean channelJeiMessage,
            boolean advancementEnabled,
            String advancementPlatform,
            boolean announceDefault,
            boolean removeOnDisable,
            boolean packetCoordinates,
            int autoSaveIntervalSeconds,
            boolean opBypass) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.recipeBridgeEnabled = recipeBridgeEnabled;
        this.defaultUnlockAll = defaultUnlockAll;
        this.syncOnJoin = syncOnJoin;
        this.resyncOnReload = resyncOnReload;
        this.globalBlacklist = globalBlacklist == null ? List.of() : List.copyOf(globalBlacklist);
        this.unlockWhitelist = unlockWhitelist == null ? List.of() : List.copyOf(unlockWhitelist);
        this.channelVanillaBook = channelVanillaBook;
        this.channelPacketEvents = channelPacketEvents;
        this.channelJeiMessage = channelJeiMessage;
        this.advancementEnabled = advancementEnabled;
        this.advancementPlatform = advancementPlatform == null || advancementPlatform.isBlank()
                ? "unsafe" : advancementPlatform;
        this.announceDefault = announceDefault;
        this.removeOnDisable = removeOnDisable;
        this.packetCoordinates = packetCoordinates;
        this.autoSaveIntervalSeconds = Math.max(0, autoSaveIntervalSeconds);
        this.opBypass = opBypass;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                true,
                true,
                true,
                true,
                true,
                List.of(),
                List.of("minecraft:crafting_table"),
                true,
                true,
                false,
                true,
                "unsafe",
                false,
                true,
                true,
                300,
                false
        );
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public boolean recipeBridgeEnabled() {
        return recipeBridgeEnabled;
    }

    public boolean defaultUnlockAll() {
        return defaultUnlockAll;
    }

    public boolean syncOnJoin() {
        return syncOnJoin;
    }

    public boolean resyncOnReload() {
        return resyncOnReload;
    }

    public List<String> globalBlacklist() {
        return globalBlacklist;
    }

    public List<String> unlockWhitelist() {
        return unlockWhitelist;
    }

    public boolean channelVanillaBook() {
        return channelVanillaBook;
    }

    public boolean channelPacketEvents() {
        return channelPacketEvents;
    }

    public boolean channelJeiMessage() {
        return channelJeiMessage;
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

    public int autoSaveIntervalSeconds() {
        return autoSaveIntervalSeconds;
    }

    /** {@return the auto-save interval expressed in server ticks} */
    public long autoSaveIntervalTicks() {
        return autoSaveIntervalSeconds * 20L;
    }

    public boolean opBypass() {
        return opBypass;
    }
}
