package emaki.jiuwu.craft.corelib.math;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.math.Numbers}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具改由 {@code emaki-corelib-api} 契约 artifact 提供。
 * 此处保留全部 13 个方法签名并逐一委托，旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.math.Numbers}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class Numbers {

    private Numbers() {
    }

    public static Integer tryParseInt(Object value, Integer defaultValue) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.tryParseInt(value, defaultValue);
    }

    public static Long tryParseLong(Object value, Long defaultValue) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.tryParseLong(value, defaultValue);
    }

    public static Double tryParseDouble(Object value, Double defaultValue) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.tryParseDouble(value, defaultValue);
    }

    public static boolean isNumeric(Object value) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.isNumeric(value);
    }

    public static int clamp(int value, int min, int max) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.clamp(value, min, max);
    }

    public static double clamp(double value, double min, double max) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.clamp(value, min, max);
    }

    public static String toPlainString(double value) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.toPlainString(value);
    }

    public static String formatNumber(double value, String pattern) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.formatNumber(value, pattern);
    }

    public static int roundToInt(double value) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.roundToInt(value);
    }

    public static int floor(double value) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.floor(value);
    }

    public static int ceil(double value) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.ceil(value);
    }

    public static double safeDivide(double numerator, double denominator, double defaultValue) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.safeDivide(numerator, denominator, defaultValue);
    }

    public static Double parsePercentage(Object value) {
        return emaki.jiuwu.craft.corelib.api.math.Numbers.parsePercentage(value);
    }
}
