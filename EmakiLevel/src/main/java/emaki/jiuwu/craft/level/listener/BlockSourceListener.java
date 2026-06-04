package emaki.jiuwu.craft.level.listener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

public final class BlockSourceListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;
    private final Set<String> placedBlocks = ConcurrentHashMap.newKeySet();

    public BlockSourceListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.appConfig().placedBlockTracking()) {
            placedBlocks.add(key(event.getBlockPlaced().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean playerPlaced = placedBlocks.remove(key(block.getLocation()));
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("block_break")) {
            if ((source.ignorePlayerPlacedBlocks() || !plugin.appConfig().placedBlockExp()) && playerPlaced) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchBlock(source, block.getType());
            if (rule != null) {
                sourceService.award(event.getPlayer(), source, rule, Map.of("block_type", block.getType().name()), "block_break");
            }
        }
        if (isMature(block)) {
            for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("crop_harvest")) {
                SourceRuleConfig.Rule rule = sourceService.matchBlock(source, block.getType());
                if (rule != null) {
                    sourceService.award(event.getPlayer(), source, rule, Map.of("block_type", block.getType().name()), "crop_harvest");
                }
            }
        }
    }

    private boolean isMature(Block block) {
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    private String key(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
