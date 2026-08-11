package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one crafting recipe.
 *
 * @param id                the recipe id, unique across the whole recipe directory
 * @param displayName       the configured display name, unrendered
 * @param tags              the tags used by station include/exclude rules
 * @param requirements      the material requirements, matched as an unordered set
 * @param durationSeconds   how long one submission of this recipe takes
 * @param outputs           what one batch produces
 * @param permission        the permission required to see and use it, or an empty string when unrestricted
 * @param costProviderId    the economy provider charged per batch, or an empty string when free
 * @param costAmount        the currency charged per batch; zero when free
 * @param visible           whether the recipe appears in a station catalog at all
 * @param displayConditional whether the catalog entry is gated behind a display condition
 */
public record RecipeView(@NotNull String id,
        @NotNull String displayName,
        @NotNull List<String> tags,
        @NotNull List<MaterialRequirementView> requirements,
        long durationSeconds,
        @NotNull List<PendingOutput> outputs,
        @NotNull String permission,
        @NotNull String costProviderId,
        long costAmount,
        boolean visible,
        boolean displayConditional) {

    /**
     * Creates a recipe view with defensively copied collections.
     *
     * @param id                the recipe id
     * @param displayName       the configured display name; {@code null} becomes the id
     * @param tags              the recipe tags; {@code null} becomes empty
     * @param requirements      the material requirements; {@code null} becomes empty
     * @param durationSeconds   the craft duration; negative values are clamped to zero
     * @param outputs           what one batch produces; {@code null} becomes empty
     * @param permission        the required permission; {@code null} becomes an empty string
     * @param costProviderId    the charged economy provider; {@code null} becomes an empty string
     * @param costAmount        the per-batch charge; negatives are clamped to zero
     * @param visible           whether the recipe appears in a catalog
     * @param displayConditional whether a display condition gates the catalog entry
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public RecipeView {
        if (id == null) {
            throw new NullPointerException("id");
        }
        displayName = displayName == null ? id : displayName;
        tags = tags == null ? List.of() : List.copyOf(tags);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        permission = permission == null ? "" : permission;
        durationSeconds = Math.max(0L, durationSeconds);
        costProviderId = costProviderId == null ? "" : costProviderId;
        costAmount = Math.max(0L, costAmount);
    }

    /** {@return whether this recipe restricts use behind its own permission node} */
    public boolean hasPermission() {
        return !permission.isBlank();
    }

    /** {@return whether this recipe completes instantly instead of entering a timed queue} */
    public boolean instant() {
        return durationSeconds <= 0L;
    }

    /** {@return whether this recipe charges currency on submission} */
    public boolean charges() {
        return !costProviderId.isEmpty() && costAmount > 0L;
    }
}
