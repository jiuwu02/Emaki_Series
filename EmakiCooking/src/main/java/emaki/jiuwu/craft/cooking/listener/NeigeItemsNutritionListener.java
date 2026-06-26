package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import pers.neige.neigeitems.event.ItemActionEvent;
import pers.neige.neigeitems.item.action.ItemActionType;

/**
 * 监听 NeigeItems 的 {@link ItemActionEvent}，仅处理 {@link ItemActionType#EAT} 食用触发。
 * NeigeItems 的物品多数不是原版食物，不会触发原版 {@code PlayerItemConsumeEvent}，需单独接入。
 *
 * <p>仅当服务器安装并启用 NeigeItems 时才注册本监听器（由主类用 softdepend + {@code LinkageError} 隔离）。</p>
 */
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
