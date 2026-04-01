package emaki.jiuwu.craft.attribute.config;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

public record AttributeConfig(String language,
        boolean hardLockDamage,
        String defaultDamageType,
        int regenIntervalTicks,
        int syncDelayTicks,
        boolean syntheticHitKnockback,
        double syntheticHitKnockbackStrength,
        boolean syntheticHitHurtSound,
        List<DamageCauseRule> allowedDamageCauses) {

    public static AttributeConfig defaults() {
        return new AttributeConfig("zh_CN", true, "physical", 20, 1, true, 0.4D, true, List.of());
    }

    public static AttributeConfig fromConfig(YamlConfiguration configuration) {
        if (configuration == null) {
            return defaults();
        }
        String language = ConfigNodes.string(configuration, "language", "zh_CN");
        boolean hardLockDamage = configuration.getBoolean("hard_lock_damage", true);
        String defaultDamageType = ConfigNodes.string(configuration, "default_damage_type", "physical");
        int regenIntervalTicks = Math.max(1, configuration.getInt("regen_interval_ticks", 20));
        int syncDelayTicks = Math.max(0, configuration.getInt("sync_delay_ticks", 1));
        boolean syntheticHitKnockback = configuration.getBoolean("synthetic_hit_feedback.knockback", true);
        double syntheticHitKnockbackStrength = Math.max(0D, configuration.getDouble("synthetic_hit_feedback.knockback_strength", 0.4D));
        boolean syntheticHitHurtSound = configuration.getBoolean("synthetic_hit_feedback.hurt_sound", true);
        List<DamageCauseRule> causes = new ArrayList<>();
        Object rawCauses = configuration.get("allowed_damage_causes");
        for (Object entry : ConfigNodes.asObjectList(rawCauses)) {
            DamageCauseRule rule = DamageCauseRule.fromMap(entry, defaultDamageType);
            if (rule != null) {
                causes.add(rule);
            }
        }
        return new AttributeConfig(
                language,
                hardLockDamage,
                defaultDamageType,
                regenIntervalTicks,
                syncDelayTicks,
                syntheticHitKnockback,
                syntheticHitKnockbackStrength,
                syntheticHitHurtSound,
                List.copyOf(causes)
        );
    }

    public boolean allowsDamageCause(String cause) {
        return damageCauseRule(cause) != null;
    }

    public boolean hasDamageCauseRules() {
        return allowedDamageCauses != null && !allowedDamageCauses.isEmpty();
    }

    public DamageCauseRule damageCauseRule(String cause) {
        if (cause == null || cause.isBlank() || allowedDamageCauses == null || allowedDamageCauses.isEmpty()) {
            return null;
        }
        for (DamageCauseRule rule : allowedDamageCauses) {
            if (rule != null && rule.matches(cause)) {
                return rule;
            }
        }
        return null;
    }
}
