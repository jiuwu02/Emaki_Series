package emaki.jiuwu.craft.corelib.api.script;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * JavaScript 脚本执行的结果，封装成功值、失败原因或超时标记。
 *
 * <p>该类是密封的结果类型，只能通过工厂方法构造：
 * <ul>
 *   <li>{@link #success(Object)} - 脚本正常执行完成</li>
 *   <li>{@link #error(Throwable)} - 脚本抛出异常</li>
 *   <li>{@link #timeout()} - 脚本执行超时被强制中断</li>
 *   <li>{@link #interrupted()} - 脚本被外部中断信号终止</li>
 * </ul>
 *
 * @since 4.8.0
 */
public final class ScriptResult {

    private final boolean success;
    private final Object value;
    private final Throwable error;
    private final boolean timeout;
    private final boolean interrupted;

    private ScriptResult(boolean success, Object value, Throwable error, boolean timeout, boolean interrupted) {
        this.success = success;
        this.value = value;
        this.error = error;
        this.timeout = timeout;
        this.interrupted = interrupted;
    }

    /**
     * 创建成功结果。
     *
     * @param value 脚本返回值（可能为 {@code null}，对应 JavaScript 的 {@code null} 或 {@code undefined}）
     * @return 成功结果
     */
    public static @NotNull ScriptResult success(@Nullable Object value) {
        return new ScriptResult(true, value, null, false, false);
    }

    /**
     * 创建失败结果。
     *
     * @param error 脚本执行过程中抛出的异常
     * @return 失败结果
     */
    public static @NotNull ScriptResult error(@NotNull Throwable error) {
        if (error == null) {
            throw new IllegalArgumentException("error cannot be null");
        }
        return new ScriptResult(false, null, error, false, false);
    }

    /**
     * 创建超时结果。
     *
     * @return 超时结果
     */
    public static @NotNull ScriptResult timeout() {
        return new ScriptResult(false, null, null, true, false);
    }

    /**
     * 创建中断结果。
     *
     * @return 中断结果
     */
    public static @NotNull ScriptResult interrupted() {
        return new ScriptResult(false, null, null, false, true);
    }

    /**
     * {@return 脚本是否成功执行}
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取脚本返回值。
     *
     * <p>仅当 {@link #isSuccess()} 为 {@code true} 时有意义。
     * JavaScript 的 {@code null} 和 {@code undefined} 都映射为 Java {@code null}。</p>
     *
     * @return 脚本返回值，可能为 {@code null}
     */
    public @Nullable Object getValue() {
        return value;
    }

    /**
     * 获取脚本执行过程中抛出的异常。
     *
     * <p>仅当 {@link #isSuccess()} 为 {@code false} 且非超时/中断时有意义。</p>
     *
     * @return 异常对象，若无异常则为 {@code null}
     */
    public @Nullable Throwable getError() {
        return error;
    }

    /**
     * {@return 脚本是否因超时被强制中断}
     */
    public boolean isTimeout() {
        return timeout;
    }

    /**
     * {@return 脚本是否被外部中断信号终止}
     */
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public String toString() {
        if (success) {
            return "ScriptResult{success, value=" + value + "}";
        }
        if (timeout) {
            return "ScriptResult{timeout}";
        }
        if (interrupted) {
            return "ScriptResult{interrupted}";
        }
        return "ScriptResult{error=" + (error != null ? error.getMessage() : "null") + "}";
    }
}
