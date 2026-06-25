package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * {@link GuiCloseContext} backed by a Bukkit {@link InventoryCloseEvent}.
 */
final class BukkitGuiCloseContext implements GuiCloseContext {

    private final InventoryCloseEvent event;

    BukkitGuiCloseContext(InventoryCloseEvent event) {
        this.event = event;
    }

    @Override
    public Player player() {
        HumanEntity who = event.getPlayer();
        return who instanceof Player p ? p : null;
    }

    @Override
    public int topInventorySize() {
        Inventory inventory = event.getInventory();
        return inventory == null ? 0 : inventory.getSize();
    }

    @Override
    public ItemStack topInventoryItem(int slot) {
        Inventory inventory = event.getInventory();
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) {
            return null;
        }
        return inventory.getItem(slot);
    }
}
