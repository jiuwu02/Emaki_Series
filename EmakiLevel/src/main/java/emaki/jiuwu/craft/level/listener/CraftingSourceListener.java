package emaki.jiuwu.craft.level.listener;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class CraftingSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;

    public CraftingSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        int amount = Math.max(1, result.getAmount());
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("craft_item")) {
            SourceRuleConfig.Rule rule = sourceService.matchItem(source, result);
            if (rule != null) {
                sourceService.award(player, source, rule, Map.of("result_amount", amount, "result_type", result.getType().name()), "craft_item");
            }
        }
    }
}
