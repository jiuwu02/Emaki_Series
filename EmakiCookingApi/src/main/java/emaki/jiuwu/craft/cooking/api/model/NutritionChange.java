package emaki.jiuwu.craft.cooking.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Result of changing a player's nutrition value.
 *
 * <p>{@link #oldValue()} and {@link #newValue()} are equal when the requested change was clamped away
 * entirely — for example adding to a value that already sits at its configured maximum. That is a
 * success, not a failure: the operation was applied and the outcome is simply "no movement".
 *
 * @param typeId   canonical lowercase nutrition type id
 * @param oldValue the value before the change
 * @param newValue the value after the change, already clamped into the type's range
 */
public record NutritionChange(@NotNull String typeId, double oldValue, double newValue) {

    /**
     * Normalises the type id so it can never be {@code null}.
     *
     * @param typeId   canonical lowercase nutrition type id
     * @param oldValue value before the change
     * @param newValue value after the change
     */
    public NutritionChange {
        typeId = typeId == null ? "" : typeId;
    }

    /** {@return how much the value moved; negative when it decreased} */
    public double delta() {
        return newValue - oldValue;
    }

    /** {@return whether the value actually moved} */
    public boolean changed() {
        return Double.compare(oldValue, newValue) != 0;
    }
}
