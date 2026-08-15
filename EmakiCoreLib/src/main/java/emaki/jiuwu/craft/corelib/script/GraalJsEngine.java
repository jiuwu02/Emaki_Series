package emaki.jiuwu.craft.corelib.script;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.script.ScriptEngine;
import emaki.jiuwu.craft.corelib.api.script.ScriptResult;

public final class GraalJsEngine implements ScriptEngine {

    private static final String LANGUAGE_ID = "js";
    private static volatile GraalJsEngine instance;

    private final Engine sharedEngine;
    private final WatchdogExecutor watchdog;
    private final CompiledScriptCache cache;

    private GraalJsEngine() {
        this.sharedEngine = Engine.newBuilder(LANGUAGE_ID)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        this.watchdog = new WatchdogExecutor();
        this.cache = new CompiledScriptCache();
    }

    public static @NotNull GraalJsEngine getInstance() {
        if (instance == null) {
            synchronized (GraalJsEngine.class) {
                if (instance == null) {
                    instance = new GraalJsEngine();
                }
            }
        }
        return instance;
    }

    @Override
    public @NotNull ScriptResult eval(@NotNull String code,
                                       @NotNull Map<String, Object> bindings,
                                       long timeoutMs) {
        if (code == null) {
            throw new IllegalArgumentException("code cannot be null");
        }
        if (bindings == null) {
            throw new IllegalArgumentException("bindings cannot be null");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }

        Source source = cache.getOrCompile(code, "eval-" + System.currentTimeMillis());
        Context context = createContext();
        try {
            injectBindings(context, bindings);
            return watchdog.evalWithTimeout(context, source, timeoutMs);
        } finally {
            closeQuietly(context);
        }
    }

    public void shutdown() {
        watchdog.shutdown();
        sharedEngine.close();
    }

    private Context createContext() {
        return Context.newBuilder(LANGUAGE_ID)
                .engine(sharedEngine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    private void injectBindings(Context context, Map<String, Object> bindings) {
        Value jsBindings = context.getBindings(LANGUAGE_ID);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            jsBindings.putMember(entry.getKey(), entry.getValue());
        }
    }

    private void closeQuietly(Context context) {
        try {
            context.close();
        } catch (Exception ignored) {

        }
    }

    private static final class WatchdogExecutor {

        private final ScheduledExecutorService scheduler;

        WatchdogExecutor() {
            this.scheduler = Executors.newScheduledThreadPool(1, runnable -> {
                Thread thread = new Thread(runnable, "GraalJS-Watchdog");
                thread.setDaemon(true);
                return thread;
            });
        }

        ScriptResult evalWithTimeout(Context context, Source source, long timeoutMs) {
            CompletableFuture<Value> future = CompletableFuture.supplyAsync(
                    () -> context.eval(source)
            );

            ScheduledFuture<?> killer = scheduler.schedule(
                    () -> {
                        try {
                            context.interrupt(Duration.ofMillis(100));
                        } catch (TimeoutException e) {

                        }
                        context.close(true);
                    },
                    timeoutMs,
                    TimeUnit.MILLISECONDS
            );

            try {
                Value result = future.get(timeoutMs + 200, TimeUnit.MILLISECONDS);
                killer.cancel(false);
                Object javaValue = result.isHostObject() ? result.asHostObject()
                        : result.isNull() ? null
                        : convertToJava(result);
                return ScriptResult.success(javaValue);
            } catch (TimeoutException exception) {
                killer.cancel(false);
                return ScriptResult.timeout();
            } catch (ExecutionException exception) {
                killer.cancel(false);
                Throwable cause = exception.getCause();
                if (cause instanceof PolyglotException polyglotException) {
                    if (polyglotException.isCancelled()) {
                        return ScriptResult.interrupted();
                    }
                }
                return ScriptResult.error(cause != null ? cause : exception);
            } catch (InterruptedException exception) {
                killer.cancel(false);
                Thread.currentThread().interrupt();
                return ScriptResult.interrupted();
            }
        }

        void shutdown() {
            scheduler.shutdownNow();
        }

        private Object convertToJava(Value value) {
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber()) {
                if (value.fitsInInt()) {
                    return value.asInt();
                }
                if (value.fitsInLong()) {
                    return value.asLong();
                }
                return value.asDouble();
            }
            if (value.isString()) {
                return value.asString();
            }
            return value.toString();
        }
    }
}
