package emaki.jiuwu.craft.skills.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    private final boolean releaseDefaultData;
    private final int defaultSlotCount;
    private final CastModeSettings castMode;
    private final CastTimingSettings castTiming;
    private final ActionBarSettings actionBar;
    private final Map<String, TriggerConfig> triggers;
    private final Map<String, TriggerConfig> passiveTriggers;
    private final PassiveTriggerSettings passiveTriggerSettings;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            int defaultSlotCount,
            CastModeSettings castMode,
            CastTimingSettings castTiming,
            ActionBarSettings actionBar,
            Map<String, TriggerConfig> triggers,
            Map<String, TriggerConfig> passiveTriggers,
            PassiveTriggerSettings passiveTriggerSettings) {
        super(language, configVersion, "1.0.0");
        this.releaseDefaultData = releaseDefaultData;
        this.defaultSlotCount = Math.max(1, defaultSlotCount);
        this.castMode = castMode == null ? CastModeSettings.defaults() : castMode;
        this.castTiming = castTiming == null ? CastTimingSettings.defaults() : castTiming;
        this.actionBar = actionBar == null ? ActionBarSettings.defaults() : actionBar;
        this.triggers = triggers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(triggers));
        this.passiveTriggers = passiveTriggers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(passiveTriggers));
        this.passiveTriggerSettings = passiveTriggerSettings == null
                ? PassiveTriggerSettings.defaults()
                : passiveTriggerSettings;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                "1.0.0",
                true,
                3,
                CastModeSettings.defaults(),
                CastTimingSettings.defaults(),
                ActionBarSettings.defaults(),
                Map.of(),
                Map.of(),
                PassiveTriggerSettings.defaults()
        );
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public int defaultSlotCount() {
        return defaultSlotCount;
    }

    public CastModeSettings castMode() {
        return castMode;
    }

    public CastTimingSettings castTiming() {
        return castTiming;
    }

    public ActionBarSettings actionBar() {
        return actionBar;
    }

    public Map<String, TriggerConfig> triggers() {
        return triggers;
    }

    public Map<String, TriggerConfig> passiveTriggers() {
        return passiveTriggers;
    }

    public PassiveTriggerSettings passiveTriggerSettings() {
        return passiveTriggerSettings;
    }

    public record CastModeSettings(String entryKey, boolean restoreLastStateOnJoin) {

        public CastModeSettings {
            entryKey = "f";
        }

        public static CastModeSettings defaults() {
            return new CastModeSettings("f", true);
        }
    }

    public record CastTimingSettings(long forcedGlobalCastDelayTicks) {

        public CastTimingSettings {
            forcedGlobalCastDelayTicks = Math.max(0L, forcedGlobalCastDelayTicks);
        }

        public static CastTimingSettings defaults() {
            return new CastTimingSettings(0L);
        }
    }

    public record ActionBarSettings(boolean enabled,
            int refreshIntervalTicks,
            String templateCastMode,
            String templateIdle) {

        public ActionBarSettings {
            refreshIntervalTicks = Math.max(1, refreshIntervalTicks);
            templateCastMode = templateCastMode == null || templateCastMode.isBlank()
                    ? "&aCast Mode &7| {slot_display}" : templateCastMode;
            templateIdle = templateIdle == null || templateIdle.isBlank()
                    ? "&7Idle" : templateIdle;
        }

        public static ActionBarSettings defaults() {
            return new ActionBarSettings(true, 10,
                    "&aCast Mode &7| {slot_display}",
                    "&7Idle");
        }
    }

    public record TriggerConfig(String displayName, boolean enabled, List<String> incompatibleWith) {

        public TriggerConfig {
            displayName = displayName == null || displayName.isBlank() ? "Unknown" : displayName;
            incompatibleWith = incompatibleWith == null ? List.of() : List.copyOf(incompatibleWith);
        }
    }

    public record PassiveTriggerSettings(long timerIntervalTicks) {

        public PassiveTriggerSettings {
            timerIntervalTicks = Math.max(1L, timerIntervalTicks);
        }

        public static PassiveTriggerSettings defaults() {
            return new PassiveTriggerSettings(20L);
        }
    }
}
