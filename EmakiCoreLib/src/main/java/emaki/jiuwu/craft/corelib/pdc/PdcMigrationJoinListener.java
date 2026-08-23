package emaki.jiuwu.craft.corelib.pdc;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import emaki.jiuwu.craft.corelib.api.pdc.PdcKeyMigration;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 玩家加入时把其 PDC 上的历史带点键转换为扁平键。
 *
 * <p>为什么不能只靠读取路径的懒转换：
 * <ul>
 *   <li>只写不读的镜像键（如 EmakiLevel 的 {@code player.*}）永远不会触发懒转换，
 *       老键会长期滞留，PAPI 可能同时看到新老两份；</li>
 *   <li>玩家资源按 {@code resourceId} 分区，没被读到的资源不会转换。</li>
 * </ul>
 *
 * <p>玩家的 {@code PersistentDataContainer} 是活容器，改动直接生效，无需回写。
 * 单个玩家容器的键数量是常数级，放在 {@link EventPriority#MONITOR} 上不影响加入耗时。
 */
public final class PdcMigrationJoinListener implements Listener {

    private final Supplier<DebugLogger> debugLoggerSupplier;

    public PdcMigrationJoinListener(Supplier<DebugLogger> debugLoggerSupplier) {
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        int migrated = PdcKeyMigration.migrateAll(event.getPlayer().getPersistentDataContainer(), false);
        if (migrated <= 0) {
            return;
        }
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("pdc", event.getPlayer())) {
            return;
        }
        debugLogger.log("pdc", event.getPlayer(), "pdc.join_migrated", Map.of(
                "player", event.getPlayer().getName(),
                "keys", String.valueOf(migrated)
        ));
    }
}
