package emaki.jiuwu.craft.corelib.event.gameplay;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
