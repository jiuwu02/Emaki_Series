package emaki.jiuwu.craft.corelib.packet;

import org.bukkit.Bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

/**
 * PacketEvents 的可用性与版本探测。
 *
 * <p>菜单发包后端与展示实体发包后端此前各自重复这套判定，收敛到一处避免不一致。
 * 所有方法都对 PacketEvents 缺失的情况做了兜底，不会抛 {@link LinkageError}。
 */
public final class PacketEventsSupport {

    /** 展示实体与容器封包所需的最低服务端版本。 */
    private static final ServerVersion MINIMUM_VERSION = ServerVersion.V_1_19_4;

    private static final String PLUGIN_NAME = "PacketEvents";

    private PacketEventsSupport() {
    }

    /** {@return PacketEvents 插件是否已启用} */
    public static boolean pluginPresent() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    /**
     * 探测服务端版本是否满足最低要求。
     *
     * @return 满足返回 {@code true}；PacketEvents 不可用或探测失败时返回 {@code false}
     */
    public static boolean versionSupported() {
        try {
            return PacketEvents.getAPI()
                    .getServerManager()
                    .getVersion()
                    .isNewerThanOrEquals(MINIMUM_VERSION);
        } catch (LinkageError | RuntimeException _) {
            return false;
        }
    }

    /** {@return 插件在位且版本满足要求} */
    public static boolean available() {
        return pluginPresent() && versionSupported();
    }

    /** {@return 最低要求版本的展示名，用于日志} */
    public static String minimumVersionName() {
        return MINIMUM_VERSION.name();
    }
}
