package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Fired when a player places a block.
 *
 * <p>Emitted primarily so anti-abuse consumers (e.g. EmakiLevel) can remember player-placed
 * blocks and later decline to reward breaking them, without maintaining their own
 * {@link org.bukkit.event.block.BlockPlaceEvent} listener. The raw placed {@link Block} is
 * exposed for location keying and type matching.
 *
 * @param player the placing player
 * @param block  the placed block
 */
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
