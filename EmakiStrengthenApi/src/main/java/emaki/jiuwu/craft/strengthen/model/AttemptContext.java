package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

/**
 * Inputs for a strengthen preview or attempt: the item being strengthened and
 * any material item stacks supplied for the operation.
 *
 * <p>All stacks are defensively cloned and air/empty stacks are dropped on
 * construction, so the record never exposes mutable or empty inputs.
 *
 * @param targetItem     the item to strengthen; {@code null} when absent
 * @param materialInputs the supplied material stacks; never {@code null}
 */
public record AttemptContext(ItemStack targetItem, List<ItemStack> materialInputs) {

    /** Canonical constructor; clones inputs and drops air/empty stacks. */
    public AttemptContext {
        targetItem = normalizeItem(targetItem);
        if (materialInputs == null || materialInputs.isEmpty()) {
            materialInputs = List.of();
        } else {
            List<ItemStack> normalized = new ArrayList<>(materialInputs.size());
            for (ItemStack itemStack : materialInputs) {
                ItemStack cloned = normalizeItem(itemStack);
                if (cloned != null) {
                    normalized.add(cloned);
                }
            }
            materialInputs = List.copyOf(normalized);
        }
    }

    /**
     * Creates a context from a target item and material inputs.
     *
     * @param targetItem     the item to strengthen
     * @param materialInputs the supplied material stacks
     * @return the new context
     */
    public static AttemptContext of(ItemStack targetItem, List<ItemStack> materialInputs) {
        return new AttemptContext(targetItem, materialInputs);
    }

    private static ItemStack normalizeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return itemStack.clone();
    }
}
