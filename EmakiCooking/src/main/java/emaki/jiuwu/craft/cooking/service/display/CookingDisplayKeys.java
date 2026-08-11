package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.display.DisplayKey;

/**
 * 工位身份到 CoreLib {@link DisplayKey} 的映射。
 *
 * <p>把「工位类型 + 坐标 + 条目名」三元组收敛到一处，避免各调用点各自拼接字符串。
 * 命名空间固定为 {@code emakicooking}，分组用工位类型与坐标，与迁移前的
 * {@code folderName():runtimeKey()} 保持同样的分组粒度。
 */
public final class CookingDisplayKeys {

    /** 本模块在展示实体服务中的命名空间。 */
    public static final String NAMESPACE = "emakicooking";

    private CookingDisplayKeys() {
    }

    /** {@return 工位内某个展示条目的键} */
    public static DisplayKey of(StationType stationType, StationCoordinates coordinates, String displayKey) {
        return new DisplayKey(NAMESPACE, group(stationType, coordinates), displayKey);
    }

    /** {@return 工位分组标识，用于批量移除该工位的全部展示条目} */
    public static String group(StationType stationType, StationCoordinates coordinates) {
        return stationType.folderName() + ":" + coordinates.runtimeKey();
    }

    /** {@return 工位类型的分组前缀，用于按类型批量移除} */
    public static String typePrefix(StationType stationType) {
        return stationType.folderName() + ":";
    }
}
