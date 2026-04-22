package emaki.jiuwu.craft.attribute.service;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

final class SyntheticDamageDispatcher {

    private static final double ZERO_EPSILON = 1.0E-9D;

    private final AttributeService service;

    SyntheticDamageDispatcher(AttributeService service) {
        this.service = service;
    }

    ResolvedDamage dispatchIfNeeded(ResolvedDamage resolvedDamage, Entity visualSource) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null) {
            return null;
        }
        DamageContext damageContext = resolvedDamage.damageContext();
        if (!shouldTriggerMythicOnDamaged(damageContext)) {
            return resolvedDamage;
        }
        LivingEntity target = damageContext.target();
        if (target == null || !target.isValid() || target.isDead()) {
            return null;
        }
        EntityDamageEvent.DamageCause cause = damageContext.cause() == null ? EntityDamageEvent.DamageCause.CUSTOM : damageContext.cause();
        DamageSource damageSource = buildSyntheticDamageSource(damageContext, visualSource, cause);
        EntityDamageEvent event = createSyntheticDamageEvent(target, cause, damageSource, resolvedDamage.finalDamage());
        service.markSyntheticDamage(target, true);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            service.markSyntheticDamage(target, false);
        }
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            if (shouldDebugCombat(damageContext)) {
                debugCombat(damageContext, "MYTHIC_ON_DAMAGED_BLOCKED", "combat_debug.mythic_on_damaged_blocked", Map.of(
                        "cancelled", event.isCancelled(),
                        "final_damage", formatNumber(event.getFinalDamage())
                ));
            }
            return null;
        }
        if (Math.abs(event.getFinalDamage() - resolvedDamage.finalDamage()) > ZERO_EPSILON
                && shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "MYTHIC_ON_DAMAGED_ADJUSTED", "combat_debug.mythic_on_damaged_adjusted", Map.of(
                    "before", formatNumber(resolvedDamage.finalDamage()),
                    "after", formatNumber(event.getFinalDamage())
            ));
        }
        return new ResolvedDamage(
                damageContext,
                resolvedDamage.damageResult(),
                resolvedDamage.damageType(),
                Math.max(0D, event.getFinalDamage())
                );
    }

    private EntityDamageEvent createSyntheticDamageEvent(
            LivingEntity target,
            EntityDamageEvent.DamageCause cause,
            DamageSource damageSource,
            double finalDamage
    ) {
        return new EntityDamageEvent(target, cause, damageSource, finalDamage);
    }

    private boolean shouldTriggerMythicOnDamaged(DamageContext damageContext) {
        return damageContext != null
                && contextBoolean(
                        damageContext.variables(),
                        false,
                        "trigger_mythic_on_damaged",
                        "trigger_on_damaged",
                        "mythic_on_damaged"
                );
    }

    private DamageSource buildSyntheticDamageSource(DamageContext damageContext, Entity visualSource, EntityDamageEvent.DamageCause cause) {
        DamageSource.Builder builder = DamageSource.builder(resolveSyntheticDamageType(damageContext, visualSource, cause));
        Entity directEntity = resolveDirectDamageEntity(damageContext, visualSource);
        Entity causingEntity = resolveCausingDamageEntity(damageContext, visualSource);
        if (directEntity != null && directEntity.isValid()) {
            builder.withDirectEntity(directEntity);
            builder.withDamageLocation(directEntity.getLocation());
        } else if (damageContext != null && damageContext.target() != null) {
            builder.withDamageLocation(damageContext.target().getLocation());
        }
        if (causingEntity != null && causingEntity.isValid()) {
            builder.withCausingEntity(causingEntity);
            if (directEntity == null) {
                builder.withDamageLocation(causingEntity.getLocation());
            }
        }
        return builder.build();
    }

    private DamageType resolveSyntheticDamageType(DamageContext damageContext, Entity visualSource, EntityDamageEvent.DamageCause cause) {
        Entity source = visualSource != null ? visualSource : resolveDirectDamageEntity(damageContext, null);
        return switch (cause == null ? EntityDamageEvent.DamageCause.CUSTOM : cause) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK ->
                damageContext != null && damageContext.attacker() instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK;
            case PROJECTILE -> resolveProjectileDamageType(source);
            case MAGIC, POISON -> source instanceof Projectile ? DamageType.INDIRECT_MAGIC : DamageType.MAGIC;
            case THORNS -> DamageType.THORNS;
            case BLOCK_EXPLOSION -> DamageType.EXPLOSION;
            case ENTITY_EXPLOSION ->
                source instanceof Player ? DamageType.PLAYER_EXPLOSION : DamageType.EXPLOSION;
            case SONIC_BOOM -> DamageType.SONIC_BOOM;
            case WITHER -> DamageType.WITHER;
            case FALL -> DamageType.FALL;
            case DROWNING -> DamageType.DROWN;
            case LAVA -> DamageType.LAVA;
            case FIRE_TICK -> DamageType.ON_FIRE;
            case FIRE -> DamageType.IN_FIRE;
            case CONTACT -> DamageType.CACTUS;
            case VOID -> DamageType.OUT_OF_WORLD;
            default -> DamageType.GENERIC;
        };
    }

    private DamageType resolveProjectileDamageType(Entity source) {
        return switch (source) {
            case Trident ignored -> DamageType.TRIDENT;
            case WitherSkull ignored -> DamageType.WITHER_SKULL;
            case Fireball ignored -> DamageType.FIREBALL;
            case AbstractArrow ignored -> DamageType.ARROW;
            default -> DamageType.MOB_PROJECTILE;
        };
    }

    private Entity resolveDirectDamageEntity(DamageContext damageContext, Entity visualSource) {
        if (visualSource != null) {
            return visualSource;
        }
        if (damageContext == null) {
            return null;
        }
        if (damageContext.projectile() != null) {
            return damageContext.projectile();
        }
        return damageContext.attacker();
    }

    private Entity resolveCausingDamageEntity(DamageContext damageContext, Entity visualSource) {
        if (damageContext != null && damageContext.attacker() != null) {
            return damageContext.attacker();
        }
        if (visualSource instanceof Projectile projectile && projectile.getShooter() instanceof Entity entity) {
            return entity;
        }
        return visualSource;
    }

    private boolean contextBoolean(DamageContextVariables context, boolean fallback, String... keys) {
        if (context == null || context.isEmpty() || keys == null || keys.length == 0) {
            return fallback;
        }
        for (String key : keys) {
            if (Texts.isBlank(key) || !context.contains(key)) {
                continue;
            }
            Object raw = context.get(key);
            if (raw instanceof Boolean boolValue) {
                return boolValue;
            }
            String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    private boolean shouldDebugCombat(DamageContext damageContext) {
        if (damageContext == null) {
            return false;
        }
        return damageContext.projectile() != null
                ? service.combatDebug().shouldTrace(damageContext.projectile(), damageContext.target())
                : service.combatDebug().shouldTrace(damageContext.attacker(), damageContext.target());
    }

    private void debugCombat(DamageContext damageContext, String phase, String messageKey, Map<String, ?> replacements) {
        if (!shouldDebugCombat(damageContext) || Texts.isBlank(messageKey)) {
            return;
        }
        service.combatDebug().logMessage(phase, messageKey, replacements);
    }

    private String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.##");
    }
}
