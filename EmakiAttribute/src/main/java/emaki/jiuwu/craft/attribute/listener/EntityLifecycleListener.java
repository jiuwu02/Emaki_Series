package emaki.jiuwu.craft.attribute.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import emaki.jiuwu.craft.attribute.service.AttributeService;

public final class EntityLifecycleListener implements Listener {

    private final AttributeService attributeService;

    public EntityLifecycleListener(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        attributeService.cleanupEntityState(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player || !(entity instanceof LivingEntity)) {
            return;
        }
        attributeService.cleanupEntityState(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (LivingEntity entity : event.getWorld().getLivingEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            attributeService.cleanupEntityState(entity.getUniqueId());
        }
    }
}
