package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;

/**
 * 玩家上下线时加载 / 保存营养数据。
 */
public final class NutritionPlayerDataListener implements Listener {

    private final EmakiCookingPlugin plugin;

    public NutritionPlayerDataListener(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.nutritionService() == null) {
            return;
        }
        plugin.nutritionDataStore().load(event.getPlayer(), plugin.nutritionTypeRegistry().asMap());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.nutritionService() == null) {
            return;
        }
        plugin.nutritionDataStore().unload(event.getPlayer().getUniqueId(), true);
        plugin.nutritionService().handleQuit(event.getPlayer().getUniqueId());
    }
}
