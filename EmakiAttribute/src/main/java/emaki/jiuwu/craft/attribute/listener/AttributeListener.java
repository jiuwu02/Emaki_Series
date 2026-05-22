package emaki.jiuwu.craft.attribute.listener;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.CombatSupport;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class AttributeListener implements Listener {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;

    public AttributeListener(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        attributeService.scheduleJoinHealthSync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        attributeService.cleanupEntityState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        attributeService.cleanupEntityState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        attributeService.scheduleRespawnHealthSync(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("MythicMobs".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensureMythicBridge();
            if (plugin.mythicBridge() != null) {
                plugin.mythicBridge().resyncActiveMobs();
            }
        }
        if ("PlaceholderAPI".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensurePlaceholderExpansion();
        }
        if ("MMOItems".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensureMmoItemsBridge();
        }
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            attributeService.scheduleEquipmentSync(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            attributeService.scheduleEquipmentSync(player);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : null;
        if (shooter instanceof LivingEntity livingEntity) {
            Player playerShooter = livingEntity instanceof Player player ? player : null;
            if (isAttackCoolingDown(playerShooter, livingEntity, null, projectile,
                    "PROJECTILE_LAUNCH_BLOCKED", "combat_debug.projectile_launch_blocked", "shooter")) {
                event.setCancelled(true);
                return;
            }
            var snapshot = attributeService.snapshotProjectile(projectile, livingEntity);
            if (shouldDebugCombat(livingEntity, null, projectile)) {
                debugCombat(livingEntity, null, projectile, "PROJECTILE_LAUNCH", "combat_debug.projectile_launch_snapshot_written", Map.of(
                        "projectile", describeEntity(projectile),
                        "damage_type", snapshot == null ? "<none>" : snapshot.damageTypeId(),
                        "signature", snapshot == null ? "<none>" : snapshot.sourceSignature()
                ));
            }
            if (playerShooter != null) {
                attributeService.startAttackCooldown(
                        playerShooter,
                        snapshot == null ? null : snapshot.attackSnapshot(),
                        playerShooter.getInventory().getItemInMainHand()
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getPlayer() != null && attributeService.isAttackCoolingDown(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getPlayer() != null && attributeService.isAttackCoolingDown(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LivingEntity target = event.getEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (target == null) {
            return;
        }
        Entity damager = event.getDamager();
        if (attributeService.isSyntheticDamage(target)) {
            LivingEntity syntheticAttacker = damager instanceof LivingEntity livingEntity ? livingEntity : null;
            Projectile syntheticProjectile = damager instanceof Projectile projectile ? projectile : null;
            debugCombat(syntheticAttacker, target, syntheticProjectile, "SYNTHETIC_DAMAGE_BYPASS", "combat_debug.synthetic_damage_bypass_entity");
            return;
        }
        // 只有原生近战 / 横扫 / 投射物继续走战斗属性结算，其余 by-entity 原版伤害按环境伤害分流。
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            if (damager instanceof Projectile projectile) {
                Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : null;
                LivingEntity shootingEntity = shooter instanceof LivingEntity livingEntity ? livingEntity : null;
                if (shouldDebugCombat(shootingEntity, target, projectile)) {
                    debugCombat(shootingEntity, target, projectile, "PROJECTILE_HIT", "combat_debug.projectile_hit_intercept", Map.of(
                            "shooter", describeEntity(shootingEntity),
                            "projectile", describeEntity(projectile),
                            "target", describeEntity(target),
                            "cause", cause.name(),
                            "vanilla_damage", formatNumber(event.getDamage()),
                            "vanilla_final", formatNumber(event.getFinalDamage())
                    ));
                }
                Player playerShooter = shooter instanceof Player player ? player : null;
                if (isAttackCoolingDown(playerShooter, shootingEntity, target, projectile,
                        "PROJECTILE_HIT_BLOCKED", "combat_debug.projectile_hit_blocked", "shooter")) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                DamageContextVariables context = CombatSupport.baseContext(event, target);
                DamageContext damageContext = createProjectileDamageContext(event, projectile, target, context);
                if (damageContext == null) {
                    debugCombat(shootingEntity, target, projectile, "PROJECTILE_RESOLVE_EMPTY", "combat_debug.projectile_resolve_empty");
                    return;
                }
                resolveAndApplyDamage(
                        attributeService.resolveDamageApplicationAsync(damageContext),
                        shootingEntity,
                        target,
                        projectile,
                        projectile,
                        "PROJECTILE_RESOLVE_EMPTY",
                        "combat_debug.projectile_resolve_empty",
                        "PROJECTILE_RESOLVED",
                        "combat_debug.projectile_resolved",
                        "PROJECTILE_APPLY",
                        "combat_debug.projectile_apply"
                );
                return;
            }
            handleEnvironmentalDamage(event, target, damager instanceof LivingEntity livingEntity ? livingEntity : null);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (damager instanceof LivingEntity attacker) {
                DamageContextVariables context = CombatSupport.baseContext(event, target);
                if (shouldDebugCombat(attacker, target, null)) {
                    debugCombat(attacker, target, null, "MELEE_HIT", "combat_debug.melee_hit_intercept", Map.of(
                            "attacker", describeEntity(attacker),
                            "target", describeEntity(target),
                            "cause", cause.name(),
                            "vanilla_damage", formatNumber(event.getDamage()),
                            "vanilla_final", formatNumber(event.getFinalDamage())
                    ));
                }
                Player attackingPlayer = attacker instanceof Player player ? player : null;
                if (isAttackCoolingDown(attackingPlayer, attacker, target, null,
                        "MELEE_HIT_BLOCKED", "combat_debug.melee_hit_blocked", "attacker")) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                DamageContext damageContext = createMeleeDamageContext(event, attacker, target, context);
                if (damageContext == null) {
                    debugCombat(attacker, target, null, "MELEE_RESOLVE_EMPTY", "combat_debug.melee_resolve_empty");
                    return;
                }
                resolveAndApplyDamage(
                        attributeService.resolveDamageApplicationAsync(damageContext),
                        attacker,
                        target,
                        null,
                        damager,
                        "MELEE_RESOLVE_EMPTY",
                        "combat_debug.melee_resolve_empty",
                        "MELEE_RESOLVED",
                        "combat_debug.melee_resolved",
                        "MELEE_APPLY",
                        "combat_debug.melee_apply"
                );
                return;
            }
            handleEnvironmentalDamage(event, target, null);
            return;
        }
        handleEnvironmentalDamage(event, target, damager instanceof LivingEntity livingEntity ? livingEntity : null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        LivingEntity target = event.getEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (target == null) {
            return;
        }
        if (attributeService.isSyntheticDamage(target)) {
            debugCombat(null, target, null, "SYNTHETIC_DAMAGE_BYPASS", "combat_debug.synthetic_damage_bypass_non_entity");
            return;
        }
        handleEnvironmentalDamage(event, target, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        attributeService.scheduleHealthSync(livingEntity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            attributeService.scheduleHealthSync(livingEntity);
        }
    }

    private boolean handleEnvironmentalDamage(EntityDamageEvent event, LivingEntity target, LivingEntity attacker) {
        AttributeConfig config = attributeService.config();
        DamageCauseRule rule = config.damageCauseRule(event.getCause().name());
        if (rule == null && !config.vanillaEventDamageEnabled()) {
            if (shouldDebugCombat(attacker, target, null)) {
                debugCombat(attacker, target, null, "ENVIRONMENT_IGNORED", "combat_debug.environment_ignored", Map.of(
                        "cause", event.getCause().name()
                ));
            }
            return false;
        }
        event.setCancelled(true);
        DamageContextVariables.Builder context = CombatSupport.baseContext(event, target).toBuilder();
        if (rule != null && rule.context() != null && !rule.context().isEmpty()) {
            context.putAll(rule.context());
        }
        double sourceDamage = event.getDamage();
        double baseDamage = rule == null ? sourceDamage : rule.resolveDamage(sourceDamage);
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            applyFallDamageContext(target, context, sourceDamage);
        }
        context.put("cause", event.getCause().name());
        context.put("damage_cause", event.getCause().name());
        context.put("damage_cause_id", event.getCause().name());
        context.put("base_damage", baseDamage);
        context.put("source_damage", sourceDamage);
        context.put("input_damage", sourceDamage);
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        String damageTypeId = rule == null
                ? config.vanillaEventDamageType()
                : (rule.hasDamageType() ? rule.damageTypeId() : config.defaultDamageType());
        DamageContextVariables resolvedContext = context.build();
        if (shouldDebugCombat(attacker, target, null)) {
            debugCombat(attacker, target, null, "ENVIRONMENT_RESOLVED", "combat_debug.environment_mapped", Map.of(
                    "attacker", describeEntity(attacker),
                    "target", describeEntity(target),
                    "cause", event.getCause().name(),
                    "damage_type", damageTypeId,
                    "source_damage", formatNumber(sourceDamage),
                    "base_damage", formatNumber(baseDamage)
            ));
        }
        DamageContext damageContext = attributeService.createDamageContext(
                attacker,
                target,
                null,
                event.getCause(),
                damageTypeId,
                sourceDamage,
                baseDamage,
                resolvedContext
        );
        resolveAndApplyDamage(
                attributeService.resolveDamageApplicationAsync(damageContext),
                attacker,
                target,
                null,
                attacker,
                "ENVIRONMENT_RESOLVE_EMPTY",
                "combat_debug.environment_resolve_empty",
                "ENVIRONMENT_ASYNC_RESOLVED",
                "combat_debug.environment_resolved_async",
                "ENVIRONMENT_APPLY",
                "combat_debug.environment_apply"
        );
        return true;
    }

    private void applyFallDamageContext(LivingEntity target, DamageContextVariables.Builder context, double vanillaDamage) {
        if (target == null || context == null) {
            return;
        }
        // 原版服务端已经计算过摔落伤害；这里仅补充高度相关上下文，便于调试和表达式引用。
        double fallDistance = Math.max(0D, target.getFallDistance());
        PotionEffect jumpBoost = target.getPotionEffect(PotionEffectType.JUMP_BOOST);
        int jumpBoostLevel = jumpBoost == null ? 0 : Math.max(0, jumpBoost.getAmplifier() + 1);
        double fallDamageFormula = Math.max(0D, Math.ceil(fallDistance - 3D - jumpBoostLevel));
        context.put("fall_distance", fallDistance);
        context.put("jump_boost_level", jumpBoostLevel);
        context.put("vanilla_fall_damage", vanillaDamage);
        context.put("fall_damage_formula", fallDamageFormula);
    }

    private void scheduleDamageApplication(Runnable action) {
        if (action == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private boolean isAttackCoolingDown(Player player,
            LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            String phase,
            String messageKey,
            String entityKey) {
        if (player == null || !attributeService.isAttackCoolingDown(player)) {
            return false;
        }
        if (shouldDebugCombat(attacker, target, projectile)) {
            debugCombat(attacker, target, projectile, phase, messageKey, Map.of(
                    entityKey, describeEntity(attacker)
            ));
        }
        return true;
    }

    private DamageContext createMeleeDamageContext(EntityDamageByEntityEvent event,
            LivingEntity attacker,
            LivingEntity target,
            DamageContextVariables context) {
        if (target == null) {
            return null;
        }
        return attributeService.createDamageContext(
                attacker,
                target,
                null,
                event.getCause(),
                null,
                event.getDamage(),
                0D,
                context
        );
    }

    private DamageContext createProjectileDamageContext(EntityDamageByEntityEvent event,
            Projectile projectile,
            LivingEntity target,
            DamageContextVariables context) {
        if (projectile == null || target == null) {
            return null;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        var snapshot = attributeService.readProjectileSnapshot(projectile);
        if (snapshot == null) {
            if (shouldDebugCombat(shooter, target, projectile)) {
                debugCombat(shooter, target, projectile, "PROJECTILE_SNAPSHOT_MISSING", "combat_debug.projectile_snapshot_missing_strict", Map.of(
                        "projectile", describeEntity(projectile)
                ));
            }
            return null;
        }
        var attackerSnapshot = snapshot.attackSnapshot();
        var targetSnapshot = attributeService.collectCombatSnapshot(target);
        String damageTypeId = snapshot.damageTypeId();
        return attributeService.createDamageContext(
                shooter,
                target,
                projectile,
                event.getCause(),
                damageTypeId,
                event.getDamage(),
                0D,
                attackerSnapshot,
                targetSnapshot,
                context
        );
    }

    private void resolveAndApplyDamage(CompletableFuture<ResolvedDamage> future,
            LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            Entity visualSource,
            String emptyPhase,
            String emptyMessageKey,
            String resolvedPhase,
            String resolvedMessageKey,
            String applyPhase,
            String applyMessageKey) {
        if (future == null) {
            debugCombat(attacker, target, projectile, emptyPhase, emptyMessageKey);
            return;
        }
        future.whenComplete((resolvedDamage, throwable) -> scheduleDamageApplication(() -> {
            if (throwable != null) {
                if (shouldDebugCombat(attacker, target, projectile)) {
                    debugCombat(attacker, target, projectile, "ASYNC_DAMAGE_FAILED", "combat_debug.async_damage_failed", Map.of(
                            "error", CombatSupport.rootCauseMessage(throwable)
                    ));
                }
                return;
            }
            if (resolvedDamage == null) {
                debugCombat(attacker, target, projectile, emptyPhase, emptyMessageKey);
                return;
            }
            if (shouldDebugCombat(attacker, target, projectile)) {
                debugCombat(attacker, target, projectile, resolvedPhase, resolvedMessageKey, Map.of(
                        "resolved", describeResolvedDamage(resolvedDamage)
                ));
            }
            CombatSupport.applySyntheticKnockback(target, visualSource, resolvedDamage.finalDamage(), attributeService.config());
            debugCombat(attacker, target, projectile, applyPhase, applyMessageKey);
            attributeService.applyResolvedDamage(resolvedDamage, visualSource, 0D);
        }));
    }

    private boolean shouldDebugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile) {
        return projectile != null
                ? attributeService.shouldTraceCombat(projectile, target)
                : attributeService.shouldTraceCombat(attacker, target);
    }

    private void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey) {
        debugCombat(attacker, target, projectile, phase, messageKey, Map.of());
    }

    private void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey, Map<String, ?> replacements) {
        if (phase == null || Texts.isBlank(messageKey) || !shouldDebugCombat(attacker, target, projectile)) {
            return;
        }
        attributeService.logCombatDebug(phase, messageKey, replacements);
    }

    private String describeResolvedDamage(ResolvedDamage resolvedDamage) {
        if (resolvedDamage == null) {
            return "<null>";
        }
        return "damageType=" + (resolvedDamage.damageType() == null ? resolvedDamage.damageResult().damageTypeId() : resolvedDamage.damageType().id())
                + ", finalDamage=" + formatNumber(resolvedDamage.finalDamage())
                + ", critical=" + resolvedDamage.damageResult().critical()
                + ", stages=" + resolvedDamage.damageResult().stageValues();
    }

    private String describeEntity(Entity entity) {
        if (entity == null) {
            return "<none>";
        }
        String name = entity.getName();
        if (name == null || name.isBlank()) {
            name = entity.getType().name().toLowerCase(Locale.ROOT);
        }
        return name + "(" + entity.getType().name() + "," + entity.getUniqueId() + ")";
    }

    private String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.##");
    }
}
