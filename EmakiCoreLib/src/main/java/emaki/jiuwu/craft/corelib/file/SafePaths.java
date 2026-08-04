package emaki.jiuwu.craft.corelib.file;

import java.nio.file.Path;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.file.SafePaths}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 1 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.file.SafePaths}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class SafePaths {

    private SafePaths() {
    }

    public static Path resolveInside(Path root, String relativePath) {
        return emaki.jiuwu.craft.corelib.api.file.SafePaths.resolveInside(root, relativePath);
    }
}
