package emaki.jiuwu.craft.corelib.item.preview;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

public record ItemLayerPreviewRequest(
        String itemId,
        ItemStack baseItem,
        ItemStack currentItem,
        Map<String, Object> options
) {

    public ItemLayerPreviewRequest {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
