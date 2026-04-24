package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.inventory.ItemStack;

public record AttemptContext(ItemStack targetItem, List<ItemStack> materialInputs) {

    public AttemptContext {
        targetItem = normalizeItem(targetItem);
        if (materialInputs == null || materialInputs.isEmpty()) {
            materialInputs = List.of();
        } else {
            List<ItemStack> normalized = new ArrayList<>(materialInputs.size());
            for (ItemStack itemStack : materialInputs) {
                normalized.add(normalizeItem(itemStack));
            }
            materialInputs = Collections.unmodifiableList(normalized);
        }
    }

    public static AttemptContext of(ItemStack targetItem, List<ItemStack> materialInputs) {
        return new AttemptContext(targetItem, materialInputs);
    }

    private static ItemStack normalizeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return itemStack.clone();
    }
}
