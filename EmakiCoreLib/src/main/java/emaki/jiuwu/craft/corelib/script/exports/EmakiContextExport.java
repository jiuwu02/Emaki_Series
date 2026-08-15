package emaki.jiuwu.craft.corelib.script.exports;

import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

/**
 * {@link CoreStageContext} 的 JavaScript 白名单导出。
 *
 * <p>该类暴露上下文中的变量和元数据，但不暴露内部状态和目标操作方法。</p>
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
