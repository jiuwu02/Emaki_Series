package emaki.jiuwu.craft.corelib.item;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.util.Locale;


/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 3 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class EquipmentSlotMatcher {

    private EquipmentSlotMatcher() {
    }

    public static String normalizeRequired(String slot) {
        return emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher.normalizeRequired(slot);
    }

    public static String normalizeActual(String slot) {
        return emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher.normalizeActual(slot);
    }

    public static boolean matches(String actualSlot, String requiredSlot) {
        return emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher.matches(actualSlot, requiredSlot);
    }
}
