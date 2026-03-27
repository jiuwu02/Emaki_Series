package emaki.jiuwu.craft.attribute.listener;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import java.util.LinkedHashMap;
import java.util.Map;
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
        attributeService.scheduleHealthSync(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        attributeService.scheduleHealthSync(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("MythicMobs".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensureMythicBridge();
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
            attributeService.snapshotProjectile(projectile, livingEntity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LivingEntity target = event.getEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (target == null) {
            return;
        }
        // Vanilla damage is treated as input only; Emaki applies the real health change itself.
        event.setCancelled(true);
        Map<String, Object> context = baseContext(event, target);
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            attributeService.applyProjectileDamage(projectile, target, event.getDamage(), context);
            return;
        }
        LivingEntity attacker = damager instanceof LivingEntity livingEntity ? livingEntity : null;
        attributeService.applyDamage(attacker, target, null, event.getDamage(), context);
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
        // Vanilla damage is treated as input only; Emaki applies the real health change itself.
        event.setCancelled(true);
        Map<String, Object> context = baseContext(event, target);
        attributeService.applyDamage(null, target, null, event.getDamage(), context);
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

    private Map<String, Object> baseContext(EntityDamageEvent event, LivingEntity target) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("cause", event.getCause().name());
        context.put("base_damage", event.getDamage());
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
            context.put("damager_type", byEntityEvent.getDamager().getType().name());
            context.put("damager_uuid", byEntityEvent.getDamager().getUniqueId().toString());
        }
        return context;
    }
}
