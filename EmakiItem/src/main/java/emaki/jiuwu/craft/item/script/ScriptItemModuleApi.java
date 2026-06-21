package emaki.jiuwu.craft.item.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class ScriptItemModuleApi {

    private final EmakiItemPlugin plugin;
    private final ScriptModuleContext moduleContext;
    private final ActionContext context;

    public ScriptItemModuleApi(EmakiItemPlugin plugin, ScriptModuleContext moduleContext) {
        this.plugin = plugin;
        this.moduleContext = moduleContext;
        this.context = moduleContext == null ? null : moduleContext.actionContext();
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

    @HostAccess.Export
    public boolean registerDefinition(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptDefinitionRegistry() != null
                && plugin.javaScriptDefinitionRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerDefinition(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerDefinition(merged);
    }

    @HostAccess.Export
    public void unregisterDefinition(String id) {
        if (plugin != null && plugin.javaScriptDefinitionRegistry() != null) {
            plugin.javaScriptDefinitionRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredDefinitions() {
        return plugin == null || plugin.javaScriptDefinitionRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptDefinitionRegistry().ids();
    }

    @HostAccess.Export
    public boolean registerFactory(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptFactoryRegistry() != null
                && plugin.javaScriptFactoryRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerFactory(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerFactory(merged);
    }

    @HostAccess.Export
    public void unregisterFactory(String id) {
        if (plugin != null && plugin.javaScriptFactoryRegistry() != null) {
            plugin.javaScriptFactoryRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredFactories() {
        return plugin == null || plugin.javaScriptFactoryRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptFactoryRegistry().ids();
    }

    @HostAccess.Export
    public Map<String, Object> createFactory(String id, int amount) {
        if (plugin == null || plugin.javaScriptFactoryRegistry() == null) {
            return Map.of();
        }
        return ScriptServiceApiSupport.itemSummary(plugin.javaScriptFactoryRegistry().create(id, amount));
    }

    private JavaScriptRegistrationTracker tracker() {
        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().javaScriptRegistrationTracker();
    }
}
