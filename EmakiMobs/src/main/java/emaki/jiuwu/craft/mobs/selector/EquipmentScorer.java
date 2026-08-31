package emaki.jiuwu.craft.mobs.selector;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

final class EquipmentScorer {

    private final ItemSourceService itemSourceService;

    EquipmentScorer(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

    double score(Player player, EquipmentWeightTable table) {
        EntityEquipment equipment = player.getEquipment();
        double score = 0D;
        for (var slot : table.slots()) {
            ItemStack stack = equipment.getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemSourceRef ref = itemSourceService.identifyItem(stack);
            score += ref == null
                    ? table.defaultWeight()
                    : table.weights().getOrDefault(ref, table.defaultWeight());
        }
        return score;
    }
}
