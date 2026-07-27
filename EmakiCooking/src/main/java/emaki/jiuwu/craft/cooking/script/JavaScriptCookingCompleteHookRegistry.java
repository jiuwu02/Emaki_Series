package emaki.jiuwu.craft.cooking.script;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;

public final class JavaScriptCookingCompleteHookRegistry {


    private static final String REGISTRATION_TYPE = "cooking_complete_hook";

    private final EmakiCookingPlugin plugin;
    private final Map<String, HookEntry> hooks = new LinkedHashMap<>();

    public JavaScriptCookingCompleteHookRegistry(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Cooking complete hook cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Cooking complete hook id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "onCookingComplete"));
        HookEntry entry = new HookEntry(id,
                normalizedStationSet(definition.containsKey("stations") ? definition.get("stations") : definition.get("station")),
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









    public List<String> prepareActions(JavaScriptCookingResultRuleRegistry.DeliveryPlan plan) {
        if (plan == null || plugin == null) {
            return List.of();
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return List.of();
        }
        List<String> frozenActions = new java.util.ArrayList<>();
        for (HookEntry hook : matchingHooks(plan.recipeId(), plan.stationType())) {
            Map<String, Object> context = ScriptSnapshots.immutableMap(plan.toContext(hook.id()));
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
                plugin.getLogger().warning("[JavaScript] Cooking complete hook '" + hook.id() + "' failed: " + (result == null ? "no result" : result.message()));
                continue;
            }
            frozenActions.addAll(returnedActions(result.returnValue()));
        }
        return frozenActions.isEmpty() ? List.of() : List.copyOf(frozenActions);
    }

    public void fire(JavaScriptCookingResultRuleRegistry.DeliveryPlan plan) {
        if (plan == null || plugin == null) {
            return;
        }
        List<String> actions = prepareActions(plan);
        if (actions.isEmpty()) {
            return;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null) {
            return;
        }
        Player player = resolvePlayer(plan.playerUuid());
        ActionExecutor actionExecutor = coreLib.actionExecutor();
        if (actionExecutor == null) {
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("cooking_recipe_id", Texts.toStringSafe(plan.recipeId()));
        placeholders.put("cooking_station_type", Texts.toStringSafe(plan.stationType()));
        ActionContext context = ActionContext.create(plugin, player, "cooking.complete", false)
                .withPlaceholders(placeholders);
        actionExecutor.executeAll(context, actions, true).whenComplete((batch, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("[JavaScript] Cooking complete hook actions failed: " + throwable.getMessage());
            }
        });
    }

    private List<String> returnedActions(Object returnValue) {
        if (!(returnValue instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<String> actions = Texts.asStringList(map.get("actions"));
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    private static Player resolvePlayer(String playerUuid) {
        if (Texts.isBlank(playerUuid)) {
            return null;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private synchronized List<HookEntry> matchingHooks(String recipeId, String stationType) {
        String normalizedRecipe = Texts.normalizeId(recipeId);
        String normalizedStation = Texts.normalizeId(stationType);
        return hooks.values().stream()
                .filter(hook -> hook.recipeIds().isEmpty() || hook.recipeIds().contains(normalizedRecipe))
                .filter(hook -> hook.stations().isEmpty() || hook.stations().contains(normalizedStation))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), REGISTRATION_TYPE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Cooking complete hook registration failed: " + message);
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


    private static final Map<String, String> STATION_ALIASES = Map.of(
            "fermenter", "fermentation_barrel",
            "fermentation", "fermentation_barrel");

    private static Set<String> normalizedStationSet(Object raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String station : normalizedSet(raw)) {
            result.add(STATION_ALIASES.getOrDefault(station, station));
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

    private record HookEntry(String id, Set<String> stations, Set<String> recipeIds, String functionName, String scriptPath, long timeoutMillis) {
    }
}
