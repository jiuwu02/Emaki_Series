package emaki.jiuwu.craft.corelib.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Source;
import org.jetbrains.annotations.NotNull;

/**
 * GraalJS 编译脚本缓存，避免重复解析相同代码。
 *
 * <p>该缓存将 JavaScript 代码字符串编译为 {@link Source} 对象并复用。
 * GraalVM 的编译和 JIT 优化成本较高，首次执行一段新代码可能需要 6-8ms（optimized runtime）
 * 或 40-60ms（stock JDK 解释器），但复用已缓存的 {@link Source} 可将热路径 eval 耗时降至约 0.065ms，
 * 性能提升约 100 倍。</p>
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li><b>键</b>：JavaScript 代码字符串本身（精确匹配）</li>
 *   <li><b>值</b>：{@link Source#newBuilder} 构建的 {@link Source} 对象，{@code cached(true)} 启用 GraalVM 内部编译缓存</li>
 *   <li><b>容量</b>：无上限，依赖 {@link ConcurrentHashMap} 自然增长</li>
 *   <li><b>过期</b>：不自动过期，手工调用 {@link #clear()} 清空</li>
 * </ul>
 *
 * <p><b>使用场景</b>：适合 YAML 配置中的固定脚本片段（每次 Action 执行都是相同代码）。
 * 对于动态拼接的脚本代码，缓存命中率会下降，但不会引入功能错误。</p>
 *
 * <h3>线程安全性</h3>
 * <p>该类使用 {@link ConcurrentHashMap}，可被多个 Folia 区域线程并发访问。
 * {@link #getOrCompile} 通过 {@link Map#computeIfAbsent} 保证相同代码只编译一次。</p>
 *
 * @see GraalJsEngine
 * @since 4.7.1
 */
final class CompiledScriptCache {

    private static final String LANGUAGE_ID = "js";
    private final Map<String, Source> cache = new ConcurrentHashMap<>();

    /**
     * 获取或编译脚本。
     *
     * <p>若 {@code code} 已缓存，直接返回缓存的 {@link Source}；
     * 否则通过 {@link Source.Builder#buildLiteral()} 编译并缓存。</p>
     *
     * <p>{@code cached(true)} 选项指示 GraalVM 在 {@link org.graalvm.polyglot.Engine} 层缓存编译结果，
     * 即使 {@link org.graalvm.polyglot.Context} 被销毁后，下次使用相同 {@link Source} 仍可复用编译产物。</p>
     *
     * <p>{@code name} 参数仅用于调试和错误消息中的源文件名显示，不影响缓存键的匹配。</p>
     *
     * @param code 脚本代码
     * @param name 脚本名称，用于调试和错误消息（如 {@code "eval-1234567890"}）
     * @return 编译后的 {@link Source} 对象
     */
    @NotNull Source getOrCompile(@NotNull String code, @NotNull String name) {
        return cache.computeIfAbsent(code, key ->
                Source.newBuilder(LANGUAGE_ID, key, name)
                        .cached(true)
                        .buildLiteral()
        );
    }

    /**
     * 清空缓存。
     *
     * <p>移除所有已缓存的 {@link Source} 对象。下次 {@link #getOrCompile} 调用将重新编译。
     * 该方法通常在插件重载或调试场景下使用，正常运行时不需要清空缓存。</p>
     */
    void clear() {
        cache.clear();
    }

    /**
     * 获取当前缓存的脚本数量。
     *
     * <p>返回 {@link ConcurrentHashMap} 中的条目数，即不同代码字符串的数量。
     * 该方法主要用于监控和调试。</p>
     *
     * @return 当前缓存的脚本数量
     */
    int size() {
        return cache.size();
    }
}
