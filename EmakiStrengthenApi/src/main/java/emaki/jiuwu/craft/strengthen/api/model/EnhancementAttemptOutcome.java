package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Public result payload for a generic enhancement attempt.
 *
 * <p>Committed failures are valid completed attempts: costs and materials were consumed, but the target
 * did not advance. Rejected attempts are returned as {@code EmakiResult.Failure} instead of this payload.
 */
public record EnhancementAttemptOutcome(@NotNull AttemptOutcome outcome,
        boolean success,
        @NotNull String recipeId,
        @Nullable ItemStack resultItem,
        @NotNull List<ItemStack> materialInputs,
        int previousLevel,
        int resultingLevel,
        double successRate,
        int pityCounter,
        boolean pityTriggered,
        @NotNull String operationId,
        @NotNull Map<String, Object> replacements,
        @NotNull EnhancementPityResult pityResult) {

    public EnhancementAttemptOutcome {
        outcome = outcome == null ? AttemptOutcome.NOT_COMMITTED : outcome;
        recipeId = recipeId == null ? "" : recipeId;
        resultItem = cloneItem(resultItem);
        materialInputs = copyItems(materialInputs);
        operationId = operationId == null ? "" : operationId;
        replacements = replacements == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(replacements));
        pityResult = pityResult == null ? EnhancementPityResult.empty() : pityResult;
    }

    /**
     * Compatibility constructor retaining the original scalar pity payload.
     */
    public EnhancementAttemptOutcome(@NotNull AttemptOutcome outcome,
            boolean success,
            @NotNull String recipeId,
            @Nullable ItemStack resultItem,
            @NotNull List<ItemStack> materialInputs,
            int previousLevel,
            int resultingLevel,
            double successRate,
            int pityCounter,
            boolean pityTriggered,
            @NotNull String operationId,
            @NotNull Map<String, Object> replacements) {
        this(outcome, success, recipeId, resultItem, materialInputs, previousLevel, resultingLevel,
                successRate, pityCounter, pityTriggered, operationId, replacements,
                EnhancementPityResult.empty());
    }

    /**
     * Constructs an outcome from the multi-track pity payload and derives the legacy scalar fields.
     */
    public EnhancementAttemptOutcome(@NotNull AttemptOutcome outcome,
            boolean success,
            @NotNull String recipeId,
            @Nullable ItemStack resultItem,
            @NotNull List<ItemStack> materialInputs,
            int previousLevel,
            int resultingLevel,
            double successRate,
            @NotNull EnhancementPityResult pityResult,
            @NotNull String operationId,
            @NotNull Map<String, Object> replacements) {
        this(outcome, success, recipeId, resultItem, materialInputs, previousLevel, resultingLevel,
                successRate, pityResult == null ? 0 : pityResult.primaryCounter(),
                pityResult != null && pityResult.triggered(), operationId, replacements, pityResult);
    }

    public boolean committed() {
        return outcome == AttemptOutcome.COMMITTED_SUCCESS || outcome == AttemptOutcome.COMMITTED_FAILURE;
    }

    public boolean compensationPending() {
        return outcome == AttemptOutcome.COMPENSATION_PENDING;
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
