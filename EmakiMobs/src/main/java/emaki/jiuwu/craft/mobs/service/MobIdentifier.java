package emaki.jiuwu.craft.mobs.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class MobIdentifier {

    private final NamespacedKey mobIdKey;

    public MobIdentifier(Plugin plugin) {
        mobIdKey = new NamespacedKey(plugin, "mob_id");
    }

    public void mark(LivingEntity entity, String mobId) {
        entity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, mobId);
    }

    public String readId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
    }

    public boolean isManaged(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(mobIdKey, PersistentDataType.STRING);
    }
}
