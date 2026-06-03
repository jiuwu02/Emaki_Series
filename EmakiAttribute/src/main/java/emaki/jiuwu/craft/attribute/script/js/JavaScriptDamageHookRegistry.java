package emaki.jiuwu.craft.attribute.script.js;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptDamageHookRegistry {

    private final org.bukkit.plugin.Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final List<Hook> hooks = new ArrayList<>();

    public JavaScriptDamageHookRegistry(org.bukkit.plugin.Plugin plugin, JavaScriptService javaScriptService, ScriptConfig scriptConfig) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
    }

    public void register(String id, int priority, Set<String> damageTypes, String scriptPath, String functionName) {
        if (Texts.isBlank(id) || Texts.isBlank(scriptPath) || Texts.isBlank(functionName)) {
            return;
        }
        unregister(id);
        hooks.add(new Hook(Texts.normalizeId(id), priority, damageTypes == null ? Set.of() : Set.copyOf(damageTypes), scriptPath, functionName));
        hooks.sort(Comparator.comparingInt(Hook::priority).reversed().thenComparing(Hook::id));
    }

    public void unregister(String id) {
        hooks.removeIf(hook -> hook.id().equals(Texts.normalizeId(id)));
    }

    public void clear() {
        hooks.clear();
    }

    public int size() {
        return hooks.size();
    }

    public void handle(EmakiAttributeDamageEvent event) {
        if (event == null || javaScriptService == null || !javaScriptService.enabled()) {
            return;
        }
        ScriptDamageEventApi api = new ScriptDamageEventApi(event);
        for (Hook hook : List.copyOf(hooks)) {
            if (!hook.matches(event.getDamageTypeId())) {
                continue;
            }
            try {
                javaScriptService.invoke(new ScriptInvocationRequest(
                        plugin,
                        null,
                        hook.scriptPath(),
                        hook.functionName(),
                        List.of(api),
                        Map.of("hook", hook.id(), "damage_type", event.getDamageTypeId()),
                        scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                        true
                ));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("JavaScript damage hook failed: " + hook.id() + " - " + exception.getMessage());
            }
            if (event.isCancelled()) {
                return;
            }
        }
    }

    private record Hook(String id, int priority, Set<String> damageTypes, String scriptPath, String functionName) {

        boolean matches(String damageTypeId) {
            return damageTypes.isEmpty() || damageTypes.contains(Texts.normalizeId(damageTypeId));
        }
    }
}
