package emaki.jiuwu.craft.gem.script.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
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
import emaki.jiuwu.craft.gem.model.GemState;

public final class JavaScriptGemSetBonusRegistry {

    private final EmakiGemPlugin plugin;
    private final Map<String, BonusEntry> bonuses = new LinkedHashMap<>();

    public JavaScriptGemSetBonusRegistry(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> definition, JavaScriptRegistrationTracker tracker) {
        if (definition == null) {
            recordError(context, tracker, "", "register", "Gem set bonus cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Gem set bonus id cannot be blank.");
            return false;
        }
        String function = value(definition, "function", value(definition, "execute", "applySetBonus"));
        BonusEntry entry = new BonusEntry(id, function, scriptPath(context), longValue(definition.get("timeoutMillis"), defaultTimeout(context)));
        long started = System.nanoTime();
        bonuses.put(id, entry);
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                JavaScriptRegistrationType.GEM_SET_BONUS,
                id,
                elapsedMillis(started),
                () -> unregister(id),
                Map.of("function", function))) {
            bonuses.remove(id);
            return false;
        }
        return true;
    }

    public synchronized void unregister(String id) {
        bonuses.remove(Texts.normalizeId(id));
    }

    public synchronized void clear() {
        bonuses.clear();
    }

    public synchronized List<String> ids() {
        return bonuses.keySet().stream().sorted().toList();
    }

    public List<AppliedBonus> evaluate(GemItemDefinition itemDefinition, GemState state, List<GemDefinition> inlaidGems) {
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib == null || coreLib.javaScriptService() == null || !coreLib.javaScriptService().enabled()) {
            return List.of();
        }
        List<AppliedBonus> result = new ArrayList<>();
        for (BonusEntry bonus : bonuses()) {
            Map<String, Object> context = toContext(bonus.id(), itemDefinition, state, inlaidGems);
            ScriptConfig config = coreLib.configModel() == null ? ScriptConfig.defaults() : coreLib.configModel().scriptConfig();
            ScriptExecutionResult execution = coreLib.javaScriptService().invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    bonus.scriptPath(),
                    bonus.functionName(),
                    List.of(context),
                    context,
                    config.clampTimeoutMillis(bonus.timeoutMillis()),
                    true
            ));
            if (execution == null || !execution.success() || !(execution.returnValue() instanceof Map<?, ?> rawMap)) {
                if (plugin != null && (execution == null || !execution.success())) {
                    plugin.getLogger().warning("[JavaScript] Gem set bonus '" + bonus.id() + "' failed: " + (execution == null ? "no result" : execution.message()));
                }
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            ConfigNodes.entries(rawMap).forEach(map::put);
            Object nameActions = map.get("nameActions");
            Object loreActions = map.get("loreActions");
            if (nameActions != null || loreActions != null) {
                result.add(new AppliedBonus(bonus.id(), nameActions, loreActions));
            }
        }
        return List.copyOf(result);
    }

    private synchronized List<BonusEntry> bonuses() {
        return bonuses.values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    private Map<String, Object> toContext(String bonusId, GemItemDefinition itemDefinition, GemState state, List<GemDefinition> inlaidGems) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bonusId", bonusId);
        map.put("itemId", itemDefinition == null ? "" : itemDefinition.id());
        map.put("gems", gemSummaries(inlaidGems));
        map.put("gemIds", inlaidGems == null ? List.of() : inlaidGems.stream().map(GemDefinition::id).toList());
        map.put("gemTypes", inlaidGems == null ? List.of() : inlaidGems.stream().map(GemDefinition::gemType).toList());
        map.put("socketCount", state == null ? 0 : state.socketAssignments().size());
        return map;
    }

    private List<Map<String, Object>> gemSummaries(List<GemDefinition> gems) {
        if (gems == null || gems.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (GemDefinition gem : gems) {
            if (gem == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", gem.id());
            map.put("type", gem.gemType());
            map.put("level", gem.level());
            result.add(Map.copyOf(map));
        }
        return List.copyOf(result);
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), JavaScriptRegistrationType.GEM_SET_BONUS, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Gem set bonus registration failed: " + message);
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

    private record BonusEntry(String id, String functionName, String scriptPath, long timeoutMillis) {
    }

    public record AppliedBonus(String id, Object nameActions, Object loreActions) {
    }
}
