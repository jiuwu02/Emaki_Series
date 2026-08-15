package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record BlockBreakGameplayEvent(Player player, Block block, boolean mature)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "block_break";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of("block_type", block.getType().name());
    }
}
