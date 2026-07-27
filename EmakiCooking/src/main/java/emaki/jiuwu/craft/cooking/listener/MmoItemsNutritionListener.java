package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import net.Indyuce.mmoitems.api.event.item.ConsumableConsumedEvent;







public final class MmoItemsNutritionListener implements Listener {

    private final EmakiCookingPlugin plugin;

    public MmoItemsNutritionListener(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsumableConsumed(ConsumableConsumedEvent event) {
        if (plugin.nutritionService() == null || !plugin.nutritionService().enabled()) {
            return;
        }
        if (event.getUseItem() == null) {
            return;
        }
        plugin.nutritionService().applyFood(event.getPlayer(), event.getUseItem().getItem());
    }
}
