package emaki.jiuwu.craft.mobs.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class MobIdentifier {

    private final NamespacedKey mobIdKey;
    private final NamespacedKey fireImmuneKey;

    public MobIdentifier(Plugin plugin) {
        mobIdKey = new NamespacedKey(plugin, "mob_id");
        fireImmuneKey = new NamespacedKey(plugin, "fire_immune");
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
