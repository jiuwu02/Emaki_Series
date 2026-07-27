package emaki.jiuwu.craft.item.api.preview;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

/**
 * Immutable request snapshot passed to an item layer preview provider.
 */
public record ItemLayerPreviewRequest(
        String itemId,
        ItemStack baseItem,
        ItemStack currentItem,
        Map<String, Object> options
) {

    public ItemLayerPreviewRequest {
        baseItem = cloneItem(baseItem);
        currentItem = cloneItem(currentItem);
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    @Override
    public ItemStack baseItem() {
        return cloneItem(baseItem);
    }

    @Override
    public ItemStack currentItem() {
        return cloneItem(currentItem);
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }
}
