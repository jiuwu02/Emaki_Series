package emaki.jiuwu.craft.corelib.api.condition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;











public record ConditionContext(Player player, ItemStack item, Map<String, Object> variables) {

    public ConditionContext {
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }


    public static final ConditionContext EMPTY = new ConditionContext(null, null, Map.of());

    public static ConditionContext of(Player player) {
        return new ConditionContext(player, null, Map.of());
    }

    public static ConditionContext of(Player player, ItemStack item) {
        return new ConditionContext(player, item, Map.of());
    }

    public static ConditionContext of(Player player, ItemStack item, Map<String, Object> variables) {
        return new ConditionContext(player, item, variables);
    }

    public boolean isEmpty() {
        return player == null && item == null && variables.isEmpty();
    }
}
