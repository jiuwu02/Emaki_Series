package emaki.jiuwu.craft.attribute.config;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record AttributeConfig(String language,
        boolean hardLockDamage,
        String defaultDamageType,
        int regenIntervalTicks,
        int syncDelayTicks,
        DefaultProfile defaultProfile,
        boolean syntheticHitKnockback,
        double syntheticHitKnockbackStrength,
        boolean syntheticHitHurtSound,
        List<DamageCauseRule> allowedDamageCauses) {

    public static AttributeConfig defaults() {
        return new AttributeConfig("zh_CN", true, "physical", 20, 1, defaultProfileDefaults(), true, 0.4D, true, List.of());
    }

    public static AttributeConfig fromConfig(YamlSection configuration) {
        if (configuration == null) {
            return defaults();
        }
        AttributeConfig defaults = defaults();
        String language = ConfigNodes.string(configuration, "language", "zh_CN");
        boolean hardLockDamage = Boolean.TRUE.equals(configuration.getBoolean("hard_lock_damage", true));
        String defaultDamageType = ConfigNodes.string(configuration, "default_damage_type", "physical");
        int regenIntervalTicks = Math.max(1, configuration.getInt("regen_interval_ticks", 20));
        int syncDelayTicks = Math.max(0, configuration.getInt("sync_delay_ticks", 1));
        DefaultProfile defaultProfile = DefaultProfile.fromMap(configuration.getSection("default_profile"));
        if (defaultProfile == null) {
            defaultProfile = defaults.defaultProfile();
        }
        boolean syntheticHitKnockback = Boolean.TRUE.equals(configuration.getBoolean("synthetic_hit_feedback.knockback", true));
        double syntheticHitKnockbackStrength = Math.max(0D, configuration.getDouble("synthetic_hit_feedback.knockback_strength", 0.4D));
        boolean syntheticHitHurtSound = Boolean.TRUE.equals(configuration.getBoolean("synthetic_hit_feedback.hurt_sound", true));
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
                defaultProfile,
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

    private static DefaultProfile defaultProfileDefaults() {
        return DefaultProfile.fromMap(java.util.Map.of(
                "id", "default",
                "priority", 1_000_000,
                "description", "所有玩家共享的默认基础属性与资源上限。",
                "resources", java.util.Map.of(
                        "health", java.util.Map.of(
                                "display_name", "生命值",
                                "default_max", 20.0,
                                "min_max", 1.0,
                                "max_max", 2048.0,
                                "sync_to_bukkit", true,
                                "full_on_init", true
                        ),
                        "mana", java.util.Map.of(
                                "display_name", "法力值",
                                "default_max", 100.0,
                                "min_max", 0.0,
                                "max_max", 99999.0,
                                "sync_to_bukkit", false,
                                "full_on_init", true
                        )
                ),
                "attributes", java.util.Map.ofEntries(
                        java.util.Map.entry("physical_attack", 0.0),
                        java.util.Map.entry("physical_defense", 0.0),
                        java.util.Map.entry("physical_damage_bonus", 0.0),
                        java.util.Map.entry("physical_crit_rate", 0.0),
                        java.util.Map.entry("physical_crit_damage", 0.0),
                        java.util.Map.entry("projectile_attack", 0.0),
                        java.util.Map.entry("projectile_damage_bonus", 0.0),
                        java.util.Map.entry("projectile_crit_rate", 0.0),
                        java.util.Map.entry("projectile_crit_damage", 0.0),
                        java.util.Map.entry("projectile_defense", 0.0),
                        java.util.Map.entry("spell_attack", 0.0),
                        java.util.Map.entry("spell_damage_bonus", 0.0),
                        java.util.Map.entry("spell_crit_rate", 0.0),
                        java.util.Map.entry("spell_crit_damage", 0.0),
                        java.util.Map.entry("spell_defense", 0.0),
                        java.util.Map.entry("health_regen", 0.0),
                        java.util.Map.entry("mana_regen", 0.0)
                )
        ));
    }
}
