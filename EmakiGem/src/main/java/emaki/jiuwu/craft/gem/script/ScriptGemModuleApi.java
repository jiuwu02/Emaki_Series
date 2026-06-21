package emaki.jiuwu.craft.gem.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.EmakiGemApi;

public final class ScriptGemModuleApi {

    private final EmakiGemPlugin plugin;
    private final ScriptModuleContext moduleContext;

    public ScriptGemModuleApi(EmakiGemPlugin plugin, ScriptModuleContext moduleContext) {
        this.plugin = plugin;
        this.moduleContext = moduleContext;
    }

    @HostAccess.Export
    public boolean available() {
        return EmakiGemApi.available();
    }

    @HostAccess.Export
    public String apiVersion() {
        return EmakiGemApi.apiVersion();
    }

    @HostAccess.Export
    public String pluginName() {
        return EmakiGemApi.pluginName();
    }

    @HostAccess.Export
    public boolean ready() {
        return EmakiGemApi.isReady();
    }

    @HostAccess.Export
    public boolean registerSocketRule(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptSocketRuleRegistry() != null
                && plugin.javaScriptSocketRuleRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerSocketRule(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerSocketRule(merged);
    }

    @HostAccess.Export
    public void unregisterSocketRule(String id) {
        if (plugin != null && plugin.javaScriptSocketRuleRegistry() != null) {
            plugin.javaScriptSocketRuleRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredSocketRules() {
        return plugin == null || plugin.javaScriptSocketRuleRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptSocketRuleRegistry().ids();
    }

    @HostAccess.Export
    public boolean registerSetBonus(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptSetBonusRegistry() != null
                && plugin.javaScriptSetBonusRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerSetBonus(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerSetBonus(merged);
    }

    @HostAccess.Export
    public void unregisterSetBonus(String id) {
        if (plugin != null && plugin.javaScriptSetBonusRegistry() != null) {
            plugin.javaScriptSetBonusRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredSetBonuses() {
        return plugin == null || plugin.javaScriptSetBonusRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptSetBonusRegistry().ids();
    }

    private JavaScriptRegistrationTracker tracker() {
        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().javaScriptRegistrationTracker();
    }
}
