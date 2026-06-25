package emaki.jiuwu.craft.forge.script.js;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class JavaScriptForgeResultHookRegistry {

    /** JavaScript registration type id for forge result hooks (CoreLib tracks this as a free-form string). */
    private static final String REGISTRATION_TYPE = "forge_result_hook";

    private final EmakiForgePlugin plugin;
    private final Map<String, HookEntry> hooks = new LinkedHashMap<>();

    public JavaScriptForgeResultHookRegistry(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Forge result hook cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Forge result hook id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "onForgeResult"));
        HookEntry entry = new HookEntry(id,
                normalizedSet(definition.containsKey("recipeIds") ? definition.get("recipeIds") : definition.get("recipes")),
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), defaultTimeout(context)));
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

    public void fire(Player player, Recipe recipe, ForgeResult forgeResult) {
        if (forgeResult == null || plugin == null) {
            return;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return;
        }
        for (HookEntry hook : matchingHooks(recipe == null ? "" : recipe.id())) {
            Map<String, Object> context = toContext(hook.id(), player, recipe, forgeResult);
            ScriptConfig config = coreLib.configModel() == null ? ScriptConfig.defaults() : coreLib.configModel().scriptConfig();
            ScriptExecutionResult result = coreLib.javaScriptService().invoke(new ScriptInvocationRequest(
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
                plugin.getLogger().warning("[JavaScript] Forge result hook '" + hook.id() + "' failed: " + (result == null ? "no result" : result.message()));
                continue;
            }
            executeReturnedActions(coreLib, player, result.returnValue(), recipe, forgeResult);
        }
    }

    private void executeReturnedActions(EmakiCoreLibPlugin coreLib, Player player, Object returnValue, Recipe recipe, ForgeResult forgeResult) {
        if (player == null || !(returnValue instanceof Map<?, ?> map)) {
            return;
        }
        List<String> actions = Texts.asStringList(map.get("actions"));
        if (actions.isEmpty()) {
            return;
        }
        ActionExecutor actionExecutor = coreLib.actionExecutor();
        if (actionExecutor == null) {
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("success", Boolean.toString(forgeResult.success()));
        placeholders.put("forge_recipe_id", recipe == null ? "" : recipe.id());
        placeholders.put("forge_quality", Texts.toStringSafe(forgeResult.quality()));
        placeholders.put("forge_multiplier", Double.toString(forgeResult.multiplier()));
        ActionContext context = ActionContext.create(plugin, player, "forge.result", false)
                .withPlaceholders(placeholders);
        actionExecutor.executeAll(context, actions, true).whenComplete((batch, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("[JavaScript] Forge result hook actions failed: " + throwable.getMessage());
            }
        });
    }

    private synchronized List<HookEntry> matchingHooks(String recipeId) {
        String normalizedRecipe = Texts.normalizeId(recipeId);
        return hooks.values().stream()
                .filter(hook -> hook.recipeIds().isEmpty() || hook.recipeIds().contains(normalizedRecipe))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    private Map<String, Object> toContext(String hookId, Player player, Recipe recipe, ForgeResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hookId", hookId);
        map.put("playerUuid", player == null ? "" : player.getUniqueId().toString());
        map.put("playerName", player == null ? "" : player.getName());
        map.put("recipeId", recipe == null ? "" : recipe.id());
        map.put("recipeName", recipe == null ? "" : recipe.displayName());
        map.put("success", result.success());
        map.put("errorKey", Texts.toStringSafe(result.errorKey()));
        map.put("quality", Texts.toStringSafe(result.quality()));
        map.put("multiplier", result.multiplier());
        map.put("resultItem", ScriptServiceApiSupport.itemSummary(result.resultItem()));
        map.put("actionFailureReason", Texts.toStringSafe(result.actionFailureReason()));
        map.put("replacements", result.replacements());
        return map;
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), REGISTRATION_TYPE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Forge result hook registration failed: " + message);
        }
    }

    private EmakiCoreLibPlugin coreLib() {
        try {
            return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        } catch (IllegalStateException exception) {
            return null;
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

    private static long defaultTimeout(ScriptModuleContext context) {
        return context == null ? 1_000L : context.config().engine().defaultTimeoutMillis();
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record HookEntry(String id, Set<String> recipeIds, String functionName, String scriptPath, long timeoutMillis) {
    }
}
