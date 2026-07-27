package emaki.jiuwu.craft.corelib.api.dialog;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 原版对话框的公开静态门面。
 *
 * <p>对话框定义由服主写在 EmakiCoreLib 的对话框目录中，业务插件只需按 id 展示。
 * EmakiCoreLib 在启用时安装桥接实现，停用时移除。
 *
 * <p>展示对话框需要客户端 1.21.6 及以上；低版本客户端的表现未经验证。
 */
public final class DialogApi {

    private static volatile Bridge bridge;

    private DialogApi() {
    }

    /**
     * 安装桥接实现，仅供 EmakiCoreLib 生命周期调用。
     *
     * @param bridge EmakiCoreLib 提供的桥接实现
     */
    public static void install(@NotNull Bridge bridge) {
        DialogApi.bridge = bridge;
    }

    /**
     * 在仍为当前桥接时移除它。
     *
     * @param bridge 待移除的桥接；不是当前桥接时忽略
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (DialogApi.bridge == bridge) {
            DialogApi.bridge = null;
        }
    }

    /** {@return 对话框能力是否可用} */
    public static boolean available() {
        Bridge resolved = bridge;
        return resolved != null && resolved.enabled();
    }

    /** {@return 已加载的对话框 id 集合；不可用时返回空集合} */
    public static @NotNull Collection<String> dialogIds() {
        Bridge resolved = bridge;
        return resolved == null ? java.util.List.of() : resolved.dialogIds();
    }

    /**
     * 判断指定 id 的对话框是否已加载。
     *
     * @param dialogId 对话框 id
     * @return 已加载返回 {@code true}
     */
    public static boolean contains(@Nullable String dialogId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.contains(dialogId);
    }

    /**
     * 向玩家展示对话框。必须在该玩家的所有者线程调用。
     *
     * @param player   目标玩家
     * @param dialogId 对话框 id
     * @return 成功展示返回 {@code true}；能力不可用、id 不存在或玩家为空时返回 {@code false}
     */
    public static boolean show(@Nullable Player player, @Nullable String dialogId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.show(player, dialogId);
    }

    /**
     * 关闭玩家当前的对话框。必须在该玩家的所有者线程调用。
     *
     * @param player 目标玩家
     * @return 已发出关闭请求返回 {@code true}
     */
    public static boolean close(@Nullable Player player) {
        Bridge resolved = bridge;
        return resolved != null && resolved.close(player);
    }

    /** EmakiCoreLib 提供的对话框桥接契约。 */
    public interface Bridge {

        /** {@return 对话框功能是否已启用} */
        boolean enabled();

        /** {@return 已加载的对话框 id 集合} */
        @NotNull
        Collection<String> dialogIds();

        /**
         * @param dialogId 对话框 id
         * @return 该 id 是否已加载
         */
        boolean contains(@Nullable String dialogId);

        /**
         * @param player   目标玩家
         * @param dialogId 对话框 id
         * @return 是否成功展示
         */
        boolean show(@Nullable Player player, @Nullable String dialogId);

        /**
         * @param player 目标玩家
         * @return 是否已发出关闭请求
         */
        boolean close(@Nullable Player player);
    }
}
