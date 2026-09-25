package emaki.jiuwu.craft.accessory.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class AccessoryEffectGate {

    private AccessoryEffectGate() {
    }

    public static boolean active(Player owner, ItemStack item) {
        return conditionsMet(owner, item) && durabilityIntact(item);
    }

    public static boolean conditionsMet(Player owner, ItemStack item) {
        if (owner == null || item == null || item.getType().isAir() || !EmakiItemApi.status().usable()) {
            return true;
        }
        return EmakiItemApi.catalog().conditionSatisfied(owner, item).orElse(Boolean.TRUE);
    }

    public static boolean durabilityIntact(ItemStack item) {
        int maxDurability = maxDurability(item);
        return maxDurability <= 0 || damage(item) < maxDurability;
    }

    public static int maxDurability(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            return Math.max(0, damageable.getMaxDamage());
        }
        return Math.max(0, item.getType().getMaxDurability());
    }

    public static int damage(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta instanceof Damageable damageable ? Math.max(0, damageable.getDamage()) : 0;
    }

    public static int applyDamage(ItemStack item, int points) {
        int maxDurability = maxDurability(item);
        if (maxDurability <= 0 || points <= 0) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 0;
        }
        int current = Math.max(0, damageable.getDamage());
        if (current >= maxDurability) {
            return 0;
        }
        int applied = Math.min(points, maxDurability - current);
        damageable.setDamage(current + applied);
        item.setItemMeta(meta);
        return applied;
    }
}