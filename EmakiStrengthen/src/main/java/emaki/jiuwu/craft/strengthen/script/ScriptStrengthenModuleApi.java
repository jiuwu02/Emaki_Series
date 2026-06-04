package emaki.jiuwu.craft.strengthen.script;

import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;

public final class ScriptStrengthenModuleApi {

    private static final String SERVICE = "emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi";

    private final ActionContext context;

    public ScriptStrengthenModuleApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public boolean available() {
        return ScriptServiceApiSupport.available(SERVICE);
    }

    @HostAccess.Export
    public boolean canStrengthen(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service, "canStrengthen", new Class<?>[] { ItemStack.class }, itemStack))
                .orElse(false);
    }

    @HostAccess.Export
    public Map<String, Object> readState(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.strengthenStateToMap(ScriptServiceApiSupport.invoke(service, "readState", new Class<?>[] { ItemStack.class }, itemStack)))
                .orElseGet(Map::of);
    }

    @HostAccess.Export
    public Map<String, Object> rebuild(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.service(SERVICE)
                .map(service -> ScriptServiceApiSupport.itemSummary((ItemStack) ScriptServiceApiSupport.invoke(service, "rebuild", new Class<?>[] { ItemStack.class }, itemStack)))
                .orElseGet(Map::of);
    }
}
