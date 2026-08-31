package emaki.jiuwu.craft.attribute.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record DamageIndicatorConfig(boolean enabled,
        long mergeWindowMs,
        int maxPerTargetPerSecond,
        boolean visibleToInvolved,
        Set<String> ignoredCauses,
        TriggerSettings defaultSettings,
        Map<String, TriggerSettings> triggers) {

    public static final String TRIGGER_NORMAL = "normal";
    public static final String TRIGGER_CRITICAL = "critical";
    public static final String TRIGGER_HEAL = "heal";
    public static final String TRIGGER_DODGE = "dodge";

    private static final List<String> TRIGGER_IDS =
            List.of(TRIGGER_NORMAL, TRIGGER_CRITICAL, TRIGGER_HEAL, TRIGGER_DODGE);

    public DamageIndicatorConfig {
        mergeWindowMs = Math.max(0L, mergeWindowMs);
        maxPerTargetPerSecond = Math.max(0, maxPerTargetPerSecond);
        ignoredCauses = ignoredCauses == null ? Set.of() : Set.copyOf(ignoredCauses);
        defaultSettings = defaultSettings == null ? TriggerSettings.defaults() : defaultSettings;
        triggers = triggers == null ? Map.of() : Map.copyOf(triggers);
    }

    public static DamageIndicatorConfig defaults() {
        TriggerSettings defaults = TriggerSettings.defaults();
        Map<String, TriggerSettings> triggers = new LinkedHashMap<>();
        for (String id : TRIGGER_IDS) {
            triggers.put(id, defaults);
        }
        return new DamageIndicatorConfig(
                true,
                500L,
                4,
                true,
                Set.of("FIRE_TICK", "POISON", "WITHER", "DROWNING", "FREEZE", "SUFFOCATION", "STARVATION"),
                defaults,
                triggers
        );
    }

    public static DamageIndicatorConfig fromConfig(YamlSection section) {
        if (section == null) {
            return defaults();
        }
        DamageIndicatorConfig fallback = defaults();
        TriggerSettings defaults = TriggerSettings.fromConfig(section, fallback.defaultSettings());
        YamlSection triggersSection = section.getSection("triggers");
        Map<String, TriggerSettings> triggers = new LinkedHashMap<>();
        for (String id : TRIGGER_IDS) {
            triggers.put(id, readTrigger(triggersSection, id, defaults));
        }
        return new DamageIndicatorConfig(
                section.getBoolean("enabled", fallback.enabled()),
                section.getInt("merge_window_ms", (int) fallback.mergeWindowMs()),
                section.getInt("max_per_target_per_second", fallback.maxPerTargetPerSecond()),
                "involved".equalsIgnoreCase(section.getString("visible_to", "involved")),
                normalizeCauses(section.getStringList("ignored_causes"), fallback.ignoredCauses()),
                defaults,
                triggers
        );
    }

    public TriggerSettings settingsFor(String triggerId) {
        TriggerSettings settings = triggerId == null ? null : triggers.get(triggerId);
        return settings == null ? defaultSettings : settings;
    }

    public boolean isIgnored(EntityDamageEvent.DamageCause cause) {
        return cause != null && ignoredCauses.contains(cause.name());
    }

    private static TriggerSettings readTrigger(YamlSection triggersSection,
            String id,
            TriggerSettings defaults) {
        if (triggersSection == null) {
            return defaults;
        }
        Object raw = triggersSection.get(id);
        if (raw instanceof Boolean flag) {
            return defaults.withEnabled(flag);
        }
        YamlSection triggerSection = triggersSection.getSection(id);
        if (triggerSection == null) {
            return defaults;
        }
        return TriggerSettings.fromConfig(triggerSection, defaults);
    }

    private static Set<String> normalizeCauses(List<String> raw, Set<String> fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry != null && !entry.isBlank()) {
                result.add(entry.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static double doubleOf(YamlSection section, String key, double fallback) {
        Object value = section == null ? null : section.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    public record TriggerSettings(boolean enabled,
            int lifetimeTicks,
            SpawnSettings spawn,
            MotionSettings motion) {

        public TriggerSettings {
            lifetimeTicks = Math.max(1, lifetimeTicks);
            spawn = spawn == null ? SpawnSettings.defaults() : spawn;
            motion = motion == null ? MotionSettings.defaults() : motion;
        }

        public static TriggerSettings defaults() {
            return new TriggerSettings(true, 24, SpawnSettings.defaults(), MotionSettings.defaults());
        }

        static TriggerSettings fromConfig(YamlSection section, TriggerSettings fallback) {
            if (section == null) {
                return fallback;
            }
            return new TriggerSettings(
                    section.getBoolean("enabled", fallback.enabled()),
                    section.getInt("lifetime_ticks", fallback.lifetimeTicks()),
                    SpawnSettings.fromConfig(section.getSection("spawn"), fallback.spawn()),
                    MotionSettings.fromConfig(section.getSection("motion"), fallback.motion())
            );
        }

        TriggerSettings withEnabled(boolean value) {
            return new TriggerSettings(value, lifetimeTicks, spawn, motion);
        }
    }

    public record SpawnSettings(Vector3 offset, Vector3 randomOffset) {

        public SpawnSettings {
            offset = offset == null ? new Vector3(0D, 2D, 0D) : offset;
            randomOffset = randomOffset == null ? Vector3.ZERO : randomOffset;
        }

        public static SpawnSettings defaults() {
            return new SpawnSettings(new Vector3(0D, 2D, 0D), new Vector3(0.4D, 0.15D, 0.4D));
        }

        static SpawnSettings fromConfig(YamlSection section, SpawnSettings fallback) {
            if (section == null) {
                return fallback;
            }
            return new SpawnSettings(
                    Vector3.fromConfig(section.getSection("offset"), fallback.offset()),
                    Vector3.fromConfig(section.getSection("random_offset"), fallback.randomOffset())
            );
        }
    }

    public record MotionSettings(boolean enabled,
            int stepTicks,
            double speed,
            double gravity,
            DirectionSettings direction,
            ScaleSettings scale) {

        public MotionSettings {
            stepTicks = Math.max(1, stepTicks);
            speed = Math.max(0D, speed);
            direction = direction == null ? DirectionSettings.defaults() : direction;
            scale = scale == null ? ScaleSettings.defaults() : scale;
        }

        public static MotionSettings defaults() {
            return new MotionSettings(true, 2, 0.30D, -0.030D,
                    DirectionSettings.defaults(), ScaleSettings.defaults());
        }

        static MotionSettings fromConfig(YamlSection section, MotionSettings fallback) {
            if (section == null) {
                return fallback;
            }
            return new MotionSettings(
                    section.getBoolean("enabled", fallback.enabled()),
                    section.getInt("step_ticks", fallback.stepTicks()),
                    doubleOf(section, "speed", fallback.speed()),
                    doubleOf(section, "gravity", fallback.gravity()),
                    DirectionSettings.fromConfig(section.getSection("direction"), fallback.direction()),
                    ScaleSettings.fromConfig(section.getSection("scale"), fallback.scale())
            );
        }
    }

    public record DirectionSettings(String mode, Vector3 fixed, Range pitch, Range yaw) {

        public static final String MODE_RANDOM = "random";

        public static final String MODE_FIXED = "fixed";

        public static final String MODE_AWAY_FROM_ATTACKER = "away_from_attacker";

        public DirectionSettings {
            mode = normalizeMode(mode);
            fixed = fixed == null ? new Vector3(0D, 1D, 0D) : fixed;
            pitch = pitch == null ? new Range(55D, 85D) : pitch;
            yaw = yaw == null ? new Range(-180D, 180D) : yaw;
        }

        public static DirectionSettings defaults() {
            return new DirectionSettings(MODE_RANDOM, new Vector3(0D, 1D, 0D),
                    new Range(55D, 85D), new Range(-180D, 180D));
        }

        static DirectionSettings fromConfig(YamlSection section, DirectionSettings fallback) {
            if (section == null) {
                return fallback;
            }
            return new DirectionSettings(
                    section.getString("mode", fallback.mode()),
                    Vector3.fromConfig(section.getSection("fixed"), fallback.fixed()),
                    Range.fromConfig(section.getSection("pitch"), fallback.pitch()),
                    Range.fromConfig(section.getSection("yaw"), fallback.yaw())
            );
        }

        private static String normalizeMode(String raw) {
            if (raw == null || raw.isBlank()) {
                return MODE_RANDOM;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case MODE_FIXED -> MODE_FIXED;
                case MODE_AWAY_FROM_ATTACKER -> MODE_AWAY_FROM_ATTACKER;
                default -> MODE_RANDOM;
            };
        }
    }

    public record ScaleSettings(double popFrom, int popTicks, double shrinkTo, int shrinkTicks) {

        public ScaleSettings {
            popFrom = Math.max(0D, popFrom);
            popTicks = Math.max(0, popTicks);
            shrinkTo = Math.max(0D, shrinkTo);
            shrinkTicks = Math.max(0, shrinkTicks);
        }

        public static ScaleSettings defaults() {
            return new ScaleSettings(0.6D, 3, 0D, 6);
        }

        static ScaleSettings fromConfig(YamlSection section, ScaleSettings fallback) {
            if (section == null) {
                return fallback;
            }
            return new ScaleSettings(
                    doubleOf(section, "pop_from", fallback.popFrom()),
                    section.getInt("pop_ticks", fallback.popTicks()),
                    doubleOf(section, "shrink_to", fallback.shrinkTo()),
                    section.getInt("shrink_ticks", fallback.shrinkTicks())
            );
        }
    }

    public record Vector3(double x, double y, double z) {

        public static final Vector3 ZERO = new Vector3(0D, 0D, 0D);

        static Vector3 fromConfig(YamlSection section, Vector3 fallback) {
            if (section == null) {
                return fallback;
            }
            return new Vector3(
                    doubleOf(section, "x", fallback.x()),
                    doubleOf(section, "y", fallback.y()),
                    doubleOf(section, "z", fallback.z())
            );
        }
    }

    public record Range(double min, double max) {

        public Range {
            if (min > max) {
                double swapped = min;
                min = max;
                max = swapped;
            }
        }

        static Range fromConfig(YamlSection section, Range fallback) {
            if (section == null) {
                return fallback;
            }
            return new Range(
                    doubleOf(section, "min", fallback.min()),
                    doubleOf(section, "max", fallback.max())
            );
        }

        public double resolve() {
            return min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
        }
    }
}
