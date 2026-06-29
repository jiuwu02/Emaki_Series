package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import net.Indyuce.mmoitems.api.event.item.ConsumableConsumedEvent;

/**
 * 监听 MMOItems 的 {@link ConsumableConsumedEvent}。MMOItems 的消耗品很多不是原版食物，
 * 不会触发原版 {@code PlayerItemConsumeEvent}，因此需要单独接入。
 *
 * <p>仅当服务器安装并启用 MMOItems 时才注册本监听器（由主类用 softdepend + {@code LinkageError} 隔离）。</p>
 */
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
