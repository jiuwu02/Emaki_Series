package emaki.jiuwu.craft.cooking.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of a cooking recipe.
 *
 * <p>Only the stable identity of a recipe is exposed. EmakiCooking's internal recipe object carries a
 * live handle to its YAML section, which would tie third parties to a configuration shape that server
 * owners are free to restructure; that handle is deliberately not surfaced.
 *
 * @param id          canonical lowercase recipe id
 * @param displayName display name; falls back to the id when unset
 * @param stationType the station kind that can run this recipe
 */
public record CookingRecipeView(@NotNull String id,
                                @NotNull String displayName,
                                @NotNull CookingStationType stationType) {

    /**
     * Normalises the identity components and requires a station type.
     *
     * @param id          canonical lowercase recipe id
     * @param displayName display name
     * @param stationType owning station kind
     * @throws NullPointerException when {@code stationType} is {@code null}
     */
    public CookingRecipeView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        if (stationType == null) {
            throw new NullPointerException("stationType");
        }
    }
}
