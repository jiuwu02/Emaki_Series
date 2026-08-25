package emaki.jiuwu.craft.skills.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "2.5.11";

    private final boolean releaseDefaultData;
    private final int defaultSlotCount;
    private final SkillSourceSettings skillSources;
    private final CastModeSettings castMode;
    private final CastTimingSettings castTiming;
    private final ActionBarSettings actionBar;
    private final Map<String, Integer> skillTagEquipLimits;
    private final Map<String, TriggerConfig> triggers;
    private final Map<String, TriggerConfig> passiveTriggers;
    private final PassiveTriggerSettings passiveTriggerSettings;
    private final ScriptEngineSettings scriptEngine;
    private final TriggerSettings triggerSettings;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            int defaultSlotCount,
            SkillSourceSettings skillSources,
            CastModeSettings castMode,
            CastTimingSettings castTiming,
            ActionBarSettings actionBar,
            Map<String, Integer> skillTagEquipLimits,
            Map<String, TriggerConfig> triggers,
            Map<String, TriggerConfig> passiveTriggers,
            PassiveTriggerSettings passiveTriggerSettings,
            ScriptEngineSettings scriptEngine,
            TriggerSettings triggerSettings) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.defaultSlotCount = Math.max(1, defaultSlotCount);
        this.skillSources = skillSources == null ? SkillSourceSettings.defaults() : skillSources;
        this.castMode = castMode == null ? CastModeSettings.defaults() : castMode;
        this.castTiming = castTiming == null ? CastTimingSettings.defaults() : castTiming;
        this.actionBar = actionBar == null ? ActionBarSettings.defaults() : actionBar;
        this.skillTagEquipLimits = skillTagEquipLimits == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(skillTagEquipLimits));
        this.triggers = triggers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(triggers));
        this.passiveTriggers = passiveTriggers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(passiveTriggers));
        this.passiveTriggerSettings = passiveTriggerSettings == null
                ? PassiveTriggerSettings.defaults()
                : passiveTriggerSettings;
        this.scriptEngine = scriptEngine == null ? ScriptEngineSettings.defaults() : scriptEngine;
        this.triggerSettings = triggerSettings == null ? TriggerSettings.defaults() : triggerSettings;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                true,
                3,
                SkillSourceSettings.defaults(),
                CastModeSettings.defaults(),
                CastTimingSettings.defaults(),
                ActionBarSettings.defaults(),
                Map.of(),
                Map.of(),
                Map.of(),
                PassiveTriggerSettings.defaults(),
                ScriptEngineSettings.defaults(),
                TriggerSettings.defaults()
        );
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public int defaultSlotCount() {
        return defaultSlotCount;
    }

    public SkillSourceSettings skillSources() {
        return skillSources;
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

    public Map<String, Integer> skillTagEquipLimits() {
        return skillTagEquipLimits;
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

    public ScriptEngineSettings scriptEngine() {
        return scriptEngine;
    }

    public TriggerSettings triggerSettings() {
        return triggerSettings;
    }

    public record SkillSourceSettings(boolean readLoreSkills,
            boolean readPdcSkills,
            boolean requireLorePdcMatch) {

        public static SkillSourceSettings defaults() {
            return new SkillSourceSettings(true, true, false);
        }
    }

    public record CastModeSettings(String entryKey, boolean enabled, boolean restoreLastStateOnJoin) {

        public CastModeSettings {
            entryKey = "f";
        }

        public static CastModeSettings defaults() {
            return new CastModeSettings("f", true, true);
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
                    ? "&aCast Mode &7| %slot_display%" : templateCastMode;
            templateIdle = templateIdle == null || templateIdle.isBlank()
                    ? "&7Idle" : templateIdle;
        }

        public static ActionBarSettings defaults() {
            return new ActionBarSettings(true, 10,
                    "&aCast Mode &7| %slot_display%",
                    "&7Idle");
        }
    }

    public record TriggerConfig(String displayName, boolean enabled, List<String> incompatibleWith) {

        public TriggerConfig {
            displayName = displayName == null || displayName.isBlank() ? "Unknown" : displayName;
            incompatibleWith = incompatibleWith == null ? List.of() : List.copyOf(incompatibleWith);
        }
    }

    public record TriggerSettings(boolean legacyDispatchCancelledEvents) {

        public static TriggerSettings defaults() {
            return new TriggerSettings(false);
        }
    }

    public record PassiveTriggerSettings(long timerIntervalTicks, long comboTimeoutTicks) {

        public PassiveTriggerSettings {
            timerIntervalTicks = Math.max(1L, timerIntervalTicks);
            comboTimeoutTicks = Math.max(1L, comboTimeoutTicks);
        }

        public PassiveTriggerSettings(long timerIntervalTicks) {
            this(timerIntervalTicks, 60L);
        }

        public static PassiveTriggerSettings defaults() {
            return new PassiveTriggerSettings(20L, 60L);
        }
    }

    public record ScriptEngineSettings(boolean enabled,
            String defaultMode,
            boolean stopOnFailure,
            int maxLinesPerPhase,
            int maxTargetsPerAction,
            boolean debug) {

        public ScriptEngineSettings {
            defaultMode = defaultMode == null || defaultMode.isBlank() ? "native" : defaultMode;
            maxLinesPerPhase = Math.max(1, maxLinesPerPhase);
            maxTargetsPerAction = Math.max(1, maxTargetsPerAction);
        }

        public static ScriptEngineSettings defaults() {
            return new ScriptEngineSettings(true, "native", true, 64, 16, false);
        }
    }
}
