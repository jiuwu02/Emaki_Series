package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a player extracts a smelted result from a furnace.
 *
 * <p>The {@code result} stack is synthesized from the event's item type and (clamped) amount so
 * subscribers can reuse the same item-matching path as crafting. {@code amount} preserves the
 * raw extracted amount for the {@code result_amount} variable.
 *
 * @param player the extracting player
 * @param result the synthesized result stack (amount clamped to at least {@code 1})
 * @param amount the raw extracted amount
 */
public record FurnaceExtractGameplayEvent(Player player, ItemStack result, int amount)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "furnace_extract";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of(
                "result_type", result.getType().name(),
                "result_amount", amount);
    }
}
