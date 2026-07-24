package emaki.jiuwu.craft.corelib.script.js;

import java.util.ArrayList;
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
import net.objecthunter.exp4j.function.Function;

public final class JavaScriptExpressionFunctionRegistry {

    private final Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final JavaScriptRegistrationTracker tracker;
    private final java.util.function.Supplier<DebugLogger> debugLoggerSupplier;
    private final Map<String, RegisteredExpressionFunction> functions = new LinkedHashMap<>();

    public JavaScriptExpressionFunctionRegistry(Plugin plugin,
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
                tracker.recordError(scriptPath, JavaScriptRegistrationTypes.EXPRESSION_FUNCTION, normalizedId, "register", "Expression function id is blank.");
            }
            return false;
        }
        List<String> safeParameters = parameters == null ? List.of() : parameters.stream()
                .map(Texts::normalizeId)
                .filter(Texts::isNotBlank)
                .toList();
        RegisteredExpressionFunction registered = new RegisteredExpressionFunction(
                normalizedId,
                Texts.toStringSafe(description),
                safeParameters,
                Texts.isBlank(functionName) ? normalizedId : Texts.trim(functionName),
                scriptPath,
                scriptConfig.clampTimeoutMillis(timeoutMillis),
                this);
        long started = System.nanoTime();
        boolean tracked = tracker == null || tracker.register(plugin,
                scriptPath,
                JavaScriptRegistrationTypes.EXPRESSION_FUNCTION,
                normalizedId,
                elapsedMillis(started),
                () -> unregister(normalizedId),
                Map.of("function", registered.functionName(), "parameters", safeParameters));
        if (!tracked) {
            return false;
        }
        functions.put(normalizedId, registered);
        emaki.jiuwu.craft.corelib.expression.ExpressionEngine.clearGlobalCache();
        emaki.jiuwu.craft.corelib.expression.ExpressionEngine.clearThreadLocalCache();
        return true;
    }

    public synchronized void unregister(String id) {
        functions.remove(Texts.normalizeId(id));
        emaki.jiuwu.craft.corelib.expression.ExpressionEngine.clearGlobalCache();
        emaki.jiuwu.craft.corelib.expression.ExpressionEngine.clearThreadLocalCache();
    }

    public synchronized void clear() {
        functions.clear();
        emaki.jiuwu.craft.corelib.expression.ExpressionEngine.clearGlobalCache();
        emaki.jiuwu.craft.corelib.expression.ExpressionEngine.clearThreadLocalCache();
    }

    public synchronized List<Function> exp4jFunctions() {
        List<Function> result = new ArrayList<>();
        for (RegisteredExpressionFunction function : functions.values()) {
            result.add(function.asExp4jFunction());
        }
        return List.copyOf(result);
    }

    public synchronized List<String> functionIds() {
        return functions.keySet().stream().sorted().toList();
    }

    private double invoke(RegisteredExpressionFunction function, double... args) {
        long started = System.nanoTime();
        try {
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    function.scriptPath(),
                    function.functionName(),
                    List.of(argumentList(args), Map.of("id", function.id(), "parameters", function.parameters())),
                    Map.of("extension", "expression", "id", function.id(), "script", function.scriptPath(), "args", argumentList(args)),
                    function.timeoutMillis(),
                    true
            ));
            Object raw = result == null ? null : result.returnValue();
            if (raw == null && result != null && result.output().containsKey("value")) {
                raw = result.output().get("value");
            }
            double value = toNumber(raw);
            debug(function, args, value, elapsedMillis(started), result == null || result.success() ? "" : result.message());
            return value;
        } catch (RuntimeException exception) {
            if (tracker != null) {
                tracker.recordError(function.scriptPath(), JavaScriptRegistrationTypes.EXPRESSION_FUNCTION, function.id(), function.functionName(), exception.getMessage());
            }
            debug(function, args, 0D, elapsedMillis(started), exception.getMessage());
            return 0D;
        }
    }

    private void debug(RegisteredExpressionFunction function, double[] args, double value, long elapsedMillis, String error) {
        DebugLogger logger = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (logger == null) {
            return;
        }
        logger.log("script", (java.util.UUID) null, "common.script.expression", Map.of(
                "expression", function.id(),
                "args", argumentList(args),
                "result", value,
                "duration_ms", elapsedMillis,
                "error", Texts.toStringSafe(error)
        ));
    }

    private static List<Double> argumentList(double... args) {
        if (args == null || args.length == 0) {
            return List.of();
        }
        List<Double> values = new ArrayList<>();
        for (double arg : args) {
            values.add(arg);
        }
        return List.copyOf(values);
    }

    private static double toNumber(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof Boolean bool) {
            return bool ? 1D : 0D;
        }
        Double parsed = emaki.jiuwu.craft.corelib.math.Numbers.tryParseDouble(Texts.toStringSafe(raw), null);
        return parsed == null || parsed.isNaN() || parsed.isInfinite() ? 0D : parsed;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private record RegisteredExpressionFunction(String id,
            String description,
            List<String> parameters,
            String functionName,
            String scriptPath,
            long timeoutMillis,
            JavaScriptExpressionFunctionRegistry registry) {

        private Function asExp4jFunction() {
            return new Function(id, parameters.size()) {
                @Override
                public double apply(double... args) {
                    return registry.invoke(RegisteredExpressionFunction.this, args);
                }
            };
        }
    }
}
