package emaki.jiuwu.craft.level.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.api.LevelTypeView;

public final class ScriptLevelModuleApi {

    private final EmakiLevelPlugin plugin;
    private final ScriptModuleContext moduleContext;

    public ScriptLevelModuleApi(EmakiLevelPlugin plugin, ScriptModuleContext moduleContext) {
        this.plugin = plugin;
        this.moduleContext = moduleContext;
    }

    @HostAccess.Export
    public boolean available() {
        return EmakiLevelApi.available();
    }

    @HostAccess.Export
    public java.util.List<String> typeIds() {
        return EmakiLevelApi.types().stream()
                .map(LevelTypeView::id)
                .sorted()
                .toList();
    }

    @HostAccess.Export
    public Map<String, Object> type(String typeId) {
        return EmakiLevelApi.type(typeId)
                .map(this::typeToMap)
                .orElseGet(Map::of);
    }

    @HostAccess.Export
    public int level(String playerUuid, String typeId) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0 : EmakiLevelApi.getLevel(uuid, typeId);
    }

    @HostAccess.Export
    public double exp(String playerUuid, String typeId) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0D : EmakiLevelApi.getExp(uuid, typeId);
    }

    @HostAccess.Export
    public double totalExp(String playerUuid, String typeId) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0D : EmakiLevelApi.getTotalExp(uuid, typeId);
    }

    @HostAccess.Export
    public double requiredExp(String playerUuid, String typeId, int targetLevel) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0D : EmakiLevelApi.getRequiredExp(uuid, typeId, targetLevel);
    }

    private Map<String, Object> typeToMap(LevelTypeView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", view.id());
        map.put("displayName", view.displayName());
        map.put("description", view.description());
        map.put("primary", view.primary());
        map.put("enabled", view.enabled());
        map.put("startLevel", view.startLevel());
        map.put("maxLevel", view.maxLevel());
        map.put("autoUpgrade", view.autoUpgrade());
        map.put("manualUpgrade", view.manualUpgrade());
        map.put("attributes", view.attributes());
        return map;
    }

    @HostAccess.Export
    public boolean registerExpRule(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptExpRuleRegistry() != null
                && plugin.javaScriptExpRuleRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean registerExpRule(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerExpRule(merged);
    }

    @HostAccess.Export
    public void unregisterExpRule(String id) {
        if (plugin != null && plugin.javaScriptExpRuleRegistry() != null) {
            plugin.javaScriptExpRuleRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredExpRules() {
        return plugin == null || plugin.javaScriptExpRuleRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptExpRuleRegistry().ids();
    }

    @HostAccess.Export
    public boolean onLevelUp(Map<String, ?> definition) {
        return plugin != null && plugin.javaScriptLevelUpHookRegistry() != null
                && plugin.javaScriptLevelUpHookRegistry().register(moduleContext, definition, tracker());
    }

    @HostAccess.Export
    public boolean onLevelUp(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return onLevelUp(merged);
    }

    @HostAccess.Export
    public void unregisterLevelUpHook(String id) {
        if (plugin != null && plugin.javaScriptLevelUpHookRegistry() != null) {
            plugin.javaScriptLevelUpHookRegistry().unregister(id);
        }
    }

    @HostAccess.Export
    public java.util.List<String> registeredLevelUpHooks() {
        return plugin == null || plugin.javaScriptLevelUpHookRegistry() == null
                ? java.util.List.of()
                : plugin.javaScriptLevelUpHookRegistry().ids();
    }

    private JavaScriptRegistrationTracker tracker() {
        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().javaScriptRegistrationTracker();
    }

    private UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
