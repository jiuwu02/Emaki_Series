package emaki.jiuwu.craft.strengthen.model;

import java.util.List;

import org.bukkit.inventory.ItemStack;

public record AttemptContext(ItemStack targetItem, List<ItemStack> materialInputs) {

    public AttemptContext {
        materialInputs = materialInputs == null ? List.of() : List.copyOf(materialInputs);
    }

    public static AttemptContext of(ItemStack targetItem, List<ItemStack> materialInputs) {
        return new AttemptContext(targetItem, materialInputs);
    }
}
