package emaki.jiuwu.craft.storage.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.config.AutoPickupConfig;

/**
 * {@code ON_PICKUP} 模式的自动拾取入口。
 *
 * <p>事件在拾取者所在区域线程触发，写入目标又是同一个玩家，因此这里天然处于
 * 正确的所有者线程，无需额外调度。
 *
 * <p>只在整单转入成功时取消事件；其余情况完全放行，交由原版拾取进背包。
 */
public final class StorageAutoPickupListener implements Listener {

    private final EmakiStoragePlugin plugin;

    public StorageAutoPickupListener(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        AutoPickupConfig config = plugin.appConfig() == null ? null : plugin.appConfig().autoPickup();
        if (config == null || !config.enabled() || config.radiusMode()) {
            return;
        }
        if (plugin.autoPickupService() == null) {
            return;
        }
        if (plugin.autoPickupService().tryDepositAll(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }
}
