package emaki.jiuwu.craft.corelib.item;

import org.bukkit.inventory.ItemStack;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;

final class CraftEngineDevApiAccessor {

    private CraftEngineDevApiAccessor() {
    }

    static ItemStack createItem(Key key, int amount) {
        try {
            BukkitItemDefinition definition = CraftEngineItems.byId(key);
            if (definition == null) {
                return null;
            }
            ItemStack itemStack = definition.buildBukkitItem();
            if (itemStack == null || itemStack.getType().isAir()) {
                return null;
            }
            itemStack.setAmount(amount);
            return itemStack;
        } catch (RuntimeException | LinkageError _) {
            return null;
        }
    }
}
