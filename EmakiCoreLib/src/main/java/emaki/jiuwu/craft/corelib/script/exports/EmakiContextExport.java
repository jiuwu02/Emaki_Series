package emaki.jiuwu.craft.corelib.script.exports;

import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

/**
 * {@link CoreStageContext} 的 JavaScript 白名单导出。
 *
 * <p>该类是 {@link CoreStageContext} 在 GraalVM JavaScript 沙箱中的安全代理，只暴露变量查询能力。
 * 内部状态（如目标实体、管道控制方法）对脚本不可见，避免脚本绕过 Stage 契约直接操纵执行流程。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>只读变量访问</b>：脚本可通过 {@link #getVariable(String)} 读取上下文变量，但无法修改或删除</li>
 *   <li><b>隔离内部状态</b>：不暴露 {@code target()}、{@code phase()}、{@code interrupt()} 等方法，避免脚本干扰执行流程</li>
 *   <li><b>类型安全</b>：变量值可能是原始类型、字符串或复杂对象，脚本需自行判断类型并处理</li>
 * </ul>
 *
 * <h3>变量求值时机</h3>
 * <p>变量已在管道执行前通过 {@code ExpressionEngine} 求值，脚本看到的是求值后的数值。
 * 例如 {@code damage: "{player.maxHealth} * 0.5"} 会在进入脚本前被求值为具体数值（如 {@code 10.0}），
 * 脚本调用 {@code context.getVariable("damage")} 得到的是 {@code 10.0} 而非表达式字符串。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // YAML 配置中的脚本
 * script: |
 *   const baseDamage = context.getVariable("damage");
 *   const multiplier = context.hasVariable("critical") ? 2.0 : 1.0;
 *   return baseDamage * multiplier;
 * }</pre>
 *
 * <h3>线程安全性</h3>
 * <p>该类本身不维护可变状态，线程安全性取决于底层 {@link CoreStageContext} 对象。
 * EmakiCoreLib 的 Stage 执行环境保证单个 Context 不会被并发访问。</p>
 *
 * @see CoreStageContext
 * @see org.graalvm.polyglot.HostAccess.Export
 * @since 4.7.1
 */
public final class EmakiContextExport {

    private final CoreStageContext context;

    public EmakiContextExport(@NotNull CoreStageContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        this.context = context;
    }

    /**
     * 获取上下文变量值。
     *
     * <p>变量已在管道执行前通过 {@code ExpressionEngine} 求值，
     * 脚本看到的是求值后的数值。</p>
     *
     * @param name 变量名
     * @return 变量值，若不存在则返回 {@code null}
     */
    @HostAccess.Export
    public @Nullable Object getVariable(@NotNull String name) {
        if (name == null) {
            return null;
        }
        return context.variable(name).orElse(null);
    }

    /**
     * 检查变量是否存在。
     *
     * @param name 变量名
     * @return 变量是否存在
     */
    @HostAccess.Export
    public boolean hasVariable(@NotNull String name) {
        if (name == null) {
            return false;
        }
        return context.variable(name).isPresent();
    }

    @Override
    public String toString() {
        return "EmakiContextExport{context=" + context.phase() + "}";
    }
}
