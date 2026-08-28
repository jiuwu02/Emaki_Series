package emaki.jiuwu.craft.mobs.service;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MobIdentifier implements Listener {

    private final NamespacedKey mobIdKey;
    private final NamespacedKey fireImmuneKey;
    private final ConcurrentMap<UUID, LivingEntity> trackedEntities = new ConcurrentHashMap<>();

    public MobIdentifier(Plugin plugin) {
        mobIdKey = new NamespacedKey(plugin, "mob_id");
        fireImmuneKey = new NamespacedKey(plugin, "fire_immune");
    }

    public void mark(LivingEntity entity, String mobId) {
        entity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, mobId);
        trackedEntities.put(entity.getUniqueId(), entity);
    }

    public String readId(LivingEntity entity) {
        String mobId = entity.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        if (mobId != null) {
            trackedEntities.put(entity.getUniqueId(), entity);
        }
        return mobId;
    }

    public boolean isManaged(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(mobIdKey, PersistentDataType.STRING);
    }

    public List<LivingEntity> trackedEntities() {
        return List.copyOf(trackedEntities.values());
    }

    public void forget(Entity entity) {
        if (entity != null) {
            trackedEntities.remove(entity.getUniqueId());
        }
    }

    public void clearTracked() {
        trackedEntities.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        forget(event.getEntity());
    }

    public void setFireImmune(LivingEntity entity, boolean immune) {
        if (immune) {
            entity.getPersistentDataContainer().set(fireImmuneKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            entity.getPersistentDataContainer().remove(fireImmuneKey);
        }
    }

    public boolean isFireImmune(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(fireImmuneKey, PersistentDataType.BYTE);
    }
}
