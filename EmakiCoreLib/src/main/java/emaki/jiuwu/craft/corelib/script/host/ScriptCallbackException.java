package emaki.jiuwu.craft.corelib.script.host;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScriptCallbackException extends Exception {

    private final boolean timeout;
    private final boolean interrupted;

    private ScriptCallbackException(@NotNull String message, @Nullable Throwable cause,
                                    boolean timeout, boolean interrupted) {
        super(message, cause);
        this.timeout = timeout;
        this.interrupted = interrupted;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public static @NotNull ScriptCallbackException timeout(@NotNull Throwable cause) {
        return new ScriptCallbackException("脚本回调超时", cause, true, false);
    }

    public static @NotNull ScriptCallbackException interrupted() {
        return new ScriptCallbackException("脚本回调线程被中断", null, false, true);
    }

    public static @NotNull ScriptCallbackException error(@NotNull Throwable cause) {
        String message = cause.getMessage();
        return new ScriptCallbackException(message != null && !message.isBlank() ? message : cause.toString(),
                cause, false, false);
    }
}
