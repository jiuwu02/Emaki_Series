package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

public record GuiItems(ItemStack targetItem,
                       Map<Integer, ItemStack> blueprints,
                       Map<Integer, ItemStack> requiredMaterials,
                       Map<Integer, ItemStack> optionalMaterials) {

    public List<ItemStack> blueprintList() {
        return new ArrayList<>(blueprints.values());
    }
}
