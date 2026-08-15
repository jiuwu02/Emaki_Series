package emaki.jiuwu.craft.corelib.api.script;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * JavaScript 脚本引擎契约，隐藏 GraalVM Polyglot 实现细节。
 *
 * <p>该接口允许业务模块在 YAML 配置中执行 JavaScript 脚本，以扩展 DSL 无法表达的复杂逻辑。
 * 脚本执行在受控环境中，仅暴露白名单标注的 API，并支持超时强制中断。</p>
 *
 * <p>线程安全性由实现保证：多个 Folia 区域线程可能并发调用 {@link #eval}，
 * 实现必须确保每次调用使用独立的执行上下文。</p>
 *
 * @since 4.8.0
 */
public interface ScriptEngine {

    /**
     * 执行 JavaScript 代码并返回结果。
     *
     * <p>该方法会：
     * <ul>
     *   <li>将 {@code bindings} 中的对象注入到脚本的全局作用域</li>
     *   <li>在独立的上下文中执行 {@code code}</li>
     *   <li>若执行超过 {@code timeoutMs}，强制中断并返回超时结果</li>
     *   <li>捕获脚本抛出的异常并封装为失败结果</li>
     * </ul>
     *
     * <p>脚本只能访问：
     * <ul>
     *   <li>{@code bindings} 中显式传入的对象</li>
     *   <li>这些对象上标注 {@code @HostAccess.Export} 的方法</li>
     *   <li>ECMAScript 2026 标准库（Math、Promise、Array 等）</li>
     * </ul>
     *
     * <p>{@code Java.type()} 被禁止，脚本无法访问任意 Java 类。</p>
     *
     * @param code 要执行的 JavaScript 代码
     * @param bindings 注入脚本全局作用域的对象（键为变量名，值为 Java 对象）
     * @param timeoutMs 超时时间（毫秒），超时后脚本会被强制中断
     * @return 脚本执行结果，包含成功值、失败原因或超时标记
     * @throws IllegalArgumentException 若 {@code code} 为 null 或 {@code timeoutMs} 非正数
     */
    @NotNull ScriptResult eval(@NotNull String code,
                               @NotNull Map<String, Object> bindings,
                               long timeoutMs);
}
