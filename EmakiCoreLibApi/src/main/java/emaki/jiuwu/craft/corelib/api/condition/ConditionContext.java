package emaki.jiuwu.craft.corelib.api.condition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;











/**
 * Evaluation context handed to condition checks: the acting player, the item under test and the
 * extra variables visible to placeholders while the check runs.
 *
 * <p>Every component is optional. The compact constructor copies {@code variables} into an
 * immutable map ({@code null} becomes an empty map), so later mutations of the caller's map are
 * not visible here. {@code player} and {@code item} may be {@code null}; the evaluator falls back
 * to {@link #EMPTY} when a caller passes a {@code null} context.
 *
 * <p>Following {@link Map#copyOf(Map)} semantics, a {@code null} key or value inside
 * {@code variables} makes the constructor throw a {@code NullPointerException}.
 *
 * @param player player the condition is evaluated for; may be {@code null}
 * @param item item the condition inspects; may be {@code null}
 * @param variables extra placeholder bindings; copied into an immutable map, never {@code null}
 */
public record ConditionContext(Player player, ItemStack item, Map<String, Object> variables) {

    /** Normalizes {@code variables} into an immutable map as described in the type documentation. */
    public ConditionContext {
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
    }


    /** Shared context with no player, no item and no variables; used as the null-context fallback. */
    public static final ConditionContext EMPTY = new ConditionContext(null, null, Map.of());

    /**
     * {@return a context bound to {@code player} without an item or variables}
     *
     * @param player player the condition is evaluated for; may be {@code null}
     */
    public static ConditionContext of(Player player) {
        return new ConditionContext(player, null, Map.of());
    }

    /**
     * {@return a context bound to {@code player} and {@code item} without variables}
     *
     * @param player player the condition is evaluated for; may be {@code null}
     * @param item item the condition inspects; may be {@code null}
     */
    public static ConditionContext of(Player player, ItemStack item) {
        return new ConditionContext(player, item, Map.of());
    }

    /**
     * {@return a context bound to {@code player}, {@code item} and the given variables}
     *
     * @param player player the condition is evaluated for; may be {@code null}
     * @param item item the condition inspects; may be {@code null}
     * @param variables placeholder bindings, copied into an immutable map; {@code null} becomes
     *                  an empty map
     */
    public static ConditionContext of(Player player, ItemStack item, Map<String, Object> variables) {
        return new ConditionContext(player, item, variables);
    }

    /**
     * {@return {@code true} when there is no player, no item and no variable binding, which is
     * exactly the shape of {@link #EMPTY}}
     */
    public boolean isEmpty() {
        return player == null && item == null && variables.isEmpty();
    }
}
