package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record BlockPlaceGameplayEvent(Player player, Block block)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "block_place";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("block_type", block.getType().name());
    }
}
