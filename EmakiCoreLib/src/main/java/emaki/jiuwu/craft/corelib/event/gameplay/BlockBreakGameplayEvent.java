package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Fired when a player breaks a block.
 *
 * <p>{@code mature} is {@code true} when the block was a fully-grown
 * {@link org.bukkit.block.data.Ageable} crop, letting subscribers additionally treat the break
 * as a {@code crop_harvest} trigger without re-inspecting block data. The raw {@link Block} is
 * exposed for rule matching (type) and location-based anti-abuse checks.
 *
 * @param player the player who broke the block
 * @param block  the broken block
 * @param mature whether the block was a fully-grown crop
 */
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
