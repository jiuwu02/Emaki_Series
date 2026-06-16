package emaki.jiuwu.craft.strengthen.script;

import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;

public final class ScriptStrengthenModuleApi {

    private final ActionContext context;

    public ScriptStrengthenModuleApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public boolean available() {
        return EmakiStrengthenApi.available();
    }

    @HostAccess.Export
    public boolean canStrengthen(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return EmakiStrengthenApi.canStrengthen(itemStack);
    }

    @HostAccess.Export
    public Map<String, Object> readState(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.strengthenStateToMap(EmakiStrengthenApi.readState(itemStack));
    }

    @HostAccess.Export
    public Map<String, Object> rebuild(String itemKey) {
        ItemStack itemStack = ScriptServiceApiSupport.item(context, itemKey);
        return ScriptServiceApiSupport.itemSummary(EmakiStrengthenApi.rebuild(itemStack));
    }
}
