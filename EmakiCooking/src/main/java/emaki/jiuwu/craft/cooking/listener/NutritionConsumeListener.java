package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;







public final class NutritionConsumeListener implements Listener {

    private final EmakiCookingPlugin plugin;

    public NutritionConsumeListener(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.nutritionService() == null || !plugin.nutritionService().enabled()) {
            return;
        }
        plugin.nutritionService().applyFood(event.getPlayer(), event.getItem());
    }
}
