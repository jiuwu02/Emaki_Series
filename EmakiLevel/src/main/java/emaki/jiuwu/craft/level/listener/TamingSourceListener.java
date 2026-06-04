package emaki.jiuwu.craft.level.listener;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class TamingSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;

    public TamingSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) {
            return;
        }
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("entity_tame")) {
            SourceRuleConfig.Rule rule = sourceService.matchEntity(source, event.getEntityType());
            if (rule != null) {
                sourceService.award(player, source, rule, Map.of("entity_type", event.getEntityType().name()), "entity_tame");
            }
        }
    }
}
