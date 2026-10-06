package emaki.jiuwu.craft.corelib.item;

import java.util.Set;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

public interface PlayerItemRefreshService {

    void refreshPlayerInventory(Player player);

    default void refreshPlayerSlots(Player player, Set<Integer> slots, boolean full) {
        refreshPlayerInventory(player);
    }

    void refreshDroppedItem(Item itemEntity);
}
