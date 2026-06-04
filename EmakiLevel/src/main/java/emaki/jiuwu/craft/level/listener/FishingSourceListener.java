package emaki.jiuwu.craft.level.listener;

import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class FishingSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;

    public FishingSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        String state = event.getState().name();
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("player_fish")) {
            SourceRuleConfig.Rule rule = sourceService.matchState(source, state);
            if (rule != null) {
                sourceService.award(event.getPlayer(), source, rule, Map.of("fish_state", state), "player_fish");
            }
        }
    }
}
