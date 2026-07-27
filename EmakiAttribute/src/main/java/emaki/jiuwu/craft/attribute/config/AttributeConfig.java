package emaki.jiuwu.craft.attribute.config;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record AttributeConfig(String language,
        boolean releaseDefaultData,
        boolean readLoreAttributes,
        boolean readPdcAttributes,
        boolean requireLorePdcMatch,
        boolean hardLockDamage,
        String defaultDamageType,
        String projectileDamageType,
        boolean vanillaEventDamageEnabled,
        String vanillaEventDamageType,
        boolean sameSignatureIgnoresInvulnerabilityEnabled,
        long sameSignatureInvulnerabilityWindowMs,
        boolean attackSpeedAttributeOnly,
        int regenIntervalTicks,
        int syncDelayTicks,
        boolean healthDisplayScalingEnabled,
        double healthDisplayScalingTarget,
        DefaultProfile defaultProfile,
        boolean syntheticHitKnockback,
        double syntheticHitKnockbackStrength,
        boolean syntheticHitHurtSound,
        DamageIndicatorConfig damageIndicator,
        List<DamageCauseRule> allowedDamageCauses) {

    /** Attack speed cooldown is managed by EmakiAttribute for every attack. */
    private static final String ATTACK_SPEED_SCOPE_GLOBAL = "global";

    /** Attack speed cooldown is managed only for items carrying an EmakiAttribute attack speed value. */
    private static final String ATTACK_SPEED_SCOPE_ATTRIBUTE_ONLY = "attribute_only";

    public static AttributeConfig defaults() {
        return new AttributeConfig(
                "zh_CN",
                true,
                true,
                true,
                false,
                true,
                "physical",
                "projectile",
                true,
                "physical",
                true,
                500L,
                false,
                20,
                1,
                false,
                20D,
                defaultProfileDefaults(),
                true,
                0.4D,
                true,
                DamageIndicatorConfig.defaults(),
                List.of()
        );
    }

    public static AttributeConfig fromConfig(YamlSection configuration) {
        if (configuration == null) {
            return defaults();
        }
        AttributeConfig defaults = defaults();
        String language = ConfigNodes.string(configuration, "language", "zh_CN");
        boolean releaseDefaultData = Boolean.TRUE.equals(configuration.getBoolean("release_default_data", true));
        boolean readLoreAttributes = Boolean.TRUE.equals(configuration.getBoolean("attribute_sources.read_lore_attributes", true));
        boolean readPdcAttributes = Boolean.TRUE.equals(configuration.getBoolean("attribute_sources.read_pdc_attributes", true));
        boolean requireLorePdcMatch = Boolean.TRUE.equals(configuration.getBoolean("attribute_sources.require_lore_pdc_match", false));
        boolean hardLockDamage = Boolean.TRUE.equals(configuration.getBoolean("hard_lock_damage", true));
        String defaultDamageType = ConfigNodes.string(configuration, "default_damage_type", "physical");
        String projectileDamageType = ConfigNodes.string(configuration, "projectile_damage_type", "projectile");
        boolean vanillaEventDamageEnabled = Boolean.TRUE.equals(configuration.getBoolean("vanilla_event_damage.enabled", true));
        String vanillaEventDamageType = ConfigNodes.string(configuration, "vanilla_event_damage.damage_type", defaultDamageType);
        boolean sameSignatureIgnoresInvulnerabilityEnabled = Boolean.TRUE.equals(
                configuration.getBoolean("same_signature_ignores_invulnerability.enabled", true));
        long sameSignatureInvulnerabilityWindowMs = Math.max(0L, Numbers.tryParseLong(
                configuration.get("same_signature_ignores_invulnerability.window_ms"), 500L));
        boolean attackSpeedAttributeOnly = ATTACK_SPEED_SCOPE_ATTRIBUTE_ONLY.equals(
                ConfigNodes.string(configuration, "attack_speed.scope", ATTACK_SPEED_SCOPE_GLOBAL)
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT));
        int regenIntervalTicks = Math.max(1, configuration.getInt("regen_interval_ticks", 20));
        int syncDelayTicks = Math.max(0, configuration.getInt("sync_delay_ticks", 1));
        boolean healthDisplayScalingEnabled = Boolean.TRUE.equals(configuration.getBoolean("health_display_scaling.enabled", false));
        double healthDisplayScalingTarget = configuration.getDouble("health_display_scaling.target", 20D);
        if (!Double.isFinite(healthDisplayScalingTarget) || healthDisplayScalingTarget <= 0D) {
            healthDisplayScalingTarget = 20D;
        }
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
                releaseDefaultData,
                readLoreAttributes,
                readPdcAttributes,
                requireLorePdcMatch,
                hardLockDamage,
                defaultDamageType,
                projectileDamageType,
                vanillaEventDamageEnabled,
                vanillaEventDamageType,
                sameSignatureIgnoresInvulnerabilityEnabled,
                sameSignatureInvulnerabilityWindowMs,
                attackSpeedAttributeOnly,
                regenIntervalTicks,
                syncDelayTicks,
                healthDisplayScalingEnabled,
                healthDisplayScalingTarget,
                defaultProfile,
                syntheticHitKnockback,
                syntheticHitKnockbackStrength,
                syntheticHitHurtSound,
                DamageIndicatorConfig.fromConfig(configuration.getSection("damage_indicator")),
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
