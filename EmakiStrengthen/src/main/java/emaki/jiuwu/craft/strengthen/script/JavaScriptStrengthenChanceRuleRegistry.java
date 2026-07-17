package emaki.jiuwu.craft.strengthen.script;

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
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptCost;
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
        String operationId = attemptContext == null ? "" : attemptContext.operationId();
        Adjustment current = Adjustment.from(player, operationId, targetItem, requiredMaterials, optionalMaterials, preview);
        for (RuleEntry rule : sortedRules()) {
            Adjustment adjusted = applyRule(coreLib, javaScriptService, rule, current);
            if (adjusted == null) {
                logRuleFailure(player, operationId, rule.id());
                return failClosed(preview);
            }
            current = adjusted;
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
            plugin.debugLogger().logRaw("script", playerId, "script trace | operationId="
                    + Texts.toStringSafe(trace.get("operationId")) + " | rule=" + Texts.toStringSafe(trace.get("id"))
                    + " | before=" + Texts.toStringSafe(trace.get("before"))
                    + " | after=" + Texts.toStringSafe(trace.get("after"))
                    + " | msg=" + Texts.toStringSafe(trace.get("message")));
        }
    }

    private Adjustment applyRule(EmakiCoreLibPlugin coreLib, JavaScriptService javaScriptService, RuleEntry rule, Adjustment current) {
        try {
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
                return null;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            ConfigNodes.entries(rawMap).forEach(map::put);
            double before = current.successRate();
            double after = before;
            String explicitKey = map.containsKey("successRate") ? "successRate" : map.containsKey("chance") ? "chance" : "";
            if (!explicitKey.isEmpty()) {
                after = requiredFiniteNumber(map.get(explicitKey), explicitKey);
            } else {
                if (map.containsKey("successBonus")) {
                    after += requiredFiniteNumber(map.get("successBonus"), "successBonus");
                }
                if (map.containsKey("successMultiplier")) {
                    after *= requiredFiniteNumber(map.get("successMultiplier"), "successMultiplier");
                }
            }
            if (!Double.isFinite(after)) {
                return null;
            }
            int failureStar = map.containsKey("failureStar")
                    ? requiredNonNegativeInt(map.get("failureStar"), "failureStar")
                    : current.failureStar();
            int failureTemper = map.containsKey("failureTemper")
                    ? requiredNonNegativeInt(map.get("failureTemper"), "failureTemper")
                    : current.failureTemper();
            boolean protectionApplied = map.containsKey("protectionApplied")
                    ? requiredBoolean(map.get("protectionApplied"), "protectionApplied")
                    : current.protectionApplied();
            boolean failureProtected = map.containsKey("failureProtected")
                    && requiredBoolean(map.get("failureProtected"), "failureProtected");
            boolean downgradeProtected = map.containsKey("downgradeProtected")
                    && requiredBoolean(map.get("downgradeProtected"), "downgradeProtected");
            if (failureProtected || downgradeProtected) {
                protectionApplied = true;
                failureStar = Math.max(failureStar, current.currentStar());
                failureTemper = 0;
            }
            ParsedCosts extraCosts = parseCosts(map.get("extraCosts"));
            if (!extraCosts.valid()) {
                return null;
            }
            double sanitized = Numbers.clamp(after, 0D, 100D);
            Adjustment next = current.withValues(sanitized, failureStar, failureTemper, protectionApplied);
            if (!extraCosts.costs().isEmpty()) {
                next = next.withExtraCosts(extraCosts.costs());
            }
            return next.withTrace(rule.id(), before, sanitized, Texts.toStringSafe(map.get("message")));
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("[JavaScript] Strengthen chance rule failed closed | operationId="
                    + current.operationId() + " | rule=" + rule.id() + " | error=" + exception.getMessage());
            return null;
        }
    }

    private AttemptPreview failClosed(AttemptPreview preview) {
        return new AttemptPreview(false, "strengthen.error.chance_rule_failed", preview.state(), preview.recipe(),
                preview.currentStar(), preview.targetStar(), 0D, preview.costs(), preview.failureStar(),
                preview.failureTemper(), preview.protectionApplied(), preview.appliedTemperBonus(),
                preview.successDeltaStats(), preview.unlockingMilestones(), preview.requiredMaterials(),
                preview.optionalMaterials());
    }

    private void logRuleFailure(Player player, String operationId, String ruleId) {
        plugin.getLogger().warning("[JavaScript] Strengthen chance rule rejected attempt | operationId="
                + (Texts.isBlank(operationId) ? "-" : operationId) + " | rule=" + ruleId);
        java.util.UUID playerId = player == null ? null : player.getUniqueId();
        if (plugin.debugLogger() != null && plugin.debugLogger().shouldLog("script", playerId)) {
            plugin.debugLogger().logRaw("script", playerId, "chance rule failed closed | operationId="
                    + operationId + " | rule=" + ruleId);
        }
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

    private static double requiredFiniteNumber(Object raw, String field) {
        double value;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            try {
                value = Double.parseDouble(Texts.toStringSafe(raw));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid numeric field: " + field, exception);
            }
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite numeric field: " + field);
        }
        return value;
    }

    private static int requiredNonNegativeInt(Object raw, String field) {
        double value = requiredFiniteNumber(raw, field);
        if (value < 0D || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            throw new IllegalArgumentException("Invalid integer field: " + field);
        }
        return (int) value;
    }

    private static boolean requiredBoolean(Object raw, String field) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = Texts.toStringSafe(raw).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean field: " + field);
    }

    private static ParsedCosts parseCosts(Object raw) {
        if (raw == null) {
            return new ParsedCosts(true, List.of());
        }
        if (!(raw instanceof List<?> list)) {
            return new ParsedCosts(false, List.of());
        }
        List<AttemptCost> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> rawCost)) {
                return new ParsedCosts(false, List.of());
            }
            Map<String, Object> cost = new LinkedHashMap<>(ConfigNodes.entries(rawCost));
            String provider = Texts.lower(Texts.toStringSafe(cost.get("provider")));
            String currencyId = Texts.toStringSafe(cost.containsKey("currencyId")
                    ? cost.get("currencyId") : cost.get("currency"));
            String displayName = Texts.toStringSafe(cost.containsKey("displayName")
                    ? cost.get("displayName") : cost.get("name"));
            if (Texts.isBlank(provider) || !cost.containsKey("amount")) {
                return new ParsedCosts(false, List.of());
            }
            double rawAmount;
            try {
                rawAmount = requiredFiniteNumber(cost.get("amount"), "extraCosts.amount");
            } catch (IllegalArgumentException exception) {
                return new ParsedCosts(false, List.of());
            }
            if (rawAmount <= 0D || rawAmount > Long.MAX_VALUE || rawAmount != Math.rint(rawAmount)
                    || "items".equals(provider) && Texts.isBlank(currencyId)) {
                return new ParsedCosts(false, List.of());
            }
            result.add(new AttemptCost(provider, currencyId,
                    Texts.isBlank(displayName) ? (Texts.isBlank(currencyId) ? provider : currencyId) : displayName,
                    (long) rawAmount));
        }
        return new ParsedCosts(true, List.copyOf(result));
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

    private record ParsedCosts(boolean valid, List<AttemptCost> costs) {
    }

    private record Adjustment(String playerUuid,
            String playerName,
            String operationId,
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
            List<AttemptCost> extraCosts,
            List<Map<String, Object>> traces) {

        private Adjustment {
            operationId = operationId == null ? "" : operationId;
            targetItem = targetItem == null ? Map.of() : ScriptSnapshots.immutableMap(targetItem);
            requiredMaterials = requiredMaterials == null ? List.of() : castMapList(ScriptSnapshots.immutableValue(requiredMaterials));
            optionalMaterials = optionalMaterials == null ? List.of() : castMapList(ScriptSnapshots.immutableValue(optionalMaterials));
            extraCosts = extraCosts == null ? List.of() : List.copyOf(extraCosts);
            traces = traces == null ? List.of() : castMapList(ScriptSnapshots.immutableValue(traces));
        }

        static Adjustment from(Player player,
                String operationId,
                ItemStack targetItem,
                List<AttemptMaterial> requiredMaterials,
                List<AttemptMaterial> optionalMaterials,
                AttemptPreview preview) {
            double initialRate = Double.isFinite(preview.successRate())
                    ? Numbers.clamp(preview.successRate(), 0D, 100D) : 0D;
            return new Adjustment(
                    player == null ? "" : player.getUniqueId().toString(),
                    player == null ? "" : player.getName(),
                    operationId,
                    preview.recipe() == null ? "" : preview.recipe().id(),
                    preview.currentStar(),
                    preview.targetStar(),
                    preview.state() == null ? 0 : preview.state().temperLevel(),
                    initialRate,
                    initialRate,
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
            return new Adjustment(playerUuid, playerName, operationId, recipeId, currentStar, targetStar, temperLevel, originalSuccessRate,
                    successRate, failureStar, failureTemper, protectionApplied, targetItem, requiredMaterials, optionalMaterials,
                    extraCosts, traces);
        }

        Adjustment withExtraCosts(List<AttemptCost> additionalCosts) {
            if (additionalCosts == null || additionalCosts.isEmpty()) {
                return this;
            }
            List<AttemptCost> merged = new ArrayList<>(extraCosts);
            merged.addAll(additionalCosts);
            return new Adjustment(playerUuid, playerName, operationId, recipeId, currentStar, targetStar, temperLevel, originalSuccessRate,
                    successRate, failureStar, failureTemper, protectionApplied, targetItem, requiredMaterials, optionalMaterials,
                    List.copyOf(merged), traces);
        }

        Adjustment withTrace(String ruleId, double before, double after, String message) {
            List<Map<String, Object>> updated = new ArrayList<>(traces);
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("id", ruleId);
            trace.put("operationId", operationId);
            trace.put("before", before);
            trace.put("after", after);
            trace.put("message", Texts.toStringSafe(message));
            updated.add(Map.copyOf(trace));
            return new Adjustment(playerUuid, playerName, operationId, recipeId, currentStar, targetStar, temperLevel, originalSuccessRate,
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
            map.put("operationId", operationId);
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
            map.put("extraCosts", costSummaries(extraCosts));
            map.put("traces", traces);
            return ScriptSnapshots.immutableMap(map);
        }

        private static List<Map<String, Object>> costSummaries(List<AttemptCost> costs) {
            if (costs == null || costs.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> summaries = new ArrayList<>(costs.size());
            for (AttemptCost cost : costs) {
                summaries.add(Map.of(
                        "provider", cost.provider(),
                        "currencyId", cost.currencyId(),
                        "displayName", cost.displayName(),
                        "amount", cost.amount()));
            }
            return List.copyOf(summaries);
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> castMapList(Object value) {
            return value instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
        }

        AttemptPreview toPreview(AttemptPreview preview) {
            List<AttemptCost> finalCosts = new ArrayList<>(preview.costs());
            finalCosts.addAll(extraCosts);
            return new AttemptPreview(
                    preview.eligible(),
                    preview.errorKey(),
                    preview.state(),
                    preview.recipe(),
                    preview.currentStar(),
                    preview.targetStar(),
                    successRate,
                    List.copyOf(finalCosts),
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
