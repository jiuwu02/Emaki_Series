package emaki.jiuwu.craft.corelib.api.script;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;

public final class ScriptItemApi {

    private final ActionContext context;

    public ScriptItemApi(ActionContext context) {
        this.context = context;
    }

    public boolean has(String attributeKey) {
        ItemStack itemStack = item(attributeKey);
        return itemStack != null && !itemStack.getType().isAir();
    }

    public String type(String attributeKey) {
        ItemStack itemStack = item(attributeKey);
        return itemStack == null ? "" : itemStack.getType().name().toLowerCase(java.util.Locale.ROOT);
    }

    public int amount(String attributeKey) {
        ItemStack itemStack = item(attributeKey);
        return itemStack == null ? 0 : itemStack.getAmount();
    }

    public String displayName(String attributeKey) {
        ItemStack itemStack = item(attributeKey);
        return itemStack == null ? "" : ItemTextBridge.effectiveNamePlain(itemStack);
    }

    private ItemStack item(String attributeKey) {
        Object value = context == null ? null : context.attribute(attributeKey);
        return value instanceof ItemStack itemStack ? itemStack : null;
    }
}
