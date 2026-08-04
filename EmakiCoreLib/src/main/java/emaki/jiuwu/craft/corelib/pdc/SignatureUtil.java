package emaki.jiuwu.craft.corelib.pdc;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 5 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class SignatureUtil {

    private SignatureUtil() {
    }

    public static String sha256(String value) {
        return emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil.sha256(value);
    }

    public static String stableSignature(Object value) {
        return emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil.stableSignature(value);
    }

    public static String stableSignature(Map<String, ?> values) {
        return emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil.stableSignature(values);
    }

    public static String stableSignature(Collection<?> values) {
        return emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil.stableSignature(values);
    }

    public static String combine(String... values) {
        return emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil.combine(values);
    }
}
