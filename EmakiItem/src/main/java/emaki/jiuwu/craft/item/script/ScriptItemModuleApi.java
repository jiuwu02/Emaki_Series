package emaki.jiuwu.craft.item.script;

import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class ScriptItemModuleApi {

    private final ActionContext context;

    public ScriptItemModuleApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public boolean available() {
        return EmakiItemApi.available();
    }

    @HostAccess.Export
    public boolean exists(String id) {
        return EmakiItemApi.exists(id);
    }

    @HostAccess.Export
    public Map<String, Object> create(String id, int amount) {
        int safeAmount = Math.max(1, Math.min(64, amount));
        return ScriptServiceApiSupport.itemSummary(EmakiItemApi.create(id, safeAmount));
    }

    @HostAccess.Export
    public String identify(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        String id = EmakiItemApi.identify(itemStack);
        return id == null ? "" : id;
    }

    @HostAccess.Export
    public java.util.List<String> definitionIds() {
        return java.util.List.copyOf(EmakiItemApi.definitionIds());
    }

    @HostAccess.Export
    public String displayName(String id) {
        return EmakiItemApi.displayName(id);
    }
}
