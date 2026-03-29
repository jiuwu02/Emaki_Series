package emaki.jiuwu.craft.attribute.listener;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.service.AttributeService;
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
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

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
                event.setCancelled(true);
                return;
            }
            var snapshot = attributeService.snapshotProjectile(projectile, livingEntity);
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
            if (shooter instanceof Player player && attributeService.isAttackCoolingDown(player)) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            attributeService.applyProjectileDamage(projectile, target, 0D, context);
            return;
        }
        LivingEntity attacker = damager instanceof LivingEntity livingEntity ? livingEntity : null;
        if (attacker instanceof Player player && attributeService.isAttackCoolingDown(player)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        attributeService.applyDamage(attacker, target, null, 0D, context);
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
                return attributeService.applyDamage(null, target, config.defaultDamageType(), 0D, baseContext(event, target));
            }
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
        return attributeService.applyDamage(null, target, damageTypeId, baseDamage, context.build());
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
}
