package emaki.jiuwu.craft.cooking.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.EmakiCookingApi;

public final class ScriptCookingModuleApi {

    private final EmakiCookingPlugin plugin;
    private final ScriptModuleContext moduleContext;

    public ScriptCookingModuleApi(EmakiCookingPlugin plugin, ScriptModuleContext moduleContext) {
        this.plugin = plugin;
        this.moduleContext = moduleContext;
    }

    @HostAccess.Export
    public boolean available() {
        return EmakiCookingApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiCookingApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiCookingApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiCookingApi.isReady();
    }

    @HostAccess.Export
    public boolean registerResultRule(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptResultRuleRegistry() != null
                && plugin.javaScriptResultRuleRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerResultRule(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerResultRule(merged);
    }

    @HostAccess.Export
    public void unregisterResultRule(String id) {
        if (plugin != null && plugin.javaScriptResultRuleRegistry() != null) {
            plugin.javaScriptResultRuleRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredResultRules() {
        return plugin == null || plugin.javaScriptResultRuleRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptResultRuleRegistry().ids();
    }

    @HostAccess.Export
    public boolean onComplete(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptCompleteHookRegistry() != null
                && plugin.javaScriptCompleteHookRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean onComplete(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return onComplete(merged);
    }

    @HostAccess.Export
    public void unregisterCompleteHook(String id) {
        if (plugin != null && plugin.javaScriptCompleteHookRegistry() != null) {
            plugin.javaScriptCompleteHookRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredCompleteHooks() {
        return plugin == null || plugin.javaScriptCompleteHookRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptCompleteHookRegistry().ids();
    }

    private JavaScriptRegistrationTracker tracker() {
        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().javaScriptRegistrationTracker();
    }
}
