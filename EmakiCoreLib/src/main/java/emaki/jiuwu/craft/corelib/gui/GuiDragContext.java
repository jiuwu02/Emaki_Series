package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;










public interface GuiDragContext {

    Player viewer();




    Set<Integer> rawSlots();




    Map<Integer, ItemStack> newItems();





    ItemStack oldCursor();

    void setCursor(ItemStack item);
}
