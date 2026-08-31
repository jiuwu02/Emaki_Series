package emaki.jiuwu.craft.mobs.selector;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.EquipmentSlot;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

public record EquipmentWeightTable(
        String id,
        Set<EquipmentSlot> slots,
        double defaultWeight,
        Map<ItemSourceRef, Double> weights
) {

    public EquipmentWeightTable {
        slots = slots == null ? Set.of() : Set.copyOf(slots);
        weights = weights == null ? Map.of() : Map.copyOf(weights);
    }
}
