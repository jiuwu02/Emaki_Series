package emaki.jiuwu.craft.level.listener;

import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class SmeltingSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;

    public SmeltingSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExtract(FurnaceExtractEvent event) {
        ItemStack result = new ItemStack(event.getItemType(), Math.max(1, event.getItemAmount()));
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("furnace_extract")) {
            SourceRuleConfig.Rule rule = sourceService.matchItem(source, result);
            if (rule != null) {
                sourceService.award(event.getPlayer(), source, rule, Map.of("result_amount", event.getItemAmount(), "result_type", event.getItemType().name()), "furnace_extract");
            }
        }
    }
}
