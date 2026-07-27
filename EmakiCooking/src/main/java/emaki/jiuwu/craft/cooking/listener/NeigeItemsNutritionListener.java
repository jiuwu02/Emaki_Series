package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import pers.neige.neigeitems.event.ItemActionEvent;
import pers.neige.neigeitems.item.action.ItemActionType;







public final class NeigeItemsNutritionListener implements Listener {

    private final EmakiCookingPlugin plugin;

    public NeigeItemsNutritionListener(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemAction(ItemActionEvent event) {
        if (plugin.nutritionService() == null || !plugin.nutritionService().enabled()) {
            return;
        }
        if (event.getType() != ItemActionType.EAT) {
            return;
        }
        plugin.nutritionService().applyFood(event.getPlayer(), event.getItemStack());
    }
}
