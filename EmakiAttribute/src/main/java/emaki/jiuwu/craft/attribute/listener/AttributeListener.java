package emaki.jiuwu.craft.attribute.listener;

import java.util.Locale;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.util.Vector;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.math.Numbers;

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
            if (livingEntity instanceof Player player && attributeService.isAttackCoolingDown(player)) {
                debugCombat(livingEntity, null, projectile, "PROJECTILE_LAUNCH_BLOCKED",
                        "投射物发射被攻击冷却拦截: shooter=" + describeEntity(livingEntity));
                event.setCancelled(true);
                return;
            }
            var snapshot = attributeService.snapshotProjectile(projectile, livingEntity);
            debugCombat(livingEntity, null, projectile, "PROJECTILE_LAUNCH",
                    "投射物已写入快照: projectile=" + describeEntity(projectile)
                    + ", damageType=" + (snapshot == null ? "<none>" : snapshot.damageTypeId())
                    + ", signature=" + (snapshot == null ? "<none>" : snapshot.sourceSignature()));
            if (livingEntity instanceof Player player) {
                attributeService.startAttackCooldown(
                        player,
                        snapshot == null ? null : snapshot.attackSnapshot(),
                        player.getInventory().getItemInMainHand()
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
        // Vanilla damage is ignored for combat math; Emaki applies the real health change itself.
        DamageContextVariables context = baseContext(event, target);
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : null;
            LivingEntity shootingEntity = shooter instanceof LivingEntity livingEntity ? livingEntity : null;
            debugCombat(shootingEntity, target, projectile, "PROJECTILE_HIT",
                    "拦截原版投射物伤害: shooter=" + describeEntity(shootingEntity)
                    + ", projectile=" + describeEntity(projectile)
                    + ", target=" + describeEntity(target)
                    + ", cause=" + event.getCause().name()
                    + ", vanillaDamage=" + formatNumber(event.getDamage())
                    + ", vanillaFinal=" + formatNumber(event.getFinalDamage()));
            if (shooter instanceof Player player && attributeService.isAttackCoolingDown(player)) {
                debugCombat(shootingEntity, target, projectile, "PROJECTILE_HIT_BLOCKED",
                        "投射物命中被攻击冷却拦截: shooter=" + describeEntity(shootingEntity));
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            DamageContext damageContext = createProjectileDamageContext(event, projectile, target, context);
            if (damageContext == null) {
                debugCombat(shootingEntity, target, projectile, "PROJECTILE_RESOLVE_EMPTY",
                        "投射物命中没有得到有效的 EA 伤害结果，命中流程到此结束。");
                return;
            }
            resolveAndApplyDamage(
                    attributeService.resolveDamageApplicationAsync(damageContext),
                    shootingEntity,
                    target,
                    projectile,
                    projectile,
                    "PROJECTILE_RESOLVE_EMPTY",
                    "投射物命中没有得到有效的 EA 伤害结果，命中流程到此结束。",
                    "PROJECTILE_RESOLVED",
                    "投射物命中已解析 EA 伤害: ",
                    "PROJECTILE_APPLY",
                    "开始执行下一 tick 的投射物 EA 伤害落地。"
            );
            return;
        }
        LivingEntity attacker = damager instanceof LivingEntity livingEntity ? livingEntity : null;
        debugCombat(attacker, target, null, "MELEE_HIT",
                "拦截原版近战伤害: attacker=" + describeEntity(attacker)
                + ", target=" + describeEntity(target)
                + ", cause=" + event.getCause().name()
                + ", vanillaDamage=" + formatNumber(event.getDamage())
                + ", vanillaFinal=" + formatNumber(event.getFinalDamage()));
        if (attacker instanceof Player player && attributeService.isAttackCoolingDown(player)) {
            debugCombat(attacker, target, null, "MELEE_HIT_BLOCKED",
                    "近战命中被攻击冷却拦截: attacker=" + describeEntity(attacker));
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        DamageContext damageContext = createMeleeDamageContext(event, attacker, target, context);
        if (damageContext == null) {
            debugCombat(attacker, target, null, "MELEE_RESOLVE_EMPTY",
                    "近战命中没有得到有效的 EA 伤害结果，命中流程到此结束。");
            return;
        }
        resolveAndApplyDamage(
                attributeService.resolveDamageApplicationAsync(damageContext),
                attacker,
                target,
                null,
                damager,
                "MELEE_RESOLVE_EMPTY",
                "近战命中没有得到有效的 EA 伤害结果，命中流程到此结束。",
                "MELEE_RESOLVED",
                "近战命中已解析 EA 伤害: ",
                "MELEE_APPLY",
                "开始执行下一 tick 的近战 EA 伤害落地。"
        );
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
        handleEnvironmentalDamage(event, target);
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

    private boolean handleEnvironmentalDamage(EntityDamageEvent event, LivingEntity target) {
        AttributeConfig config = attributeService.config();
        DamageCauseRule rule = config.damageCauseRule(event.getCause().name());
        if (rule == null) {
            if (!config.hasDamageCauseRules()) {
                if (!config.hardLockDamage()) {
                    return false;
                }
                event.setCancelled(true);
                debugCombat(null, target, null, "ENVIRONMENT_FALLBACK",
                        "环境伤害未命中白名单，但启用了硬锁，改为使用默认伤害类型接管: cause=" + event.getCause().name());
                DamageContext damageContext = attributeService.createDamageContext(
                        null,
                        target,
                        null,
                        event.getCause(),
                        config.defaultDamageType(),
                        event.getDamage(),
                        0D,
                        baseContext(event, target)
                );
                resolveAndApplyDamage(
                        attributeService.resolveDamageApplicationAsync(damageContext),
                        null,
                        target,
                        null,
                        null,
                        "ENVIRONMENT_RESOLVE_EMPTY",
                        "环境伤害没有得到有效的 EA 伤害结果，命中流程到此结束。",
                        "ENVIRONMENT_ASYNC_RESOLVED",
                        "环境伤害已解析 EA 伤害: ",
                        "ENVIRONMENT_APPLY",
                        "开始执行下一 tick 的环境 EA 伤害落地。"
                );
                return true;
            }
            debugCombat(null, target, null, "ENVIRONMENT_IGNORED",
                    "环境伤害未命中白名单，保持原版处理: cause=" + event.getCause().name());
            return false;
        }

        event.setCancelled(true);
        DamageContextVariables.Builder context = baseContext(event, target).toBuilder();
        if (rule.context() != null && !rule.context().isEmpty()) {
            context.putAll(rule.context());
        }
        double sourceDamage = event.getDamage();
        double baseDamage = rule.resolveDamage(sourceDamage);
        context.put("cause", event.getCause().name());
        context.put("damage_cause", event.getCause().name());
        context.put("damage_cause_id", event.getCause().name());
        context.put("base_damage", baseDamage);
        context.put("source_damage", sourceDamage);
        context.put("input_damage", sourceDamage);
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        String damageTypeId = rule.hasDamageType() ? rule.damageTypeId() : config.defaultDamageType();
        DamageContextVariables resolvedContext = context.build();
        debugCombat(null, target, null, "ENVIRONMENT_RESOLVED",
                "环境伤害已映射为 EA 伤害: target=" + describeEntity(target)
                + ", cause=" + event.getCause().name()
                + ", damageType=" + damageTypeId
                + ", sourceDamage=" + formatNumber(sourceDamage)
                + ", baseDamage=" + formatNumber(baseDamage));
        DamageContext damageContext = attributeService.createDamageContext(
                null,
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
                null,
                target,
                null,
                null,
                "ENVIRONMENT_RESOLVE_EMPTY",
                "环境伤害没有得到有效的 EA 伤害结果，命中流程到此结束。",
                "ENVIRONMENT_ASYNC_RESOLVED",
                "环境伤害已解析 EA 伤害: ",
                "ENVIRONMENT_APPLY",
                "开始执行下一 tick 的环境 EA 伤害落地。"
        );
        return true;
    }

    private DamageContextVariables baseContext(EntityDamageEvent event, LivingEntity target) {
        DamageContextVariables.Builder context = DamageContextVariables.builder();
        String cause = event.getCause().name();
        context.put("cause", cause);
        context.put("damage_cause", cause);
        context.put("damage_cause_id", cause);
        context.put("base_damage", event.getDamage());
        context.put("source_damage", event.getDamage());
        context.put("input_damage", event.getDamage());
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
            context.put("damager_type", byEntityEvent.getDamager().getType().name());
            context.put("damager_uuid", byEntityEvent.getDamager().getUniqueId().toString());
        }
        return context.build();
    }

    private void scheduleDamageApplication(Runnable action) {
        if (action == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, action);
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
            debugCombat(shooter, target, projectile, "PROJECTILE_SNAPSHOT_MISSING",
                    "投射物命中时未找到快照，已按严格快照模式终止结算: projectile=" + describeEntity(projectile));
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
            String emptyDetail,
            String resolvedPhase,
            String resolvedPrefix,
            String applyPhase,
            String applyDetail) {
        if (future == null) {
            debugCombat(attacker, target, projectile, emptyPhase, emptyDetail);
            return;
        }
        future.whenComplete((resolvedDamage, throwable) -> scheduleDamageApplication(() -> {
            if (throwable != null) {
                debugCombat(attacker, target, projectile, "ASYNC_DAMAGE_FAILED",
                        "异步伤害解析失败: " + rootCauseMessage(throwable));
                return;
            }
            if (resolvedDamage == null) {
                debugCombat(attacker, target, projectile, emptyPhase, emptyDetail);
                return;
            }
            debugCombat(attacker, target, projectile, resolvedPhase, resolvedPrefix + describeResolvedDamage(resolvedDamage));
            applySyntheticKnockback(target, visualSource, resolvedDamage.finalDamage());
            debugCombat(attacker, target, projectile, applyPhase, applyDetail);
            attributeService.applyResolvedDamage(resolvedDamage, visualSource, 0D);
        }));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return "unknown";
        }
        return current.getMessage();
    }

    private void applySyntheticKnockback(LivingEntity target, Entity source, double finalDamage) {
        if (target == null || source == null || finalDamage <= 0D || !target.isValid() || target.isDead()
                || !attributeService.config().syntheticHitKnockback()) {
            return;
        }
        double strength = Math.max(0D, attributeService.config().syntheticHitKnockbackStrength());
        if (strength <= 0D) {
            return;
        }
        Vector direction = source.getLocation().toVector().subtract(target.getLocation().toVector());
        direction.setY(0D);
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = source.getLocation().getDirection().multiply(-1D).setY(0D);
        }
        if (direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        direction.normalize();
        target.knockback(strength, direction.getX(), direction.getZ());
    }

    private void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String detail) {
        if (phase == null || detail == null) {
            return;
        }
        var debugService = attributeService.combatDebugService();
        boolean enabled = projectile != null
                ? debugService.shouldTrace(projectile, target)
                : debugService.shouldTrace(attacker, target);
        if (!enabled) {
            return;
        }
        debugService.log(phase, detail);
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
