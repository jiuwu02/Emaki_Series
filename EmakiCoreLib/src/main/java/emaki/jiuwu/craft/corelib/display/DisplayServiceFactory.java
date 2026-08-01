package emaki.jiuwu.craft.corelib.display;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.display.bukkit.BukkitItemDisplayService;
import emaki.jiuwu.craft.corelib.display.bukkit.BukkitTextDisplayService;
import emaki.jiuwu.craft.corelib.display.packet.PacketItemDisplayService;
import emaki.jiuwu.craft.corelib.display.packet.PacketTextDisplayService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 展示实体服务的工厂。
 *
 * <p>公开而非仅供 CoreLib 内部使用，因为各模块可能需要用自己的
 * {@link DisplayRuntimeSettings} 与后端覆盖建立独立实例。
 * 没有特殊需求的调用方应直接取用 CoreLib 的默认实例。
 *
 * <p>后端取值：{@code bukkit} 真实体、{@code packet} 发包虚拟实体、
 * {@code auto} 有 PacketEvents 就用发包。历史值 {@code packet_events} 等价于 {@code packet}。
 * 发包后端不可用时一律回退真实体，不会导致功能整体失效。
 */
public final class DisplayServiceFactory {

    public static final String BACKEND_BUKKIT = "bukkit";
    public static final String BACKEND_PACKET = "packet";
    public static final String BACKEND_AUTO = "auto";

    private static final String PACKET_EVENTS_PLUGIN = "PacketEvents";

    private DisplayServiceFactory() {
    }

    /** {@return 规范化后的后端名；无法识别时返回 {@code auto}} */
    public static String normalizeBackend(String raw) {
        if (Texts.isBlank(raw)) {
            return BACKEND_AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case BACKEND_BUKKIT -> BACKEND_BUKKIT;
            case BACKEND_PACKET, "packet_events", "packetevents" -> BACKEND_PACKET;
            default -> BACKEND_AUTO;
        };
    }

    /**
     * 创建文本展示服务。
     *
     * @param owner       负责该实例生命周期的插件，也决定 {@code shutdown} 由谁调用
     * @param backendName 后端名，见类注释
     * @param settings    发包后端所需参数；真实体后端忽略
     * @param dispatcher  线程调度器
     */
    public static TextDisplayService createTextService(Plugin owner,
            String backendName,
            DisplayRuntimeSettings settings,
            ExecutionDispatcher dispatcher) {
        DisplayRuntimeSettings safeSettings = settings == null
                ? DisplayRuntimeSettings.of(48D, 20)
                : settings;
        if (!usePacket(owner, normalizeBackend(backendName))) {
            return new BukkitTextDisplayService(owner, dispatcher);
        }
        try {
            return new PacketTextDisplayService(owner, safeSettings, dispatcher);
        } catch (LinkageError | RuntimeException exception) {
            owner.getLogger().warning("[display] Could not start the packet text backend, "
                    + "falling back to real entities: " + exception.getMessage());
            return new BukkitTextDisplayService(owner, dispatcher);
        }
    }

    /** 创建物品展示服务，参数语义同 {@link #createTextService}。 */
    public static ItemDisplayService createItemService(Plugin owner,
            String backendName,
            DisplayRuntimeSettings settings,
            ExecutionDispatcher dispatcher) {
        DisplayRuntimeSettings safeSettings = settings == null
                ? DisplayRuntimeSettings.of(48D, 20)
                : settings;
        if (!usePacket(owner, normalizeBackend(backendName))) {
            return new BukkitItemDisplayService(owner, dispatcher);
        }
        try {
            return new PacketItemDisplayService(owner, safeSettings, dispatcher);
        } catch (LinkageError | RuntimeException exception) {
            owner.getLogger().warning("[display] Could not start the packet item backend, "
                    + "falling back to real entities: " + exception.getMessage());
            return new BukkitItemDisplayService(owner, dispatcher);
        }
    }

    private static boolean usePacket(Plugin owner, String backend) {
        if (BACKEND_BUKKIT.equals(backend)) {
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled(PACKET_EVENTS_PLUGIN)) {
            if (BACKEND_PACKET.equals(backend)) {
                owner.getLogger().warning("[display] The packet backend needs PacketEvents installed, "
                        + "falling back to real entities.");
            }
            return false;
        }
        return true;
    }
}
