package emaki.jiuwu.craft.corelib.api.script.modules;

import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class ScriptItemModuleApi {

    private static final String SERVICE = "emaki.jiuwu.craft.item.api.EmakiItemApi";

    private final ActionContext context;

    public ScriptItemModuleApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public boolean available() {
        return ScriptServiceApiSupport.available(SERVICE);
    }

    @HostAccess.Export
    public boolean exists(String id) {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "exists", new Class<?>[] { String.class }, id))
                .orElse(false);
    }

    @HostAccess.Export
    public Map<String, Object> create(String id, int amount) {
        int safeAmount = Math.max(1, Math.min(64, amount));
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.itemSummary((ItemStack) ScriptServiceApiSupport.invoke(service, "create", new Class<?>[] { String.class, int.class }, id, safeAmount)))
                .orElseGet(Map::of);
    }

    @HostAccess.Export
    public String identify(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeString(service, "identify", new Class<?>[] { ItemStack.class }, itemStack))
                .orElse("");
    }

    @HostAccess.Export
    public java.util.List<String> definitionIds() {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.toStringList(ScriptServiceApiSupport.invoke(service, "definitionIds", new Class<?>[0])))
                .orElseGet(java.util.List::of);
    }

    @HostAccess.Export
    public String displayName(String id) {
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeString(service, "displayName", new Class<?>[] { String.class }, id))
                .orElse("");
    }
}
