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

/**
 * GraalVM JavaScript 脚本引擎实现。
 *
 * <p>该实现：
 * <ul>
 *   <li>使用共享 {@link Engine} 以复用编译缓存</li>
 *   <li>每次 eval 创建新的 {@link Context}（非线程安全）</li>
 *   <li>通过看门狗线程实现超时强制中断（不依赖 truffle-enterprise）</li>
 *   <li>使用 {@link HostAccess#EXPLICIT} 严格限制宿主访问</li>
 * </ul>
 *
 * <p>线程安全性：该类是线程安全的，可被多个 Folia 区域线程并发调用。</p>
 */
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

    /**
     * 获取全局单例。
     *
     * @return 全局 GraalJS 引擎实例
     */
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

    /**
     * 关闭引擎并释放资源。
     *
     * <p>该方法会关闭共享引擎和看门狗线程池。
     * 调用后该实例不应再被使用。</p>
     */
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
            // Context 可能已被 interrupt 强制关闭，静默忽略
        }
    }

    /**
     * 看门狗执行器，负责超时杀死脚本。
     */
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
                            // interrupt 自身超时，直接强制关闭
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
