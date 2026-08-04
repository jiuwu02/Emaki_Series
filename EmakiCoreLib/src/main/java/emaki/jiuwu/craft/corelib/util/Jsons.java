package emaki.jiuwu.craft.corelib.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.util.Jsons}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 5 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.util.Jsons}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class Jsons {

    private Jsons() {
    }

    public static String stringify(Object value) {
        return emaki.jiuwu.craft.corelib.api.util.Jsons.stringify(value);
    }

    public static String quote(String value) {
        return emaki.jiuwu.craft.corelib.api.util.Jsons.quote(value);
    }

    public static String extractString(String json, String key) {
        return emaki.jiuwu.craft.corelib.api.util.Jsons.extractString(json, key);
    }

    public static Object extractValue(String json, String key) {
        return emaki.jiuwu.craft.corelib.api.util.Jsons.extractValue(json, key);
    }

    public static Object parse(String json) {
        return emaki.jiuwu.craft.corelib.api.util.Jsons.parse(json);
    }
}
