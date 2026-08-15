package emaki.jiuwu.craft.storage.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.config.AutoPickupConfig;

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
