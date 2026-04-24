package emaki.jiuwu.craft.corelib.item;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

public interface PlayerItemRefreshService {

    void refreshPlayerInventory(Player player);

    void refreshDroppedItem(Item itemEntity);
}
