package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a player crafts an item via a crafting-table recipe. The {@code result} stack is
 * exposed for rule matching; {@code result_amount} in {@link #variables()} is clamped to at
 * least {@code 1}.
 *
 * @param player the crafting player
 * @param result the recipe result stack
 */
public record CraftGameplayEvent(Player player, ItemStack result)
        implements GameplayEvent {

    @Override
    public String triggerKey() {
        return "craft_item";
    }

    @Override
    public Map<String, Object> variables() {
        return Map.of(
                "result_type", result.getType().name(),
                "result_amount", Math.max(1, result.getAmount()));
    }
}
