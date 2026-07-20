package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;









public interface GuiCloseContext {

    Player player();




    int topInventorySize();




    ItemStack topInventoryItem(int slot);
}
