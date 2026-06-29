package emaki.jiuwu.craft.strengthen.script.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;

public final class JavaScriptStrengthenChanceRuleRegistry {

    /** JavaScript registration type id for strengthen chance rules (CoreLib tracks this as a free-form string). */
    private static final String REGISTRATION_TYPE = "strengthen_chance_rule";

    private final EmakiStrengthenPlugin plugin;
    private final Map<String, RuleEntry> rules = new LinkedHashMap<>();

    public JavaScriptStrengthenChanceRuleRegistry(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Strengthen chance rule cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Strengthen chance rule id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "modifyChance"));
        RuleEntry entry = new RuleEntry(
                id,
                intValue(definition.get("priority"), 0),
                function,
                scriptPath(context),
                longValue(definition.get("timeoutMillis"), defaultTimeout(context))
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

    public AttemptPreview apply(Player player, AttemptPreview preview) {
        return apply(player, null, null, null, preview);
    }

    public AttemptPreview apply(Player player,
            AttemptContext attemptContext,
            List<AttemptMaterial> requiredMaterials,
            List<AttemptMaterial> optionalMaterials,
            AttemptPreview preview) {
        if (preview == null || !preview.eligible() || plugin == null) {
            return preview;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null) {
            return preview;
        }
        JavaScriptService javaScriptService = coreLib.javaScriptService();
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return preview;
        }
        ItemStack targetItem = attemptContext == null ? null : attemptContext.targetItem();
        Adjustment current = Adjustment.from(player, targetItem, requiredMaterials, optionalMaterials, preview);
        for (RuleEntry rule : sortedRules()) {
            current = applyRule(coreLib, javaScriptService, rule, current);
        }
        logTraces(player, current.traces());
        return current.toPreview(preview);
    }

    private void logTraces(Player player, List<Map<String, Object>> traces) {
        if (plugin == null || traces == null || traces.isEmpty()) {
            return;
        }
        java.util.UUID playerId = player == null ? null : player.getUniqueId();
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

    private Adjustment applyRule(EmakiCoreLibPlugin coreLib, JavaScriptService javaScriptService, RuleEntry rule, Adjustment current) {
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
        int failureStar = intValue(map.get("failureStar"), current.failureStar());
        int failureTemper = intValue(map.get("failureTemper"), current.failureTemper());
        boolean protectionApplied = bool(map.get("protectionApplied"), current.protectionApplied());
        // 失败保护 / 降级保护是面向脚本的语义别名：命中任意一个都视为施加保护，
        // 并把失败结果星级钉在当前星级，避免脚本作者直接操作内部 failureStar 数值。
        boolean failureProtected = bool(map.get("failureProtected"), false);
        boolean downgradeProtected = bool(map.get("downgradeProtected"), false);
        if (failureProtected || downgradeProtected) {
            protectionApplied = true;
            failureStar = Math.max(failureStar, current.currentStar());
            failureTemper = 0;
        }
        List<Map<String, Object>> extraCosts = costList(map.get("extraCosts"));
        Adjustment next = current.withValues(Numbers.clamp(after, 0D, 100D), failureStar, failureTemper, protectionApplied);
        if (!extraCosts.isEmpty()) {
            next = next.withExtraCosts(extraCosts);
        }
        return next.withTrace(rule.id(), before, Numbers.clamp(after, 0D, 100D), Texts.toStringSafe(map.get("message")));
    }

    private synchronized List<RuleEntry> sortedRules() {
        return rules.values().stream()
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
            plugin.getLogger().warning("[JavaScript] Strengthen chance rule registration failed: " + message);
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> costList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?>) {
                Map<String, Object> cost = new LinkedHashMap<>(ConfigNodes.entries(entry));
                if (!cost.isEmpty()) {
                    result.add(Map.copyOf(cost));
                }
            }
        }
        return List.copyOf(result);
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

    private record RuleEntry(String id, int priority, String functionName, String scriptPath, long timeoutMillis) {
    }

    private record Adjustment(String playerUuid,
            String playerName,
            String recipeId,
            int currentStar,
            int targetStar,
            int temperLevel,
            double originalSuccessRate,
            double successRate,
            int failureStar,
            int failureTemper,
            boolean protectionApplied,
            Map<String, Object> targetItem,
            List<Map<String, Object>> requiredMaterials,
            List<Map<String, Object>> optionalMaterials,
            List<Map<String, Object>> extraCosts,
            List<Map<String, Object>> traces) {

        private Adjustment {
            targetItem = targetItem == null ? Map.of() : Map.copyOf(targetItem);
            requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
            optionalMaterials = optionalMaterials == null ? List.of() : List.copyOf(optionalMaterials);
            extraCosts = extraCosts == null ? List.of() : List.copyOf(extraCosts);
            traces = traces == null ? List.of() : List.copyOf(traces);
        }

        static Adjustment from(Player player,
                ItemStack targetItem,
                List<AttemptMaterial> requiredMaterials,
                List<AttemptMaterial> optionalMaterials,
                AttemptPreview preview) {
            return new Adjustment(
                    player == null ? "" : player.getUniqueId().toString(),
                    player == null ? "" : player.getName(),
                    preview.recipe() == null ? "" : preview.recipe().id(),
                    preview.currentStar(),
                    preview.targetStar(),
                    preview.state() == null ? 0 : preview.state().temperLevel(),
                    preview.successRate(),
                    preview.successRate(),
                    preview.failureStar(),
                    preview.failureTemper(),
                    preview.protectionApplied(),
                    ScriptServiceApiSupport.itemSummary(targetItem),
                    materialSummaries(requiredMaterials),
                    materialSummaries(optionalMaterials),
                    List.of(),
                    List.of()
            );
        }

        Adjustment withValues(double successRate, int failureStar, int failureTemper, boolean protectionApplied) {
            return new Adjustment(playerUuid, playerName, recipeId, currentStar, targetStar, temperLevel, originalSuccessRate,
                    successRate, failureStar, failureTemper, protectionApplied, targetItem, requiredMaterials, optionalMaterials,
                    extraCosts, traces);
        }

        Adjustment withExtraCosts(List<Map<String, Object>> additionalCosts) {
            if (additionalCosts == null || additionalCosts.isEmpty()) {
                return this;
            }
            List<Map<String, Object>> merged = new ArrayList<>(extraCosts);
            merged.addAll(additionalCosts);
            return new Adjustment(playerUuid, playerName, recipeId, currentStar, targetStar, temperLevel, originalSuccessRate,
                    successRate, failureStar, failureTemper, protectionApplied, targetItem, requiredMaterials, optionalMaterials,
                    List.copyOf(merged), traces);
        }

        Adjustment withTrace(String ruleId, double before, double after, String message) {
            List<Map<String, Object>> updated = new ArrayList<>(traces);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("id", ruleId);
            trace.put("before", before);
            trace.put("after", after);
            trace.put("message", Texts.toStringSafe(message));
            updated.add(Map.copyOf(trace));
            return new Adjustment(playerUuid, playerName, recipeId, currentStar, targetStar, temperLevel, originalSuccessRate,
                    successRate, failureStar, failureTemper, protectionApplied, targetItem, requiredMaterials, optionalMaterials,
                    extraCosts, List.copyOf(updated));
        }

        private static List<Map<String, Object>> materialSummaries(List<AttemptMaterial> materials) {
            if (materials == null || materials.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>(materials.size());
            for (AttemptMaterial material : materials) {
                if (material == null || Texts.isBlank(material.item())) {
                    continue;
                }
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("item", material.item());
                summary.put("requiredAmount", material.requiredAmount());
                summary.put("availableAmount", material.availableAmount());
                summary.put("consumedAmount", material.consumedAmount());
                summary.put("optional", material.optional());
                summary.put("protection", material.protection());
                summary.put("temperBoost", material.temperBoost());
                result.add(Map.copyOf(summary));
            }
            return List.copyOf(result);
        }

        Map<String, Object> toContext(String ruleId) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", ruleId);
            map.put("playerUuid", playerUuid);
            map.put("playerName", playerName);
            map.put("recipeId", recipeId);
            map.put("currentStar", currentStar);
            map.put("targetStar", targetStar);
            map.put("temperLevel", temperLevel);
            map.put("originalSuccessRate", originalSuccessRate);
            map.put("successRate", successRate);
            map.put("failureStar", failureStar);
            map.put("failureTemper", failureTemper);
            map.put("protectionApplied", protectionApplied);
            map.put("targetItem", targetItem);
            map.put("requiredMaterials", requiredMaterials);
            map.put("optionalMaterials", optionalMaterials);
            map.put("extraCosts", extraCosts);
            map.put("traces", traces);
            return map;
        }

        AttemptPreview toPreview(AttemptPreview preview) {
            return new AttemptPreview(
                    preview.eligible(),
                    preview.errorKey(),
                    preview.state(),
                    preview.recipe(),
                    preview.currentStar(),
                    preview.targetStar(),
                    successRate,
                    preview.costs(),
                    failureStar,
                    failureTemper,
                    protectionApplied,
                    preview.appliedTemperBonus(),
                    preview.successDeltaStats(),
                    preview.unlockingMilestones(),
                    preview.requiredMaterials(),
                    preview.optionalMaterials()
            );
        }
    }
}
