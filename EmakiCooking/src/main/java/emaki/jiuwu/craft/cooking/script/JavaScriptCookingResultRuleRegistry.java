package emaki.jiuwu.craft.cooking.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;

public final class JavaScriptCookingResultRuleRegistry {


    private static final String REGISTRATION_TYPE = "cooking_result_rule";

    private final EmakiCookingPlugin plugin;
    private final Map<String, RuleEntry> rules = new LinkedHashMap<>();

    public JavaScriptCookingResultRuleRegistry(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Cooking result rule cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Cooking result rule id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "modifyCookingResult"));
        RuleEntry entry = new RuleEntry(id,
                intValue(definition.get("priority"), 0),
                normalizedStationSet(definition.containsKey("stations") ? definition.get("stations") : definition.get("station")),
                normalizedSet(definition.containsKey("recipeIds") ? definition.get("recipeIds") : definition.get("recipes")),
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), defaultTimeout(context)));
        long started = System.nanoTime();
        rules.put(id, entry);
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                REGISTRATION_TYPE,
                id,
                elapsedMillis(started),
                () -> unregister(id),
                Map.of("function", function, "priority", entry.priority()))) {
            rules.remove(id);
            return false;
        }
        return true;
    }

    public synchronized void unregister(String id) {
        rules.remove(Texts.normalizeId(id));
    }

    public synchronized void clear() {
        rules.clear();
    }

    public synchronized List<String> ids() {
        return rules.keySet().stream().sorted().toList();
    }

    public DeliveryPlan apply(DeliveryPlan base) {
        if (base == null || plugin == null) {
            return base;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return base;
        }
        DeliveryPlan current = base;
        for (RuleEntry rule : matchingRules(base.recipeId(), base.stationType())) {
            current = applyRule(coreLib, coreLib.javaScriptService(), rule, current);
            if (current.cancelled()) {
                break;
            }
        }
        logTraces(current.playerUuid(), current.traces());
        return current;
    }

    private void logTraces(String playerUuid, List<Map<String, Object>> traces) {
        if (plugin == null || traces == null || traces.isEmpty()) {
            return;
        }
        java.util.UUID playerId = parseUuid(playerUuid);
        if (plugin.debugLogger() == null || !plugin.debugLogger().shouldLog("script", playerId)) {
            return;
        }
        for (Map<String, Object> trace : traces) {
            plugin.debugLogger().log("script", playerId, "script.trace", Map.of(
                    "rule", Texts.toStringSafe(trace.get("id")),
                    "message", Texts.toStringSafe(trace.get("message"))
            ));
        }
    }

    private static java.util.UUID parseUuid(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private DeliveryPlan applyRule(EmakiCoreLibPlugin coreLib, JavaScriptService javaScriptService, RuleEntry rule, DeliveryPlan current) {
        Map<String, Object> context = current.toContext(rule.id());
        ScriptConfig config = coreLib.configModel() == null ? ScriptConfig.defaults() : coreLib.configModel().scriptConfig();
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                rule.scriptPath(),
                rule.functionName(),
                List.of(context),
                context,
                config.clampTimeoutMillis(rule.timeoutMillis()),
                true
        ));
        if (result == null || !result.success() || !(result.returnValue() instanceof Map<?, ?> rawMap)) {
            return current.withTrace(rule.id(), result == null ? "no_result" : result.message());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        ConfigNodes.entries(rawMap).forEach(map::put);
        List<Map<String, Object>> outputs = current.outputs();
        if (map.get("outputs") instanceof Iterable<?> replacementOutputs) {
            outputs = normalizeOutputs(replacementOutputs);
        }
        if (map.get("extraResults") instanceof Iterable<?> extraResults) {
            List<Map<String, Object>> combined = new ArrayList<>(outputs);
            combined.addAll(normalizeOutputs(extraResults));
            outputs = List.copyOf(combined);
        }
        List<String> actions = current.actions();
        if (map.get("actions") instanceof Iterable<?> replacementActions) {
            actions = strings(replacementActions);
        }
        if (map.get("extraActions") instanceof Iterable<?> extraActions) {
            List<String> combined = new ArrayList<>(actions);
            combined.addAll(strings(extraActions));
            actions = List.copyOf(combined);
        }
        boolean cancelled = bool(map.get("cancel"), current.cancelled());
        return current.withValues(outputs, actions, cancelled).withTrace(rule.id(), Texts.toStringSafe(map.get("message")));
    }

    private synchronized List<RuleEntry> matchingRules(String recipeId, String stationType) {
        String normalizedRecipe = Texts.normalizeId(recipeId);
        String normalizedStation = Texts.normalizeId(stationType);
        return rules.values().stream()
                .filter(rule -> rule.recipeIds().isEmpty() || rule.recipeIds().contains(normalizedRecipe))
                .filter(rule -> rule.stations().isEmpty() || rule.stations().contains(normalizedStation))
                .sorted((left, right) -> {
                    int priority = Integer.compare(left.priority(), right.priority());
                    return priority != 0 ? priority : left.id().compareTo(right.id());
                })
                .toList();
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), REGISTRATION_TYPE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Cooking result rule registration failed: " + message);
        }
    }

    private EmakiCoreLibPlugin coreLib() {
        try {
            return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static List<Map<String, Object>> normalizeOutputs(Iterable<?> rawOutputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : rawOutputs) {
            if (raw instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>();
                ConfigNodes.entries(rawMap).forEach(map::put);
                if (!map.isEmpty()) {
                    result.add(Map.copyOf(map));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<String> strings(Iterable<?> rawActions) {
        List<String> result = new ArrayList<>();
        for (Object raw : rawActions) {
            String text = Texts.toStringSafe(raw);
            if (Texts.isNotBlank(text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
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

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Texts.toStringSafe(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

    private static boolean bool(Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(raw));
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

    private record RuleEntry(String id, int priority, Set<String> stations, Set<String> recipeIds, String functionName, String scriptPath, long timeoutMillis) {
    }

    public record DeliveryPlan(String recipeId,
            String recipeName,
            String stationType,
            String playerUuid,
            String playerName,
            String phase,
            String world,
            int x,
            int y,
            int z,
            List<CookingInputIngredient> inputs,
            List<Map<String, Object>> outputs,
            List<String> actions,
            Map<String, ?> placeholders,
            boolean cancelled,
            List<Map<String, Object>> traces) {

        public DeliveryPlan {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            outputs = outputs == null ? List.of() : castMapList(ScriptSnapshots.immutableValue(outputs));
            actions = actions == null ? List.of() : List.copyOf(actions);
            placeholders = placeholders == null ? Map.of() : ScriptSnapshots.immutableMap(placeholders);
            traces = traces == null ? List.of() : castMapList(ScriptSnapshots.immutableValue(traces));
        }

        public static DeliveryPlan from(RecipeDocument recipe, Player player, Location location, String phase, List<CookingInputIngredient> inputs, List<Map<String, Object>> outputs, List<String> actions, Map<String, ?> placeholders) {
            return new DeliveryPlan(
                    recipe == null ? "" : recipe.id(),
                    recipe == null ? "" : recipe.displayName(),
                    recipe == null ? "" : recipe.stationType().folderName(),
                    player == null ? "" : player.getUniqueId().toString(),
                    player == null ? "" : player.getName(),
                    Texts.toStringSafe(phase),
                    location == null || location.getWorld() == null ? "" : location.getWorld().getName(),
                    location == null ? 0 : location.getBlockX(),
                    location == null ? 0 : location.getBlockY(),
                    location == null ? 0 : location.getBlockZ(),
                    inputs,
                    outputs,
                    actions,
                    placeholders,
                    false,
                    List.of()
            );
        }

        DeliveryPlan withValues(List<Map<String, Object>> outputs, List<String> actions, boolean cancelled) {
            return new DeliveryPlan(recipeId, recipeName, stationType, playerUuid, playerName, phase, world, x, y, z, inputs, outputs, actions, placeholders, cancelled, traces);
        }

        DeliveryPlan withTrace(String ruleId, String message) {
            List<Map<String, Object>> updated = new ArrayList<>(traces);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("id", ruleId);
            trace.put("message", Texts.toStringSafe(message));
            updated.add(Map.copyOf(trace));
            return new DeliveryPlan(recipeId, recipeName, stationType, playerUuid, playerName, phase, world, x, y, z, inputs, outputs, actions, placeholders, cancelled, List.copyOf(updated));
        }

        Map<String, Object> toContext(String ruleId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", ruleId);
            map.put("recipeId", recipeId);
            map.put("recipeName", recipeName);
            map.put("stationType", stationType);
            map.put("playerUuid", playerUuid);
            map.put("playerName", playerName);
            map.put("phase", phase);
            map.put("world", world);
            map.put("x", x);
            map.put("y", y);
            map.put("z", z);
            map.put("inputs", inputs.stream().map(CookingInputIngredient::toMap).toList());
            map.put("outputs", outputs);
            map.put("actions", actions);
            map.put("placeholders", placeholders);
            map.put("traces", traces);
            return ScriptSnapshots.immutableMap(map);
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> castMapList(Object value) {
            return value instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
        }
    }
}
