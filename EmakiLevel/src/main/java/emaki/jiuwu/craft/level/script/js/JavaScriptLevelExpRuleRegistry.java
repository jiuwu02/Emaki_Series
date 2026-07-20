package emaki.jiuwu.craft.level.script.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;

public final class JavaScriptLevelExpRuleRegistry {


    private static final String REGISTRATION_TYPE = "level_exp_rule";

    private final EmakiLevelPlugin plugin;
    private final Map<String, RuleEntry> rules = new LinkedHashMap<>();

    public JavaScriptLevelExpRuleRegistry(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Level exp rule cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Level exp rule id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "modifyExp"));
        RuleEntry entry = new RuleEntry(
                id,
                intValue(definition.get("priority"), 0),
                normalizedSet(definition.containsKey("typeIds") ? definition.get("typeIds") : definition.get("types")),
                normalizedSet(definition.get("reasons")),
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), context == null ? 1_000L : context.config().engine().defaultTimeoutMillis())
        );
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

    public Adjustment apply(Adjustment base) {
        if (base == null || plugin == null || plugin.coreLib() == null) {
            return base;
        }
        JavaScriptService javaScriptService = plugin.coreLib().javaScriptService();
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return base;
        }
        Adjustment current = base;
        for (RuleEntry rule : matchingRules(base.typeId(), base.reason())) {
            current = applyRule(javaScriptService, rule, current);
        }
        logTraces(current.playerUuid(), current.traces());
        return current;
    }

    private void logTraces(String playerUuid, List<Map<String, Object>> traces) {
        if (plugin == null || plugin.debugLogger() == null || traces == null || traces.isEmpty()) {
            return;
        }
        java.util.UUID playerId = parseUuid(playerUuid);
        if (!plugin.debugLogger().shouldLog("script", playerId)) {
            return;
        }
        for (Map<String, Object> trace : traces) {
            plugin.debugLogger().logRaw("script", playerId, "script trace | rule=" + Texts.toStringSafe(trace.get("id"))
                    + " | before=" + Texts.toStringSafe(trace.get("before"))
                    + " | after=" + Texts.toStringSafe(trace.get("after"))
                    + " | msg=" + Texts.toStringSafe(trace.get("message")));
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

    private Adjustment applyRule(JavaScriptService javaScriptService, RuleEntry rule, Adjustment current) {
        Map<String, Object> context = current.toContext(rule.id());
        ScriptConfig config = plugin.coreLib().configModel() == null ? ScriptConfig.defaults() : plugin.coreLib().configModel().scriptConfig();
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
            return current.withTrace(rule.id(), current.actualAmount(), current.actualAmount(), result == null ? "no_result" : result.message());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        ConfigNodes.entries(rawMap).forEach(map::put);
        double before = current.actualAmount();
        double after = before;
        String message = Texts.toStringSafe(map.get("message"));
        if (bool(map.get("cancel"), false)) {
            after = 0D;
        } else {
            Double explicit = number(map.containsKey("actualAmount") ? map.get("actualAmount") : map.get("amount"));
            if (explicit != null) {
                after = Math.max(0D, explicit);
            } else {
                Double multiplier = number(map.get("multiplier"));
                if (multiplier != null) {
                    after = Math.max(0D, before * multiplier);
                }
            }
        }
        return current.withAmount(after).withTrace(rule.id(), before, after, message);
    }

    private synchronized List<RuleEntry> matchingRules(String typeId, String reason) {
        String normalizedType = Texts.normalizeId(typeId);
        String normalizedReason = Texts.normalizeId(reason);
        return rules.values().stream()
                .filter(rule -> rule.typeIds().isEmpty() || rule.typeIds().contains(normalizedType))
                .filter(rule -> rule.reasons().isEmpty() || rule.reasons().contains(normalizedReason))
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
            plugin.getLogger().warning("[JavaScript] Level exp rule registration failed: " + message);
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

    private static Integer intValue(Object raw, int fallback) {
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

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record RuleEntry(String id,
            int priority,
            Set<String> typeIds,
            Set<String> reasons,
            String functionName,
            String scriptPath,
            long timeoutMillis) {
    }

    public record Adjustment(String playerUuid,
            String typeId,
            String reason,
            double originalAmount,
            double multiplier,
            double multipliedAmount,
            double dailyLimit,
            double gainedToday,
            double actualAmount,
            List<Map<String, Object>> traces) {

        public Adjustment {
            traces = traces == null ? List.of() : List.copyOf(traces);
        }

        public Adjustment withAmount(double amount) {
            return new Adjustment(playerUuid, typeId, reason, originalAmount, multiplier, multipliedAmount, dailyLimit, gainedToday, amount, traces);
        }

        public Adjustment withTrace(String ruleId, double before, double after, String message) {
            List<Map<String, Object>> updated = new ArrayList<>(traces);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("id", ruleId);
            trace.put("before", before);
            trace.put("after", after);
            trace.put("message", Texts.toStringSafe(message));
            updated.add(Map.copyOf(trace));
            return new Adjustment(playerUuid, typeId, reason, originalAmount, multiplier, multipliedAmount, dailyLimit, gainedToday, actualAmount, List.copyOf(updated));
        }

        private Map<String, Object> toContext(String ruleId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", ruleId);
            map.put("playerUuid", playerUuid);
            map.put("typeId", typeId);
            map.put("reason", reason);
            map.put("originalAmount", originalAmount);
            map.put("currentAmount", actualAmount);
            map.put("multiplier", multiplier);
            map.put("multipliedAmount", multipliedAmount);
            map.put("dailyLimit", dailyLimit);
            map.put("gainedToday", gainedToday);
            return map;
        }
    }
}
