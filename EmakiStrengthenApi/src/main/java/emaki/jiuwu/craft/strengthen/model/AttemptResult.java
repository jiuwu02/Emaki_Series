package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a strengthen attempt.
 *
 * <p>{@link #success()} describes the strengthen roll. {@link #committed()} and
 * {@link #outcome()} explicitly describe whether externally visible state was
 * committed. These meanings intentionally remain separate: a failed roll can be
 * a committed attempt, while an early validation failure is not committed.
 */
public final class AttemptResult {

    private final boolean success;
    private final String errorKey;
    private final Map<String, Object> replacements;
    private final AttemptPreview preview;
    private final ItemStack resultItem;
    private final int resultingStar;
    private final int resultingCrack;
    private final Set<Integer> newlyReachedStars;
    private final String operationId;
    private final AttemptOutcome outcome;

    /**
     * Compatibility constructor. Outcome is derived from the existing fields.
     */
    public AttemptResult(boolean success,
            String errorKey,
            Map<String, Object> replacements,
            AttemptPreview preview,
            ItemStack resultItem,
            int resultingStar,
            int resultingCrack,
            Set<Integer> newlyReachedStars) {
        this(success, errorKey, replacements, preview, resultItem, resultingStar, resultingCrack,
                newlyReachedStars, "", resultItem == null
                        ? AttemptOutcome.NOT_COMMITTED
                        : success ? AttemptOutcome.COMMITTED_SUCCESS : AttemptOutcome.COMMITTED_FAILURE);
    }

    /**
     * Creates a result with explicit operation and commit semantics.
     */
    public AttemptResult(boolean success,
            String errorKey,
            Map<String, Object> replacements,
            AttemptPreview preview,
            ItemStack resultItem,
            int resultingStar,
            int resultingCrack,
            Set<Integer> newlyReachedStars,
            String operationId,
            AttemptOutcome outcome) {
        this.success = success;
        this.errorKey = errorKey == null ? "" : errorKey;
        this.replacements = immutableMap(replacements);
        this.preview = preview;
        this.resultItem = cloneItem(resultItem);
        this.resultingStar = resultingStar;
        this.resultingCrack = resultingCrack;
        this.newlyReachedStars = newlyReachedStars == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(newlyReachedStars));
        this.operationId = operationId == null ? "" : operationId.trim();
        this.outcome = outcome == null ? AttemptOutcome.NOT_COMMITTED : outcome;
    }

    public boolean success() {
        return success;
    }

    public String errorKey() {
        return errorKey;
    }

    public Map<String, Object> replacements() {
        return immutableMap(replacements);
    }

    public AttemptPreview preview() {
        return preview;
    }

    public ItemStack resultItem() {
        return cloneItem(resultItem);
    }

    public int resultingStar() {
        return resultingStar;
    }

    public int resultingCrack() {
        return resultingCrack;
    }

    public Set<Integer> newlyReachedStars() {
        return newlyReachedStars;
    }

    /** {@return the operation id used for idempotency and tracing} */
    public String operationId() {
        return operationId;
    }

    /** {@return the explicit transaction outcome} */
    public AttemptOutcome outcome() {
        return outcome;
    }

    /** {@return whether the attempt charge and rebuilt result were committed} */
    public boolean committed() {
        return outcome == AttemptOutcome.COMMITTED_SUCCESS || outcome == AttemptOutcome.COMMITTED_FAILURE;
    }

    /** {@return whether manual or deferred compensation is still required} */
    public boolean compensationPending() {
        return outcome == AttemptOutcome.COMPENSATION_PENDING;
    }

    public static AttemptResult failure(String errorKey, AttemptPreview preview, Map<String, Object> replacements) {
        return failure(errorKey, preview, replacements, "", AttemptOutcome.NOT_COMMITTED);
    }

    public static AttemptResult failure(String errorKey,
            AttemptPreview preview,
            Map<String, Object> replacements,
            String operationId) {
        return failure(errorKey, preview, replacements, operationId, AttemptOutcome.NOT_COMMITTED);
    }

    public static AttemptResult failure(String errorKey,
            AttemptPreview preview,
            Map<String, Object> replacements,
            String operationId,
            AttemptOutcome outcome) {
        int star = preview == null ? 0 : preview.currentStar();
        int crack = preview == null || preview.state() == null ? 0 : preview.state().crackLevel();
        return new AttemptResult(false, errorKey, replacements, preview, null, star, crack, Set.of(), operationId, outcome);
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? null : itemStack.clone();
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof ItemStack itemStack) {
            return cloneItem(itemStack);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), immutableValue(entry)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(immutableValue(entry)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(entry -> copy.add(immutableValue(entry)));
            return Collections.unmodifiableSet(copy);
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttemptResult result)) {
            return false;
        }
        return success == result.success
                && resultingStar == result.resultingStar
                && resultingCrack == result.resultingCrack
                && Objects.equals(errorKey, result.errorKey)
                && Objects.equals(replacements, result.replacements)
                && Objects.equals(preview, result.preview)
                && Objects.equals(resultItem, result.resultItem)
                && Objects.equals(newlyReachedStars, result.newlyReachedStars)
                && Objects.equals(operationId, result.operationId)
                && outcome == result.outcome;
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorKey, replacements, preview, resultItem, resultingStar,
                resultingCrack, newlyReachedStars, operationId, outcome);
    }

    @Override
    public String toString() {
        return "AttemptResult[success=" + success + ", errorKey=" + errorKey + ", preview=" + preview
                + ", resultingStar=" + resultingStar + ", resultingCrack=" + resultingCrack
                + ", operationId=" + operationId + ", outcome=" + outcome + "]";
    }
}
