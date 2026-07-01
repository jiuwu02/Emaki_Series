package emaki.jiuwu.craft.cooking.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.cooking.api.event.CookingStationInteractEvent;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

/**
 * 最近交互工位记录表：监听 {@link CookingStationInteractEvent}，把每个玩家最后一次
 * 交互的工位（类型 + 坐标）写入内存表，供 {@link CookingStationLocator} 在解析工位
 * 占位符时定位。
 *
 * <p>记录只保留玩家最后一次交互，下一次交互直接覆盖；玩家退出时清除。是否命中由
 * {@link CookingStationLocator} 结合世界与距离校验决定，本表不做时间过期。</p>
 */
public final class CookingStationTracker implements Listener {

    private final Map<UUID, RecentStation> recentInteractions = new ConcurrentHashMap<>();

    /**
     * 监听工位交互事件，记录该玩家最近交互的工位。事件在 {@code dispatchInteraction}
     * 分发到各工位服务之前同步触发，因此写入完成后，后续配方条件求值即可读到当前工位。
     *
     * @param event 工位交互事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStationInteract(CookingStationInteractEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        StationType type = parseStationType(event.getStationType());
        if (type == null) {
            return;
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(location.getBlock());
        if (coordinates == null) {
            return;
        }
        recentInteractions.put(player.getUniqueId(), new RecentStation(type, coordinates));
    }

    /**
     * 玩家退出时清理其最近交互记录，避免离线玩家记录长期驻留。
     *
     * @param event 玩家退出事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            recentInteractions.remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * 返回玩家最近交互的工位记录。
     *
     * @param playerId 玩家 UUID，可为 null
     * @return 最近交互工位；没有记录时空
     */
    public Optional<RecentStation> recent(UUID playerId) {
        return playerId == null ? Optional.empty() : Optional.ofNullable(recentInteractions.get(playerId));
    }

    /** 清空全部记录（reload / 关服清理用）。 */
    public void clear() {
        recentInteractions.clear();
    }

    private StationType parseStationType(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (type.folderName().equals(folderName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 最近交互工位记录：工位类型与工作方块坐标。
     *
     * @param type        工位类型
     * @param coordinates 工作方块坐标
     */
    public record RecentStation(StationType type, StationCoordinates coordinates) {
    }
}
