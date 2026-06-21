package emaki.jiuwu.craft.strengthen.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;

public final class ScriptStrengthenModuleApi {

    private final EmakiStrengthenPlugin plugin;
    private final ScriptModuleContext moduleContext;
    private final ActionContext context;

    public ScriptStrengthenModuleApi(EmakiStrengthenPlugin plugin, ScriptModuleContext moduleContext) {
        this.plugin = plugin;
        this.moduleContext = moduleContext;
        this.context = moduleContext == null ? null : moduleContext.actionContext();
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

    @HostAccess.Export
    public boolean registerChanceRule(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptChanceRuleRegistry() != null
                && plugin.javaScriptChanceRuleRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerChanceRule(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerChanceRule(merged);
    }

    @HostAccess.Export
    public void unregisterChanceRule(String id) {
        if (plugin != null && plugin.javaScriptChanceRuleRegistry() != null) {
            plugin.javaScriptChanceRuleRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredChanceRules() {
        return plugin == null || plugin.javaScriptChanceRuleRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptChanceRuleRegistry().ids();
    }

    @HostAccess.Export
    public boolean onResult(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptResultHookRegistry() != null
                && plugin.javaScriptResultHookRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean onResult(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return onResult(merged);
    }

    @HostAccess.Export
    public void unregisterResultHook(String id) {
        if (plugin != null && plugin.javaScriptResultHookRegistry() != null) {
            plugin.javaScriptResultHookRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredResultHooks() {
        return plugin == null || plugin.javaScriptResultHookRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptResultHookRegistry().ids();
    }

    private JavaScriptRegistrationTracker tracker() {
        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().javaScriptRegistrationTracker();
    }
}
