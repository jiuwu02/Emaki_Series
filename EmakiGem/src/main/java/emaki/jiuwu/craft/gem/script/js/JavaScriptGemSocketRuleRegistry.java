package emaki.jiuwu.craft.gem.script.js;

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
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

public final class JavaScriptGemSocketRuleRegistry {

    private final EmakiGemPlugin plugin;
    private final Map<String, RuleEntry> rules = new LinkedHashMap<>();

    public JavaScriptGemSocketRuleRegistry(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Gem socket rule cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Gem socket rule id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "checkSocket"));
        RuleEntry entry = new RuleEntry(id,
                intValue(definition.get("priority"), 0),
                normalizedSet(definition.containsKey("gemIds") ? definition.get("gemIds") : definition.get("gems")),
                normalizedSet(definition.containsKey("socketTypes") ? definition.get("socketTypes") : definition.get("sockets")),
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), defaultTimeout(context)));
        long started = System.nanoTime();
        rules.put(id, entry);
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                JavaScriptRegistrationType.GEM_SOCKET_RULE,
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

    public Decision apply(Decision base) {
        if (base == null || plugin == null) {
            return base;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return base;
        }
        Decision current = base;
        for (RuleEntry rule : matchingRules(base.gemId(), base.socketType())) {
            current = applyRule(coreLib, coreLib.javaScriptService(), rule, current);
            if (!current.allowed()) {
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
        boolean allowed = !bool(map.get("cancel"), false) && bool(map.get("allowed"), current.allowed());
        double before = current.successRate();
        double after = before;
        Double explicit = number(map.get("successRate"));
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
        String messageKey = value(map, "messageKey", current.messageKey());
        String message = Texts.toStringSafe(map.get("message"));
        return current.withValues(allowed, Numbers.clamp(after, 0D, 100D), messageKey, message)
                .withTrace(rule.id(), before, Numbers.clamp(after, 0D, 100D), message);
    }

    private synchronized List<RuleEntry> matchingRules(String gemId, String socketType) {
        String normalizedGem = Texts.normalizeId(gemId);
        String normalizedSocket = Texts.normalizeId(socketType);
        return rules.values().stream()
                .filter(rule -> rule.gemIds().isEmpty() || rule.gemIds().contains(normalizedGem))
                .filter(rule -> rule.socketTypes().isEmpty() || rule.socketTypes().contains(normalizedSocket))
                .sorted((left, right) -> {
                    int priority = Integer.compare(left.priority(), right.priority());
                    return priority != 0 ? priority : left.id().compareTo(right.id());
                })
                .toList();
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), JavaScriptRegistrationType.GEM_SOCKET_RULE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Gem socket rule registration failed: " + message);
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

    private record RuleEntry(String id, int priority, Set<String> gemIds, Set<String> socketTypes, String functionName, String scriptPath, long timeoutMillis) {
    }

    public record Decision(String playerUuid,
            String playerName,
            String itemId,
            int slotIndex,
            String socketType,
            String gemId,
            String gemType,
            int gemLevel,
            boolean allowed,
            double originalSuccessRate,
            double successRate,
            String messageKey,
            String message,
            List<Map<String, Object>> inlaidGems,
            List<Map<String, Object>> traces) {

        public Decision {
            inlaidGems = inlaidGems == null ? List.of() : List.copyOf(inlaidGems);
            traces = traces == null ? List.of() : List.copyOf(traces);
        }

        public static Decision from(Player player,
                GemItemDefinition itemDefinition,
                GemItemDefinition.SocketSlot slot,
                int slotIndex,
                GemDefinition gemDefinition,
                GemItemInstance instance,
                double successRate) {
            return from(player, itemDefinition, slot, slotIndex, gemDefinition, instance, successRate, List.of());
        }

        public static Decision from(Player player,
                GemItemDefinition itemDefinition,
                GemItemDefinition.SocketSlot slot,
                int slotIndex,
                GemDefinition gemDefinition,
                GemItemInstance instance,
                double successRate,
                List<Map<String, Object>> inlaidGems) {
            return new Decision(
                    player == null ? "" : player.getUniqueId().toString(),
                    player == null ? "" : player.getName(),
                    itemDefinition == null ? "" : itemDefinition.id(),
                    slotIndex,
                    slot == null ? "" : slot.type(),
                    gemDefinition == null ? "" : gemDefinition.id(),
                    gemDefinition == null ? "" : gemDefinition.gemType(),
                    instance == null ? 1 : instance.level(),
                    true,
                    successRate,
                    successRate,
                    "gem.error.condition_not_met",
                    "",
                    inlaidGems,
                    List.of()
            );
        }

        Decision withValues(boolean allowed, double successRate, String messageKey, String message) {
            return new Decision(playerUuid, playerName, itemId, slotIndex, socketType, gemId, gemType, gemLevel,
                    allowed, originalSuccessRate, successRate, Texts.toStringSafe(messageKey), Texts.toStringSafe(message),
                    inlaidGems, traces);
        }

        Decision withTrace(String ruleId, double before, double after, String message) {
            List<Map<String, Object>> updated = new ArrayList<>(traces);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("id", ruleId);
            trace.put("before", before);
            trace.put("after", after);
            trace.put("message", Texts.toStringSafe(message));
            updated.add(Map.copyOf(trace));
            return new Decision(playerUuid, playerName, itemId, slotIndex, socketType, gemId, gemType, gemLevel,
                    allowed, originalSuccessRate, successRate, messageKey, this.message, inlaidGems, List.copyOf(updated));
        }

        Map<String, Object> toContext(String ruleId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", ruleId);
            map.put("playerUuid", playerUuid);
            map.put("playerName", playerName);
            map.put("itemId", itemId);
            map.put("slotIndex", slotIndex);
            map.put("socketType", socketType);
            map.put("gemId", gemId);
            map.put("gemType", gemType);
            map.put("gemLevel", gemLevel);
            map.put("allowed", allowed);
            map.put("originalSuccessRate", originalSuccessRate);
            map.put("successRate", successRate);
            map.put("messageKey", messageKey);
            map.put("message", message);
            map.put("inlaidGems", inlaidGems);
            map.put("inlaidGemCount", inlaidGems.size());
            map.put("traces", traces);
            return map;
        }
    }
}
