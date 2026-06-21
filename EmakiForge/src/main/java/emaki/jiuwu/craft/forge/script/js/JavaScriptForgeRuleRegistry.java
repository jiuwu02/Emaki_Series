package emaki.jiuwu.craft.forge.script.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationType;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class JavaScriptForgeRuleRegistry {

    private final EmakiForgePlugin plugin;
    private final Map<String, RuleEntry> rules = new LinkedHashMap<>();

    public JavaScriptForgeRuleRegistry(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Forge rule cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Forge rule id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "modifyForge"));
        RuleEntry entry = new RuleEntry(id,
                intValue(definition.get("priority"), 0),
                normalizedSet(definition.containsKey("recipeIds") ? definition.get("recipeIds") : definition.get("recipes")),
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), defaultTimeout(context)));
        long started = System.nanoTime();
        rules.put(id, entry);
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                JavaScriptRegistrationType.FORGE_RULE,
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

    public Decision apply(Player player, Recipe recipe, double successRate) {
        Decision current = Decision.from(player, recipe, successRate);
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return current;
        }
        for (RuleEntry rule : matchingRules(current.recipeId())) {
            current = applyRule(coreLib, coreLib.javaScriptService(), rule, current);
            if (current.cancelled()) {
                break;
            }
        }
        return current;
    }

    private Decision applyRule(EmakiCoreLibPlugin coreLib, JavaScriptService javaScriptService, RuleEntry rule, Decision current) {
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
            return current.withTrace(rule.id(), current.successRate(), current.successRate(), result == null ? "no_result" : result.message());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        ConfigNodes.entries(rawMap).forEach(map::put);
        double before = current.successRate();
        double after = before;
        Double explicit = number(map.containsKey("successRate") ? map.get("successRate") : map.get("chance"));
        if (explicit != null) {
            after = explicit;
        } else {
            Double bonus = number(map.get("successBonus"));
            if (bonus != null) {
                after += bonus;
            }
            Double multiplier = number(map.get("successMultiplier"));
            if (multiplier != null) {
                after *= multiplier;
            }
        }
        boolean cancelled = bool(map.get("cancel"), current.cancelled());
        String message = Texts.toStringSafe(map.get("message"));
        return current.withValues(Numbers.clamp(after, 0D, 100D), cancelled, message)
                .withTrace(rule.id(), before, Numbers.clamp(after, 0D, 100D), message);
    }

    private synchronized List<RuleEntry> matchingRules(String recipeId) {
        String normalizedRecipe = Texts.normalizeId(recipeId);
        return rules.values().stream()
                .filter(rule -> rule.recipeIds().isEmpty() || rule.recipeIds().contains(normalizedRecipe))
                .sorted((left, right) -> {
                    int priority = Integer.compare(left.priority(), right.priority());
                    return priority != 0 ? priority : left.id().compareTo(right.id());
                })
                .toList();
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), JavaScriptRegistrationType.FORGE_RULE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Forge rule registration failed: " + message);
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

    private static Double number(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(raw));
        } catch (NumberFormatException exception) {
            return null;
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

    private record RuleEntry(String id, int priority, Set<String> recipeIds, String functionName, String scriptPath, long timeoutMillis) {
    }

    public record Decision(String recipeId,
            String recipeName,
            String playerUuid,
            String playerName,
            double originalSuccessRate,
            double successRate,
            boolean cancelled,
            String message,
            List<Map<String, Object>> traces) {

        public Decision {
            traces = traces == null ? List.of() : List.copyOf(traces);
        }

        static Decision from(Player player, Recipe recipe, double successRate) {
            return new Decision(
                    recipe == null ? "" : recipe.id(),
                    recipe == null ? "" : recipe.displayName(),
                    player == null ? "" : player.getUniqueId().toString(),
                    player == null ? "" : player.getName(),
                    successRate,
                    successRate,
                    false,
                    "",
                    List.of()
            );
        }

        Decision withValues(double successRate, boolean cancelled, String message) {
            return new Decision(recipeId, recipeName, playerUuid, playerName, originalSuccessRate, successRate, cancelled, Texts.toStringSafe(message), traces);
        }

        Decision withTrace(String ruleId, double before, double after, String message) {
            List<Map<String, Object>> updated = new ArrayList<>(traces);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("id", ruleId);
            trace.put("before", before);
            trace.put("after", after);
            trace.put("message", Texts.toStringSafe(message));
            updated.add(Map.copyOf(trace));
            return new Decision(recipeId, recipeName, playerUuid, playerName, originalSuccessRate, successRate, cancelled, this.message, List.copyOf(updated));
        }

        Map<String, Object> toContext(String ruleId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", ruleId);
            map.put("recipeId", recipeId);
            map.put("recipeName", recipeName);
            map.put("playerUuid", playerUuid);
            map.put("playerName", playerName);
            map.put("originalSuccessRate", originalSuccessRate);
            map.put("successRate", successRate);
            map.put("cancelled", cancelled);
            map.put("message", message);
            map.put("traces", traces);
            return map;
        }
    }
}
