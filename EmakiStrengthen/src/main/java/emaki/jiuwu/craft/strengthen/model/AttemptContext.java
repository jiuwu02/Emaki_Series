package emaki.jiuwu.craft.strengthen.model;

import org.bukkit.inventory.ItemStack;

public record AttemptContext(ItemStack targetItem,
        ItemStack baseMaterial,
        ItemStack supportMaterial,
        ItemStack protectionMaterial,
        ItemStack breakthroughMaterial) {

    public static AttemptContext of(ItemStack targetItem,
            ItemStack baseMaterial,
            ItemStack supportMaterial,
            ItemStack protectionMaterial,
            ItemStack breakthroughMaterial) {
        return new AttemptContext(targetItem, baseMaterial, supportMaterial, protectionMaterial, breakthroughMaterial);
    }
}
