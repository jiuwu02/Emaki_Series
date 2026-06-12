package emaki.jiuwu.craft.corelib.web.preview;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

public record WebItemLayerPreviewRequest(
        String itemId,
        ItemStack baseItem,
        ItemStack currentItem,
        Map<String, Object> options
) {

    public WebItemLayerPreviewRequest {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
