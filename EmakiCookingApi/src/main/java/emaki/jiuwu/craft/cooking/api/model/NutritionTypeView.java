package emaki.jiuwu.craft.cooking.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one configured nutrition type.
 *
 * @param id           canonical lowercase type id
 * @param displayName  display name; falls back to the id when unset
 * @param min          lower bound of the value range
 * @param max          upper bound of the value range
 * @param defaultValue the value a player starts with, already clamped into {@code [min, max]}
 */
public record NutritionTypeView(@NotNull String id,
                                @NotNull String displayName,
                                double min,
                                double max,
                                double defaultValue) {

    /**
     * Normalises the identity components so no accessor can return {@code null}.
     *
     * @param id           canonical lowercase type id
     * @param displayName  display name
     * @param min          range lower bound
     * @param max          range upper bound
     * @param defaultValue starting value
     */
    public NutritionTypeView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
    }

    /**
     * Clamps a value into this type's configured range.
     *
     * @param value the value to clamp
     * @return the clamped value
     */
    public double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }
}
