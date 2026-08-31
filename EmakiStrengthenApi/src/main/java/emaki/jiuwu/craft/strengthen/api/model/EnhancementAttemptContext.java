package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Public input for one generic enhancement attempt.
 *
 * <p>The target and material stacks are defensively copied so callers can keep their GUI/session state
 * authoritative until the returned outcome is applied.
 */
public record EnhancementAttemptContext(@NotNull String recipeId,
        @Nullable ItemStack targetItem,
        @NotNull List<ItemStack> materialInputs,
        @NotNull String operationId) {

    public EnhancementAttemptContext {
        recipeId = recipeId == null ? "" : recipeId.trim();
        targetItem = cloneItem(targetItem);
        materialInputs = copyItems(materialInputs);
        operationId = operationId == null ? "" : operationId.trim();
    }

    public static @NotNull EnhancementAttemptContext of(@NotNull String recipeId,
            @Nullable ItemStack targetItem,
            @Nullable List<ItemStack> materialInputs,
            @Nullable String operationId) {
        return new EnhancementAttemptContext(recipeId, targetItem,
                materialInputs == null ? List.of() : materialInputs,
                operationId == null ? "" : operationId);
    }

    public @NotNull EnhancementAttemptContext withOperationId(@Nullable String operationId) {
        return new EnhancementAttemptContext(recipeId, targetItem, materialInputs,
                operationId == null ? "" : operationId);
    }

    private static @Nullable ItemStack cloneItem(@Nullable ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? null : itemStack.clone();
    }

    private static @NotNull List<ItemStack> copyItems(@Nullable List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            copy.add(cloneItem(item));
        }
        return Collections.unmodifiableList(copy);
    }
}
