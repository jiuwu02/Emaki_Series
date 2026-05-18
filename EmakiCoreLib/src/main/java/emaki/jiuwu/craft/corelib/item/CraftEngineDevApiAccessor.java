package emaki.jiuwu.craft.corelib.item;

import org.bukkit.inventory.ItemStack;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;

/**
 * CraftEngine item creation accessor targeting the stable API (26.5+).
 * <p>
 * Uses {@link CraftEngineItems#byId(Key)} which returns {@link BukkitItemDefinition},
 * then calls {@link BukkitItemDefinition#buildBukkitItem()} to produce an {@link ItemStack}.
 */
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
