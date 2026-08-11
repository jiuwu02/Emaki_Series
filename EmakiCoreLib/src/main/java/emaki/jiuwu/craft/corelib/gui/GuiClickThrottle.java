package emaki.jiuwu.craft.corelib.gui;

/**
 * 菜单点击节流的全局设置。
 *
 * <p>点击入口分散在 Bukkit 后端（{@link GuiService}）与发包后端
 * （{@code PacketGuiBackend}）两处，且 {@code GuiService} 由各业务插件自行创建，
 * 因此间隔值由 CoreLib 在启用与重载时统一写入这里，两条后端共同读取。
 * 每个玩家的上次点击时间记录在 {@link GuiSession} 上，随会话关闭自动回收。
 */
public final class GuiClickThrottle {

    private static volatile int intervalMs;

    private GuiClickThrottle() {
    }

    /**
     * 设置同一玩家两次菜单点击的最小间隔（毫秒）。小于等于 0 表示不限制。
     * 由 CoreLib 依据 {@code gui.click_interval_ms} 调用。
     */
    public static void configureIntervalMs(int millis) {
        intervalMs = Math.max(0, millis);
    }

    public static int intervalMs() {
        return intervalMs;
    }

    /**
     * 判断该会话本次点击是否应被处理。间隔不足时返回 {@code false}，
     * 调用方必须丢弃业务回调，但仍要完成各自后端的取消与同步收尾。
     */
    public static boolean allow(GuiSession session) {
        if (session == null) {
            return true;
        }
        return session.tryConsumeClick(intervalMs);
    }
}
