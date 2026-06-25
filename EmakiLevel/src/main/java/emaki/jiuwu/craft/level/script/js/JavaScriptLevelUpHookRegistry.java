package emaki.jiuwu.craft.level.script.js;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;

public final class JavaScriptLevelUpHookRegistry {

    /** JavaScript registration type id for level up hooks (CoreLib tracks this as a free-form string). */
    private static final String REGISTRATION_TYPE = "level_up_hook";

    private final EmakiLevelPlugin plugin;
    private final Map<String, HookEntry> hooks = new LinkedHashMap<>();

    public JavaScriptLevelUpHookRegistry(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Level up hook cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Level up hook id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "onLevelUp"));
        Set<String> typeIds = normalizedSet(definition.containsKey("typeIds") ? definition.get("typeIds") : definition.get("type"));
        HookEntry entry = new HookEntry(id,
                typeIds,
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), context == null ? 1_000L : context.config().engine().defaultTimeoutMillis()));
        long started = System.nanoTime();
        hooks.put(id, entry);
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                REGISTRATION_TYPE,
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

    public void fire(LevelUpEvent event) {
        if (event == null || plugin == null || plugin.coreLib() == null) {
            return;
        }
        JavaScriptService javaScriptService = plugin.coreLib().javaScriptService();
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return;
        }
        for (HookEntry hook : matchingHooks(event.typeId())) {
            Map<String, Object> context = event.toMap(hook.id());
            ScriptConfig config = plugin.coreLib().configModel() == null ? ScriptConfig.defaults() : plugin.coreLib().configModel().scriptConfig();
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    hook.scriptPath(),
                    hook.functionName(),
                    List.of(context),
                    context,
                    config.clampTimeoutMillis(hook.timeoutMillis()),
                    true
            ));
            if (result == null || !result.success()) {
                plugin.getLogger().warning("[JavaScript] Level up hook '" + hook.id() + "' failed: " + (result == null ? "no result" : result.message()));
            }
        }
    }

    private synchronized List<HookEntry> matchingHooks(String typeId) {
        String normalizedType = Texts.normalizeId(typeId);
        return hooks.values().stream()
                .filter(hook -> hook.typeIds().isEmpty() || hook.typeIds().contains(normalizedType))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), REGISTRATION_TYPE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Level up hook registration failed: " + message);
        }
    }

    private static Set<String> normalizedSet(Object raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : Texts.asStringList(raw)) {
            String normalized = Texts.normalizeId(value);
            if (Texts.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return Set.copyOf(result);
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

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record HookEntry(String id, Set<String> typeIds, String functionName, String scriptPath, long timeoutMillis) {
    }

    public record LevelUpEvent(String playerUuid,
            String playerName,
            String typeId,
            int oldLevel,
            int newLevel,
            double oldExp,
            double newExp,
            String cause,
            double requiredExp) {

        private Map<String, Object> toMap(String hookId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("hookId", hookId);
            map.put("playerUuid", playerUuid);
            map.put("playerName", playerName);
            map.put("typeId", typeId);
            map.put("oldLevel", oldLevel);
            map.put("newLevel", newLevel);
            map.put("oldExp", oldExp);
            map.put("newExp", newExp);
            map.put("cause", cause);
            map.put("requiredExp", requiredExp);
            return map;
        }
    }
}
