package emaki.jiuwu.craft.attribute.script.js;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptDamageHookRegistry {

    private final EmakiAttributePlugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final List<Hook> hooks = new ArrayList<>();

    public JavaScriptDamageHookRegistry(EmakiAttributePlugin plugin, JavaScriptService javaScriptService, ScriptConfig scriptConfig) {
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
        for (Hook hook : List.copyOf(hooks)) {
            if (!hook.matches(event.getDamageTypeId())) {
                continue;
            }
            ScriptDamageEventApi api = new ScriptDamageEventApi(event);
            try {
                ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                        plugin,
                        null,
                        hook.scriptPath(),
                        hook.functionName(),
                        List.of(api),
                        ScriptSnapshots.immutableMap(Map.of(
                                "hook", hook.id(),
                                "damage_type", event.getDamageTypeId()
                        )),
                        scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                        true
                ));
                if (result == null || !result.success()) {
                    plugin.messageService().warning("console.js_damage_hook_failed", Map.of(
                            "id", hook.id(),
                            "error", result == null ? "no result" : Texts.toStringSafe(result.message())
                    ));
                    continue;
                }
                api.commitTo(event);
            } catch (RuntimeException exception) {
                plugin.messageService().warning("console.js_damage_hook_failed", Map.of(
                        "id", hook.id(),
                        "error", Texts.toStringSafe(exception.getMessage())
                ));
                continue;
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
