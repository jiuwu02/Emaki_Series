package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

/**
 * Inputs for a strengthen preview or attempt: the item being strengthened and
 * any material item stacks supplied for the operation.
 *
 * <p>All non-empty stacks are defensively cloned. Material list positions are
 * preserved exactly, including {@code null} placeholders, so callers can use
 * the list index as a stable GUI slot identity. Accessors return fresh defensive
 * copies and therefore cannot be used to mutate the stored attempt input.
 */
public final class AttemptContext {

    private final ItemStack targetItem;
    private final List<ItemStack> materialInputs;
    private final String operationId;

    /**
     * Creates a context without a caller-provided operation id.
     */
    public AttemptContext(ItemStack targetItem, List<ItemStack> materialInputs) {
        this(targetItem, materialInputs, "");
    }

    /**
     * Creates a context with an idempotency key for the operation.
     */
    public AttemptContext(ItemStack targetItem, List<ItemStack> materialInputs, String operationId) {
        this.targetItem = normalizeItem(targetItem);
        this.materialInputs = normalizeItems(materialInputs);
        this.operationId = operationId == null ? "" : operationId.trim();
    }

    public static AttemptContext of(ItemStack targetItem, List<ItemStack> materialInputs) {
        return new AttemptContext(targetItem, materialInputs);
    }

    public static AttemptContext of(ItemStack targetItem, List<ItemStack> materialInputs, String operationId) {
        return new AttemptContext(targetItem, materialInputs, operationId);
    }

    public ItemStack targetItem() {
        return normalizeItem(targetItem);
    }

    public List<ItemStack> materialInputs() {
        return normalizeItems(materialInputs);
    }

    /** {@return the caller-supplied idempotency key, or an empty string} */
    public String operationId() {
        return operationId;
    }

    /** {@return a defensive snapshot using the supplied operation id} */
    public AttemptContext withOperationId(String value) {
        return new AttemptContext(targetItem, materialInputs, value);
    }

    private static List<ItemStack> normalizeItems(List<ItemStack> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<ItemStack> normalized = new ArrayList<>(inputs.size());
        for (ItemStack itemStack : inputs) {
            normalized.add(normalizeItem(itemStack));
        }
        return Collections.unmodifiableList(normalized);
    }

    private static ItemStack normalizeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        return itemStack.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttemptContext context)) {
            return false;
        }
        return Objects.equals(targetItem, context.targetItem)
                && Objects.equals(materialInputs, context.materialInputs)
                && Objects.equals(operationId, context.operationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetItem, materialInputs, operationId);
    }

    @Override
    public String toString() {
        return "AttemptContext[targetItem=" + targetItem + ", materialInputs=" + materialInputs
                + ", operationId=" + operationId + "]";
    }
}
