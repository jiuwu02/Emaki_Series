package emaki.jiuwu.craft.forge.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.EmakiForgeApi;

public final class ScriptForgeModuleApi {

    private final EmakiForgePlugin plugin;
    private final ScriptModuleContext moduleContext;

    public ScriptForgeModuleApi(EmakiForgePlugin plugin, ScriptModuleContext moduleContext) {
        this.plugin = plugin;
        this.moduleContext = moduleContext;
    }

    @HostAccess.Export
    public boolean available() {
        return EmakiForgeApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiForgeApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiForgeApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiForgeApi.isReady();
    }

    @HostAccess.Export
    public boolean registerForgeRule(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptForgeRuleRegistry() != null
                && plugin.javaScriptForgeRuleRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerForgeRule(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerForgeRule(merged);
    }

    @HostAccess.Export
    public void unregisterForgeRule(String id) {
        if (plugin != null && plugin.javaScriptForgeRuleRegistry() != null) {
            plugin.javaScriptForgeRuleRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredForgeRules() {
        return plugin == null || plugin.javaScriptForgeRuleRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptForgeRuleRegistry().ids();
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
