package emaki.jiuwu.craft.corelib.item.preview;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public record ItemLayerPreviewResult(
        String id,
        boolean available,
        String reason,
        ItemStack itemStack,
        Map<String, ?> details,
        Map<String, ?> options,
        Map<String, ?> selected
) {

    public ItemLayerPreviewResult {
        details = details == null ? Map.of() : Map.copyOf(details);
        options = options == null ? Map.of() : Map.copyOf(options);
        selected = selected == null ? Map.of() : Map.copyOf(selected);
    }

    public static ItemLayerPreviewResult unavailable(String id, String reason, Map<String, ?> details, Map<String, ?> options) {
        return new ItemLayerPreviewResult(id, false, reason, null, details, options, Map.of());
    }

    public static ItemLayerPreviewResult available(String id, String reason, ItemStack itemStack, Map<String, ?> details, Map<String, ?> options, Map<String, ?> selected) {
        return new ItemLayerPreviewResult(id, true, reason, itemStack, details, options, selected);
    }

    public Map<String, Object> toLayerMap(Map<String, ?> preview) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id == null ? "" : id);
        map.put("available", available);
        map.put("reason", reason == null ? "" : reason);
        map.put("details", details);
        map.put("options", options);
        map.put("selected", selected);
        map.put("preview", preview == null ? Map.of() : preview);
        map.put("status", available ? "available" : "unavailable");
        return map;
    }
}
