package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class AttributeSnapshotService {

    private final AttributeSnapshotCollector collector;

    AttributeSnapshotService(AttributeSnapshotCollector collector) {
        this.collector = collector;
    }

    AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        return collector.collectItemSnapshot(itemStack);
    }

    AttributeSnapshot collectCombatSnapshot(LivingEntity entity) {
        return collector.collectCombatSnapshot(entity);
    }

    AttributeSnapshot collectPlayerCombatSnapshot(Player player) {
        return collector.collectPlayerCombatSnapshot(player);
    }
}
