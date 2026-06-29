package emaki.jiuwu.craft.corelib.script.js;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTypes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptConditionRegistry {

    private final Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final JavaScriptRegistrationTracker tracker;
    private final java.util.function.Supplier<DebugLogger> debugLoggerSupplier;
    private final Map<String, RegisteredCondition> conditions = new LinkedHashMap<>();

    public JavaScriptConditionRegistry(Plugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            JavaScriptRegistrationTracker tracker,
            java.util.function.Supplier<DebugLogger> debugLoggerSupplier) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.tracker = tracker;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    public synchronized boolean register(String scriptPath,
            String id,
            String description,
            List<String> parameters,
            String functionName,
            long timeoutMillis) {
        String normalizedId = Texts.normalizeId(id);
        if (Texts.isBlank(normalizedId) || javaScriptService == null) {
            if (tracker != null) {
                tracker.recordError(scriptPath, JavaScriptRegistrationTypes.CONDITION, normalizedId, "register", "Condition id is blank.");
            }
            return false;
        }
        List<String> safeParameters = parameters == null ? List.of() : parameters.stream()
                .map(Texts::normalizeId)
                .filter(Texts::isNotBlank)
                .toList();
        RegisteredCondition condition = new RegisteredCondition(
                normalizedId,
                Texts.toStringSafe(description),
                safeParameters,
                Texts.isBlank(functionName) ? normalizedId : Texts.trim(functionName),
                scriptPath,
                scriptConfig.clampTimeoutMillis(timeoutMillis));
        long started = System.nanoTime();
        boolean tracked = tracker == null || tracker.register(plugin,
                scriptPath,
                JavaScriptRegistrationTypes.CONDITION,
                normalizedId,
                elapsedMillis(started),
                () -> unregister(normalizedId),
                Map.of("function", condition.functionName(), "parameters", safeParameters));
        if (!tracked) {
            return false;
        }
        conditions.put(normalizedId, condition);
        return true;
    }

    public synchronized void unregister(String id) {
        conditions.remove(Texts.normalizeId(id));
    }

    public synchronized void clear() {
        conditions.clear();
    }

    public synchronized boolean contains(String id) {
        return conditions.containsKey(Texts.normalizeId(id));
    }

    public synchronized List<String> conditionIds() {
        return conditions.keySet().stream().sorted().toList();
    }

    public ConditionResult evaluate(String id, Map<String, Object> context, Map<String, Object> args) {
        RegisteredCondition condition;
        synchronized (this) {
            condition = conditions.get(Texts.normalizeId(id));
        }
        if (condition == null) {
            return ConditionResult.invalid("Unknown JavaScript condition: " + id);
        }
        long started = System.nanoTime();
        try {
            Map<String, Object> safeContext = context == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(context));
            Map<String, Object> safeArgs = args == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(args));
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    condition.scriptPath(),
                    condition.functionName(),
                    List.of(safeContext, safeArgs),
                    Map.of("extension", "condition", "id", condition.id(), "script", condition.scriptPath(), "args", safeArgs),
                    condition.timeoutMillis(),
                    true
            ));
            ConditionResult evaluated = parseResult(result);
            debug(condition, safeArgs, evaluated, elapsedMillis(started), result == null || result.success() ? "" : result.message());
            return evaluated;
        } catch (RuntimeException exception) {
            if (tracker != null) {
                tracker.recordError(condition.scriptPath(), JavaScriptRegistrationTypes.CONDITION, condition.id(), condition.functionName(), exception.getMessage());
            }
            ConditionResult failed = ConditionResult.invalid(exception.getMessage());
            debug(condition, args, failed, elapsedMillis(started), exception.getMessage());
            return failed;
        }
    }

    private ConditionResult parseResult(ScriptExecutionResult result) {
        if (result == null) {
            return ConditionResult.invalid("No script result.");
        }
        if (!result.success() || result.skipped()) {
            return ConditionResult.invalid(result.message());
        }
        Object raw = result.returnValue();
        if (raw instanceof Boolean bool) {
            return new ConditionResult(true, bool, "");
        }
        if (raw instanceof Map<?, ?> map) {
            Object passed = map.containsKey("passed") ? map.get("passed") : map.get("success");
            boolean value = passed instanceof Boolean bool ? bool : Boolean.parseBoolean(Texts.toStringSafe(passed));
            return new ConditionResult(true, value, Texts.toStringSafe(map.get("message")));
        }
        Object outputPassed = result.output().containsKey("passed") ? result.output().get("passed") : result.output().get("success");
        if (outputPassed != null) {
            boolean value = outputPassed instanceof Boolean bool ? bool : Boolean.parseBoolean(Texts.toStringSafe(outputPassed));
            return new ConditionResult(true, value, Texts.toStringSafe(result.output().get("message")));
        }
        String text = Texts.toStringSafe(raw);
        if (Texts.isBlank(text)) {
            return ConditionResult.invalid("JavaScript condition returned blank value.");
        }
        return new ConditionResult(true, Boolean.parseBoolean(text), "");
    }

    private void debug(RegisteredCondition condition, Map<String, Object> args, ConditionResult result, long elapsedMillis, String error) {
        DebugLogger logger = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (logger == null) {
            return;
        }
        logger.logRaw("script", (java.util.UUID) null, "condition id=" + condition.id()
                + " args=" + (args == null ? Map.of() : args)
                + " passed=" + result.passed()
                + " duration=" + elapsedMillis + "ms"
                + (Texts.isBlank(result.message()) ? "" : " message=" + result.message())
                + (Texts.isBlank(error) ? "" : " error=" + error));
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private record RegisteredCondition(String id,
            String description,
            List<String> parameters,
            String functionName,
            String scriptPath,
            long timeoutMillis) {
    }

    public record ConditionResult(boolean valid, boolean passed, String message) {

        public ConditionResult {
            message = Texts.toStringSafe(message);
        }

        public static ConditionResult invalid(String message) {
            return new ConditionResult(false, false, message);
        }
    }
}
