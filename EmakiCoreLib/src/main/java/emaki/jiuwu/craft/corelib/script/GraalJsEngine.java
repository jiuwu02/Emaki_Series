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
 * <p>该实现提供以下核心能力：</p>
 * <ul>
 *   <li><b>编译缓存共享</b>：使用共享 {@link Engine} 实例，多次执行相同代码时可复用已编译的 {@link Source}</li>
 *   <li><b>独立执行上下文</b>：每次 {@link #eval} 创建新的 {@link Context}，避免线程安全问题和脚本间状态污染</li>
 *   <li><b>超时强制中断</b>：通过看门狗线程在超时后调用 {@link Context#interrupt}，不依赖 {@code truffle-enterprise}</li>
 *   <li><b>严格沙箱隔离</b>：使用 {@link HostAccess#EXPLICIT} 限制只能访问标注 {@code @HostAccess.Export} 的方法，禁止 {@code Java.type()}</li>
 * </ul>
 *
 * <h3>性能特性</h3>
 * <p>在 Oracle GraalVM JDK 上可获得 optimized runtime（首次执行约 6-8 ms，{@link Source} 复用后约 0.065 ms）。
 * 在 Temurin/Zulu 等 stock JDK 上降级为解释器模式（首次约 40-60 ms），性能差距约 4-5 倍，但功能完全一致。</p>
 *
 * <h3>超时机制</h3>
 * <p>超时保护通过看门狗线程实现，在指定时间后：</p>
 * <ol>
 *   <li>调用 {@link Context#interrupt(Duration)} 发送中断信号（自身有 100ms 超时）</li>
 *   <li>调用 {@link Context#close(boolean)} 强制关闭上下文</li>
 *   <li>脚本执行线程捕获 {@link PolyglotException} 并返回 {@link ScriptResult#interrupted()}</li>
 * </ol>
 * <p>实测 {@code while(true){}} 死循环在约 200-260 ms 内被成功杀死，在 GraalVM JDK 和 Temurin 上行为一致。</p>
 *
 * <h3>线程安全性</h3>
 * <p>该类是线程安全的，可被多个 Folia 区域线程并发调用。{@link Context} 本身不是线程安全的，
 * 但每次调用都创建独立实例，因此不存在并发问题。共享 {@link Engine} 是线程安全的。</p>
 *
 * <h3>资源管理</h3>
 * <p>插件卸载时应调用 {@link #shutdown()} 关闭共享引擎和看门狗线程池。
 * 单次 {@link #eval} 调用会自动在 finally 块中关闭其 {@link Context}，无需手工清理。</p>
 *
 * @see ScriptEngine
 * @see ScriptResult
 * @see CompiledScriptCache
 * @since 4.7.1
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
     * <p>使用双重检查锁定（DCL）实现延迟初始化。首次调用时会创建共享 {@link Engine}、
     * 看门狗线程池和编译缓存，后续调用直接返回已有实例。</p>
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
     * <p>该方法会依次：</p>
     * <ol>
     *   <li>调用 {@link WatchdogExecutor#shutdown()} 关闭看门狗线程池（{@link ScheduledExecutorService#shutdownNow()}）</li>
     *   <li>调用 {@link Engine#close()} 关闭共享引擎并释放编译缓存</li>
     * </ol>
     *
     * <p><b>调用后该实例不应再被使用</b>，任何后续的 {@link #eval} 调用都会失败。
     * 通常在插件 {@code onDisable()} 时调用。</p>
     *
     * <p><b>注意</b>：该方法不会清空 {@link CompiledScriptCache}，但由于 {@link Engine} 已关闭，
     * 缓存的 {@link Source} 对象实际上不再有效。</p>
     */
    public void shutdown() {
        watchdog.shutdown();
        sharedEngine.close();
    }

    /**
     * 创建新的脚本执行上下文。
     *
     * <p>每次调用返回一个独立的 {@link Context} 实例，配置如下：</p>
     * <ul>
     *   <li><b>共享引擎</b>：通过 {@link Context.Builder#engine(Engine)} 复用编译缓存</li>
     *   <li><b>显式宿主访问</b>：{@link HostAccess#EXPLICIT} 只允许标注 {@code @HostAccess.Export} 的方法</li>
     *   <li><b>禁止类加载</b>：{@link Context.Builder#allowHostClassLookup(java.util.function.Predicate)} 返回 {@code false}，禁用 {@code Java.type()}</li>
     *   <li><b>关闭警告</b>：{@code engine.WarnInterpreterOnly=false} 避免在 stock JDK 上打印解释器模式警告</li>
     * </ul>
     *
     * @return 新的独立上下文实例
     */
    private Context createContext() {
        return Context.newBuilder(LANGUAGE_ID)
                .engine(sharedEngine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    /**
     * 将 Java 对象注入到脚本的全局作用域。
     *
     * <p>遍历 {@code bindings} 映射，对每个键值对调用 {@link Value#putMember(String, Object)}
     * 将 Java 对象绑定到 JavaScript 全局变量。</p>
     *
     * <p>示例：{@code bindings = Map.of("player", playerExport, "context", contextExport)}
     * 将使脚本中可以直接访问 {@code player.getHealth()} 和 {@code context.variable("damage")}。</p>
     *
     * @param context 目标上下文
     * @param bindings 变量名到 Java 对象的映射
     */
    private void injectBindings(Context context, Map<String, Object> bindings) {
        Value jsBindings = context.getBindings(LANGUAGE_ID);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            jsBindings.putMember(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 静默关闭上下文，忽略所有异常。
     *
     * <p>在 finally 块中调用，确保即使 {@link Context#interrupt} 或脚本执行异常后
     * 也能释放资源。{@link Context#close()} 可能抛出异常（例如上下文已被强制关闭），
     * 此处统一捕获并忽略。</p>
     *
     * @param context 要关闭的上下文
     */
    private void closeQuietly(Context context) {
        try {
            context.close();
        } catch (Exception ignored) {
            // Context 可能已被 interrupt 强制关闭，静默忽略
        }
    }

    /**
     * 看门狗执行器，负责超时杀死脚本。
     *
     * <p>使用单线程的 {@link ScheduledExecutorService} 调度超时任务。
     * 当脚本执行超过指定时间时，看门狗会：</p>
     * <ol>
     *   <li>调用 {@link Context#interrupt(Duration)} 发送中断信号（自身超时 100ms）</li>
     *   <li>如果 {@code interrupt} 超时，捕获 {@link TimeoutException} 并继续</li>
     *   <li>调用 {@link Context#close(boolean)} 强制关闭上下文</li>
     * </ol>
     *
     * <p>脚本执行线程在 {@link CompletableFuture#get(long, TimeUnit)} 超时或捕获
     * {@link PolyglotException#isCancelled()} 时，返回相应的失败结果。</p>
     *
     * <h4>设计考量</h4>
     * <ul>
     *   <li><b>不依赖 {@code sandbox.MaxCPUTime}</b>：该选项需要 {@code truffle-enterprise} 且只在 Oracle GraalVM 上可用，
     *       服主机器多为 Temurin/Zulu，无法获得该能力</li>
     *   <li><b>{@code interrupt} 足够可靠</b>：实测 {@code while(true){}} 死循环在约 200-260 ms 内被成功杀死，
     *       在 GraalVM JDK 25.0.1 和 Temurin 21 上行为一致</li>
     *   <li><b>留 200ms 缓冲时间</b>：{@code future.get(timeoutMs + 200, ...)} 给 {@code interrupt} 和 {@code close} 留出执行窗口，
     *       避免 Java 侧超时先于 GraalJS 侧中断完成</li>
     * </ul>
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

        /**
         * 在指定超时时间内执行脚本，超时后强制中断。
         *
         * <p>执行流程：</p>
         * <ol>
         *   <li>在 {@link CompletableFuture} 中异步执行 {@link Context#eval(Source)}</li>
         *   <li>调度看门狗任务，在 {@code timeoutMs} 后触发 {@link Context#interrupt} 和 {@link Context#close}</li>
         *   <li>在主线程等待最多 {@code timeoutMs + 200} 毫秒</li>
         *   <li>根据执行结果返回对应的 {@link ScriptResult}</li>
         * </ol>
         *
         * <h4>返回值映射</h4>
         * <ul>
         *   <li>脚本正常完成 → {@link ScriptResult#success(Object)}，值经过 {@link #convertToJava(Value)} 转换</li>
         *   <li>{@code future.get()} 超时 → {@link ScriptResult#timeout()}</li>
         *   <li>{@link PolyglotException#isCancelled()} → {@link ScriptResult#interrupted()}</li>
         *   <li>其他异常 → {@link ScriptResult#error(Throwable)}，传递原始 {@code cause}</li>
         *   <li>{@link InterruptedException} → {@link ScriptResult#interrupted()}，同时恢复中断标志</li>
         * </ul>
         *
         * @param context 执行上下文
         * @param source 已编译的脚本源码
         * @param timeoutMs 超时时间（毫秒）
         * @return 脚本执行结果
         */
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

        /**
         * 立即关闭看门狗线程池。
         *
         * <p>调用 {@link ScheduledExecutorService#shutdownNow()} 中断所有正在等待的看门狗任务。
         * 通常在 {@link GraalJsEngine#shutdown()} 中调用。</p>
         */
        void shutdown() {
            scheduler.shutdownNow();
        }

        /**
         * 将 GraalVM {@link Value} 转换为 Java 原生类型。
         *
         * <p>转换规则：</p>
         * <ul>
         *   <li>布尔值 → {@link Boolean}</li>
         *   <li>数值 → 优先 {@link Integer}，其次 {@link Long}，最后 {@link Double}</li>
         *   <li>字符串 → {@link String}</li>
         *   <li>其他类型 → 调用 {@link Value#toString()} 返回字符串表示</li>
         * </ul>
         *
         * <p>该方法不处理 {@code null} 和 {@code undefined}（调用方已在上层判断 {@link Value#isNull()}），
         * 也不处理 {@link Value#isHostObject()}（直接通过 {@link Value#asHostObject()} 返回原 Java 对象）。</p>
         *
         * @param value GraalVM 值对象
         * @return 转换后的 Java 对象
         */
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
