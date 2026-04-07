package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

final class TemporaryItemStore {

    private static final String SHARED_KEY = "action_temporary_items";

    private final Map<String, ItemStack> items;

    private TemporaryItemStore(Map<String, ItemStack> items) {
        this.items = items;
    }

    static TemporaryItemStore from(ActionContext context) {
        if (context == null) {
            return new TemporaryItemStore(new ConcurrentHashMap<>());
        }
        Object existing = context.sharedValue(SHARED_KEY);
        if (existing instanceof Map<?, ?> raw) {
            Map<String, ItemStack> items = new ConcurrentHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null || !(entry.getValue() instanceof ItemStack itemStack)) {
                    continue;
                }
                items.put(normalizeId(String.valueOf(entry.getKey())), itemStack);
            }
            context.sharedState().put(SHARED_KEY, items);
            return new TemporaryItemStore(items);
        }
        Map<String, ItemStack> created = new ConcurrentHashMap<>();
        context.sharedState().put(SHARED_KEY, created);
        return new TemporaryItemStore(created);
    }

    ItemStack get(String id) {
        ItemStack itemStack = items.get(normalizeId(id));
        return itemStack == null ? null : itemStack.clone();
    }

    void put(String id, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            items.remove(normalizeId(id));
            return;
        }
        items.put(normalizeId(id), itemStack.clone());
    }

    ItemStack remove(String id) {
        ItemStack itemStack = items.remove(normalizeId(id));
        return itemStack == null ? null : itemStack.clone();
    }

    Map<String, ItemStack> snapshot() {
        Map<String, ItemStack> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().clone());
        }
        return copy;
    }

    private static String normalizeId(String value) {
        String normalized = Texts.trim(value).toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "" : normalized;
    }
}
