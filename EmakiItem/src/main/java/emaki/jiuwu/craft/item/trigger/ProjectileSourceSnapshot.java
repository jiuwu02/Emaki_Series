package emaki.jiuwu.craft.item.trigger;

import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.item.ItemPdcKeys;

public final class ProjectileSourceSnapshot {

    private ProjectileSourceSnapshot() {
    }

    public static void write(Projectile projectile, ItemStack launchItem) {
        if (projectile == null || launchItem == null || launchItem.getType().isAir()) {
            return;
        }
        ItemStack snapshot = launchItem.clone();
        snapshot.setAmount(1);
        projectile.getPersistentDataContainer().set(
                ItemPdcKeys.PROJECTILE_SOURCE_ITEM,
                PersistentDataType.BYTE_ARRAY,
                snapshot.serializeAsBytes());
    }

    public static boolean has(Projectile projectile) {
        return projectile != null && projectile.getPersistentDataContainer()
                .has(ItemPdcKeys.PROJECTILE_SOURCE_ITEM, PersistentDataType.BYTE_ARRAY);
    }

    public static ItemStack read(Projectile projectile) {
        if (projectile == null) {
            return null;
        }
        PersistentDataContainer container = projectile.getPersistentDataContainer();
        byte[] payload = container.get(ItemPdcKeys.PROJECTILE_SOURCE_ITEM, PersistentDataType.BYTE_ARRAY);
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(payload);
        } catch (RuntimeException exception) {
            container.remove(ItemPdcKeys.PROJECTILE_SOURCE_ITEM);
            return null;
        }
    }
}
