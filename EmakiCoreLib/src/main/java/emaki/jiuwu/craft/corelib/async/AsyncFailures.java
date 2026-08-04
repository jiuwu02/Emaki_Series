package emaki.jiuwu.craft.corelib.async;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.async.AsyncFailures}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 3 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.async.AsyncFailures}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class AsyncFailures {

    private AsyncFailures() {
    }

    public static Throwable unwrap(Throwable throwable) {
        return emaki.jiuwu.craft.corelib.api.async.AsyncFailures.unwrap(throwable);
    }

    public static Throwable unwrapOnce(Throwable throwable) {
        return emaki.jiuwu.craft.corelib.api.async.AsyncFailures.unwrapOnce(throwable);
    }

    public static String describe(Throwable throwable) {
        return emaki.jiuwu.craft.corelib.api.async.AsyncFailures.describe(throwable);
    }
}
