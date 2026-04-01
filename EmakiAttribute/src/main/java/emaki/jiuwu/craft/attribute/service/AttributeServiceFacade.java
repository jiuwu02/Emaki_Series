package emaki.jiuwu.craft.attribute.service;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;

public interface AttributeServiceFacade {

    AttributeConfig config();

    AttributeRegistry attributeRegistry();

    AttributeBalanceRegistry attributeBalanceRegistry();

    DamageTypeRegistry damageTypeRegistry();

    DefaultProfileRegistry defaultProfileRegistry();

    LoreFormatRegistry loreFormatRegistry();

    AttributePresetRegistry presetRegistry();

    LoreParser loreParser();

    DamageEngine damageEngine();

    EmakiAttributePlugin plugin();

    void reloadConfig(AttributeConfig config);

    void refreshCaches();

    String defaultDamageTypeId();

    String defaultProjectileDamageTypeId();

    DamageTypeDefinition resolveDamageType(String damageTypeId);

    String defaultProfileSignature();

    Map<String, ResourceDefinition> resourceDefinitions();

    Map<String, Double> defaultAttributeValues();

    Double resolveAttributeValue(AttributeSnapshot snapshot, String attributeId);

    void registerContributionProvider(AttributeContributionProvider provider);

    void unregisterContributionProvider(String providerId);

    AttributeSnapshot collectItemSnapshot(ItemStack itemStack);

    AttributeSnapshot collectCombatSnapshot(LivingEntity entity);

    AttributeSnapshot collectPlayerCombatSnapshot(Player player);

    void resyncAllPlayers();

    void regenerateOnlinePlayers();

    void resyncPlayer(Player player);

    void scheduleJoinHealthSync(Player player);

    void scheduleRespawnHealthSync(Player player);

    void scheduleHealthSync(LivingEntity entity);

    void scheduleEquipmentSync(Player player);

    void scheduleLivingEntitySync(LivingEntity entity);

    void syncLivingEntity(LivingEntity entity);

    void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride);

    ResourceState syncResource(Player player,
            ResourceDefinition resourceDefinition,
            AttributeSnapshot snapshot,
            ResourceSyncReason reason,
            Double currentValueOverride);

    ResourceState readResourceState(Player player, String resourceId);

    void setDamageTypeOverride(LivingEntity entity, String damageTypeId);

    String peekDamageTypeOverride(LivingEntity entity);

    String consumeDamageTypeOverride(LivingEntity entity);

    void markSyntheticDamage(LivingEntity entity, boolean value);

    boolean isSyntheticDamage(LivingEntity entity);

    ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter);

    ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile);

    DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables context);

    DamageResult calculateDamage(DamageContext damageContext);

    boolean applyDamage(DamageContext damageContext);

    boolean applyProjectileDamage(DamageContext damageContext);

    void clearPlayerDamageTypeOverride(Player player);

    boolean isAttackCoolingDown(Player player);

    int startAttackCooldown(Player player, AttributeSnapshot snapshot, ItemStack itemStack);

    default DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            Map<String, ?> context) {
        return createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, null, null, DamageContextVariables.from(context));
    }

    default DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            DamageContextVariables context) {
        return createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, null, null, context);
    }

    default DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) {
        double sourceDamage = context == null ? baseDamage : context.doubleValue("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = enumValue(
                context,
                "cause",
                EntityDamageEvent.DamageCause.class,
                enumValue(context, "damage_cause", EntityDamageEvent.DamageCause.class, null)
        );
        return calculateDamage(createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, context));
    }

    default DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> context) {
        return calculateDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    default boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) {
        double sourceDamage = context == null ? baseDamage : context.doubleValue("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = enumValue(
                context,
                "cause",
                EntityDamageEvent.DamageCause.class,
                enumValue(context, "damage_cause", EntityDamageEvent.DamageCause.class, null)
        );
        return applyDamage(createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, context));
    }

    default boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> context) {
        return applyDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    default boolean applyProjectileDamage(Projectile projectile,
            LivingEntity target,
            double baseDamage,
            DamageContextVariables context) {
        if (projectile == null || target == null) {
            return false;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        double sourceDamage = context == null ? baseDamage : context.doubleValue("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = enumValue(
                context,
                "cause",
                EntityDamageEvent.DamageCause.class,
                enumValue(context, "damage_cause", EntityDamageEvent.DamageCause.class, null)
        );
        return applyProjectileDamage(createDamageContext(
                shooter,
                target,
                projectile,
                cause,
                defaultProjectileDamageTypeId(),
                sourceDamage,
                baseDamage,
                context
        ));
    }

    default boolean applyProjectileDamage(Projectile projectile,
            LivingEntity target,
            double baseDamage,
            Map<String, ?> context) {
        return applyProjectileDamage(projectile, target, baseDamage, DamageContextVariables.from(context));
    }

    private static <E extends Enum<E>> E enumValue(DamageContextVariables context,
            String key,
            Class<E> enumType,
            E fallback) {
        if (context == null || key == null || key.isBlank() || enumType == null) {
            return fallback;
        }
        Object raw = context.get(key);
        if (raw == null) {
            return fallback;
        }
        if (enumType.isInstance(raw)) {
            return enumType.cast(raw);
        }
        try {
            return Enum.valueOf(enumType, String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
