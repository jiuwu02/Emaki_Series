package emaki.jiuwu.craft.attribute.config;

import java.util.Locale;
import java.util.Set;

import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * 伤害飘字设置。
 *
 * <p>飘字的渲染后端与可见范围由 CoreLib 的 {@code display.backend} 决定：
 * 发包后端下 {@link #visibleToInvolved()} 生效，真实体后端下附近玩家均可见。
 */
public record DamageIndicatorConfig(boolean enabled,
        int lifetimeTicks,
        double offsetY,
        double spread,
        long mergeWindowMs,
        int maxPerTargetPerSecond,
        boolean visibleToInvolved,
        Set<String> ignoredCauses,
        boolean normalEnabled,
        boolean criticalEnabled,
        boolean healEnabled,
        boolean dodgeEnabled) {

    public DamageIndicatorConfig {
        lifetimeTicks = Math.max(1, lifetimeTicks);
        offsetY = Math.max(0D, offsetY);
        spread = Math.max(0D, spread);
        mergeWindowMs = Math.max(0L, mergeWindowMs);
        maxPerTargetPerSecond = Math.max(0, maxPerTargetPerSecond);
        ignoredCauses = ignoredCauses == null ? Set.of() : Set.copyOf(ignoredCauses);
    }

    public static DamageIndicatorConfig defaults() {
        return new DamageIndicatorConfig(
                true,
                20,
                2.2D,
                0.5D,
                500L,
                4,
                true,
                Set.of("FIRE_TICK", "POISON", "WITHER", "DROWNING", "FREEZE", "SUFFOCATION", "STARVATION"),
                true,
                true,
                true,
                true
        );
    }

    public static DamageIndicatorConfig fromConfig(YamlSection section) {
        if (section == null) {
            return defaults();
        }
        DamageIndicatorConfig defaults = defaults();
        YamlSection triggers = section.getSection("triggers");
        Set<String> ignored = normalizeCauses(section.getStringList("ignored_causes"), defaults.ignoredCauses());
        return new DamageIndicatorConfig(
                section.getBoolean("enabled", defaults.enabled()),
                section.getInt("lifetime_ticks", defaults.lifetimeTicks()),
                doubleOf(section, "offset_y", defaults.offsetY()),
                doubleOf(section, "spread", defaults.spread()),
                section.getInt("merge_window_ms", (int) defaults.mergeWindowMs()),
                section.getInt("max_per_target_per_second", defaults.maxPerTargetPerSecond()),
                "involved".equalsIgnoreCase(section.getString("visible_to", "involved")),
                ignored,
                triggers == null ? defaults.normalEnabled() : triggers.getBoolean("normal", defaults.normalEnabled()),
                triggers == null ? defaults.criticalEnabled() : triggers.getBoolean("critical", defaults.criticalEnabled()),
                triggers == null ? defaults.healEnabled() : triggers.getBoolean("heal", defaults.healEnabled()),
                triggers == null ? defaults.dodgeEnabled() : triggers.getBoolean("dodge", defaults.dodgeEnabled())
        );
    }

    /** {@return 该伤害原因是否应跳过飘字} */
    public boolean isIgnored(EntityDamageEvent.DamageCause cause) {
        return cause != null && ignoredCauses.contains(cause.name());
    }

    private static Set<String> normalizeCauses(java.util.List<String> raw, Set<String> fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String entry : raw) {
            if (entry != null && !entry.isBlank()) {
                result.add(entry.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static double doubleOf(YamlSection section, String key, double fallback) {
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }
}
