package emaki.jiuwu.craft.corelib.api.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;

public final class ScriptItemApi {

    private final Map<String, ItemSnapshot> items;

    public ScriptItemApi(ActionContext context) {
        Map<String, ItemSnapshot> captured = new LinkedHashMap<>();
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.attributes().entrySet()) {
                if (entry.getValue() instanceof ItemStack itemStack) {
                    captured.put(entry.getKey(), ItemSnapshot.capture(itemStack));
                }
            }
        }
        this.items = Map.copyOf(captured);
    }

    @HostAccess.Export
    public boolean has(String attributeKey) {
        ItemSnapshot item = items.get(attributeKey);
        return item != null && item.exists();
    }

    @HostAccess.Export
    public String type(String attributeKey) {
        ItemSnapshot item = items.get(attributeKey);
        return item == null ? "" : item.type();
    }

    @HostAccess.Export
    public int amount(String attributeKey) {
        ItemSnapshot item = items.get(attributeKey);
        return item == null ? 0 : item.amount();
    }

    @HostAccess.Export
    public String displayName(String attributeKey) {
        ItemSnapshot item = items.get(attributeKey);
        return item == null ? "" : item.displayName();
    }

    private record ItemSnapshot(boolean exists, String type, int amount, String displayName) {

        private static ItemSnapshot capture(ItemStack itemStack) {
            boolean exists = itemStack != null && !itemStack.getType().isAir();
            return new ItemSnapshot(
                    exists,
                    exists ? itemStack.getType().name().toLowerCase(java.util.Locale.ROOT) : "",
                    exists ? itemStack.getAmount() : 0,
                    exists ? ItemTextBridge.effectiveNamePlain(itemStack) : ""
            );
        }
    }
}
