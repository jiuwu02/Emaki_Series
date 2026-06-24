package emaki.jiuwu.craft.corelib.condition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 轻量运行时上下文，供 JavaScript 条件读取触发玩家、被操作物品和领域变量。
 *
 * <p>仅用于 JavaScript 条件评估的增量上下文注入；表达式条件不受影响。所有字段均可为
 * {@code null}/空，调用方按需提供。
 *
 * @param player    触发条件的玩家，可为 {@code null}
 * @param item      被操作的主物品，可为 {@code null}
 * @param variables 领域变量（如成功率、星级、配方 id 等），可为 {@code null}
 */
public record ConditionContext(Player player, ItemStack item, Map<String, Object> variables) {

    public ConditionContext {
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }

    /** 空上下文，等价于不注入任何运行时信息。 */
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
