package emaki.jiuwu.craft.strengthen.model;

import java.util.List;

import org.bukkit.inventory.ItemStack;

public record AttemptContext(ItemStack targetItem, List<ItemStack> materialInputs) {

    public AttemptContext {
        targetItem = normalizeItem(targetItem);
        materialInputs = materialInputs == null
                ? List.of()
                : materialInputs.stream()
                        .map(AttemptContext::normalizeItem)
                        .filter(itemStack -> itemStack != null)
                        .toList();
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
