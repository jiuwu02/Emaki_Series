package emaki.jiuwu.craft.strengthen.script.js;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationType;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;

public final class JavaScriptStrengthenResultHookRegistry {

    private final EmakiStrengthenPlugin plugin;
    private final Map<String, HookEntry> hooks = new LinkedHashMap<>();

    public JavaScriptStrengthenResultHookRegistry(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Strengthen result hook cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Strengthen result hook id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "onStrengthenResult"));
        HookEntry entry = new HookEntry(id, function, scriptPath(context), longValue(definition.get("timeoutMillis"), defaultTimeout(context)));
        long started = System.nanoTime();
        hooks.put(id, entry);
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                JavaScriptRegistrationType.STRENGTHEN_RESULT_HOOK,
                id,
                elapsedMillis(started),
                () -> unregister(id),
                Map.of("function", function))) {
            hooks.remove(id);
            return false;
        }
        return true;
    }

    public synchronized void unregister(String id) {
        hooks.remove(Texts.normalizeId(id));
    }

    public synchronized void clear() {
        hooks.clear();
    }

    public synchronized List<String> ids() {
        return hooks.keySet().stream().sorted().toList();
    }

    public void fire(Player player, AttemptResult result) {
        if (result == null || plugin == null) {
            return;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return;
        }
        for (HookEntry hook : hooks()) {
            Map<String, Object> context = toContext(hook.id(), player, result);
            ScriptConfig config = coreLib.configModel() == null ? ScriptConfig.defaults() : coreLib.configModel().scriptConfig();
            ScriptExecutionResult execution = coreLib.javaScriptService().invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    hook.scriptPath(),
                    hook.functionName(),
                    List.of(context),
                    context,
                    config.clampTimeoutMillis(hook.timeoutMillis()),
                    true
            ));
            if (execution == null || !execution.success()) {
                plugin.getLogger().warning("[JavaScript] Strengthen result hook '" + hook.id() + "' failed: " + (execution == null ? "no result" : execution.message()));
            }
        }
    }

    private synchronized List<HookEntry> hooks() {
        return hooks.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    private Map<String, Object> toContext(String hookId, Player player, AttemptResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hookId", hookId);
        map.put("playerUuid", player == null ? "" : player.getUniqueId().toString());
        map.put("playerName", player == null ? "" : player.getName());
        map.put("success", result.success());
        map.put("errorKey", Texts.toStringSafe(result.errorKey()));
        map.put("resultingStar", result.resultingStar());
        map.put("resultingTemper", result.resultingCrack());
        map.put("newlyReachedStars", result.newlyReachedStars());
        map.put("replacements", result.replacements());
        if (result.preview() != null) {
            map.put("recipeId", result.preview().recipe() == null ? "" : result.preview().recipe().id());
            map.put("currentStar", result.preview().currentStar());
            map.put("targetStar", result.preview().targetStar());
            map.put("successRate", result.preview().successRate());
            map.put("failureStar", result.preview().failureStar());
            map.put("failureTemper", result.preview().failureTemper());
            map.put("protectionApplied", result.preview().protectionApplied());
        }
        return map;
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), JavaScriptRegistrationType.STRENGTHEN_RESULT_HOOK, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Strengthen result hook registration failed: " + message);
        }
    }

    private EmakiCoreLibPlugin coreLib() {
        try {
            return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static String value(Map<String, ?> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        String text = Texts.toStringSafe(value);
        return Texts.isBlank(text) ? fallback : text;
    }

    private static long longValue(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return Math.max(1L, number.longValue());
        }
        try {
            return Math.max(1L, Long.parseLong(Texts.toStringSafe(raw)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String scriptPath(ScriptModuleContext context) {
        return context == null ? "" : context.scriptPath();
    }

    private static long defaultTimeout(ScriptModuleContext context) {
        return context == null ? 1_000L : context.config().engine().defaultTimeoutMillis();
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record HookEntry(String id, String functionName, String scriptPath, long timeoutMillis) {
    }
}
