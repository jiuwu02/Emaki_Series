package emaki.jiuwu.craft.cooking.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;

/**
 * 监听原版 {@link PlayerItemConsumeEvent}。覆盖原版食物，以及 CraftEngine / ItemsAdder / Nexo
 * 等通过原版 food component 实现的自定义食物（它们吃下时仍触发原版事件）。
 *
 * <p>物品来源由 CoreLib {@code ItemSourceService.identifyItem(...)} 统一识别。</p>
 */
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
