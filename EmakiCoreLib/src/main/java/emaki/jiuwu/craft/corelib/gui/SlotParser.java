package emaki.jiuwu.craft.corelib.gui;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.gui.SlotParser}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 3 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.gui.SlotParser}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class SlotParser {

    private SlotParser() {
    }

    public static List<Integer> parse(Object raw) {
        return emaki.jiuwu.craft.corelib.api.gui.SlotParser.parse(raw);
    }

    public static boolean isValidSlot(Integer slot, int rows) {
        return emaki.jiuwu.craft.corelib.api.gui.SlotParser.isValidSlot(slot, rows);
    }

    public static List<Integer> borderSlots(int rows) {
        return emaki.jiuwu.craft.corelib.api.gui.SlotParser.borderSlots(rows);
    }
}
