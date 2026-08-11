package emaki.jiuwu.craft.item.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Result of refreshing one EmakiItem stack against its current definition.
 *
 * <p>EmakiItem's internal refresh returns the original stack for six different reasons — already up to
 * date, refresh disabled in configuration, not an EmakiItem item, unknown definition, damaged ledger, or
 * an actual rebuild that produced identical output. This type separates "nothing needed doing" from
 * "rebuilt", so a caller can tell whether to write the stack back.
 *
 * @param stack   the resulting stack; the original instance when nothing changed
 * @param changed whether the stack was actually rebuilt
 */
public record ItemRefreshOutcome(@NotNull ItemStack stack, boolean changed) {

    /**
     * Requires a stack.
     *
     * @param stack   resulting stack
     * @param changed whether a rebuild happened
     * @throws NullPointerException when {@code stack} is {@code null}
     */
    public ItemRefreshOutcome {
        if (stack == null) {
            throw new NullPointerException("stack");
        }
    }

    /**
     * Creates an outcome describing a stack that needed no change.
     *
     * @param stack the unchanged stack
     * @return the outcome
     */
    public static @NotNull ItemRefreshOutcome unchanged(@NotNull ItemStack stack) {
        return new ItemRefreshOutcome(stack, false);
    }

    /**
     * Creates an outcome describing a rebuilt stack.
     *
     * @param stack the rebuilt stack
     * @return the outcome
     */
    public static @NotNull ItemRefreshOutcome rebuilt(@NotNull ItemStack stack) {
        return new ItemRefreshOutcome(stack, true);
    }
}
