package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * {@link GuiDragContext} backed by a Bukkit {@link InventoryDragEvent}.
 */
final class BukkitGuiDragContext implements GuiDragContext {

    private final InventoryDragEvent event;

    BukkitGuiDragContext(InventoryDragEvent event) {
        this.event = event;
    }

    private Player player() {
        HumanEntity who = event.getWhoClicked();
        return who instanceof Player p ? p : null;
    }

    @Override
    public Player viewer() {
        return player();
    }

    @Override
    public Set<Integer> rawSlots() {
        return event.getRawSlots();
    }

    @Override
    public Map<Integer, ItemStack> newItems() {
        return event.getNewItems();
    }

    @Override
    public ItemStack oldCursor() {
        return event.getOldCursor();
    }

    @Override
    public void setCursor(ItemStack item) {
        Player player = player();
        if (player != null) {
            player.setItemOnCursor(item);
        }
    }
}
