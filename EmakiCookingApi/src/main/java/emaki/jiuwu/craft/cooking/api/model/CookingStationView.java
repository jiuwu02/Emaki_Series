package emaki.jiuwu.craft.cooking.api.model;

import java.util.Optional;
import java.util.OptionalInt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only snapshot of one placed cooking station.
 *
 * <p>EmakiCooking stores all seven station kinds in a single wide structure where each kind fills only
 * the fields it cares about and leaves the rest at zero. That makes a raw {@code 0} ambiguous: an oven
 * at zero heat and a chopping board that has no concept of heat look identical. This view therefore
 * exposes the kind-specific readings as {@link OptionalInt}, present only when the station type
 * actually tracks them.
 *
 * @param stationType   the station kind
 * @param recipeId      the recipe currently running, or {@code null} when idle
 * @param recipeName    display name of the running recipe, or {@code null} when idle
 * @param progress      how far the current recipe has advanced
 * @param burning       whether the station's heat source is lit
 * @param heat          heat level, present for stations that track heat
 * @param moisture      moisture level, present for stations that track moisture
 * @param steam         steam reservoir, present for stations that track steam
 * @param ingredients   how many ingredients are loaded
 * @param fluidName     the fluid held, or {@code null} when the station holds none
 * @param fluidAmountMl fluid volume in millilitres, present only when a fluid is held
 */
public record CookingStationView(@NotNull CookingStationType stationType,
                                 @Nullable String recipeId,
                                 @Nullable String recipeName,
                                 @NotNull CookingProgress progress,
                                 boolean burning,
                                 @NotNull OptionalInt heat,
                                 @NotNull OptionalInt moisture,
                                 @NotNull OptionalInt steam,
                                 int ingredients,
                                 @Nullable String fluidName,
                                 @NotNull OptionalInt fluidAmountMl) {

    /**
     * Normalises optional readings and blank strings.
     *
     * @param stationType   station kind
     * @param recipeId      running recipe id
     * @param recipeName    running recipe display name
     * @param progress      recipe progress
     * @param burning       whether the heat source is lit
     * @param heat          heat reading
     * @param moisture      moisture reading
     * @param steam         steam reading
     * @param ingredients   loaded ingredient count
     * @param fluidName     held fluid name
     * @param fluidAmountMl held fluid volume
     * @throws NullPointerException when {@code stationType} is {@code null}
     */
    public CookingStationView {
        if (stationType == null) {
            throw new NullPointerException("stationType");
        }
        recipeId = recipeId == null || recipeId.isBlank() ? null : recipeId;
        recipeName = recipeName == null || recipeName.isBlank() ? null : recipeName;
        progress = progress == null ? CookingProgress.idle() : progress;
        heat = heat == null ? OptionalInt.empty() : heat;
        moisture = moisture == null ? OptionalInt.empty() : moisture;
        steam = steam == null ? OptionalInt.empty() : steam;
        ingredients = Math.max(0, ingredients);
        fluidName = fluidName == null || fluidName.isBlank() ? null : fluidName;
        fluidAmountMl = fluidAmountMl == null ? OptionalInt.empty() : fluidAmountMl;
    }

    /** {@return the running recipe id when the station is busy} */
    public @NotNull Optional<String> recipe() {
        return Optional.ofNullable(recipeId);
    }

    /** {@return the held fluid name when the station holds one} */
    public @NotNull Optional<String> fluid() {
        return Optional.ofNullable(fluidName);
    }

    /** {@return whether the station has a recipe loaded, running or completed} */
    public boolean occupied() {
        return recipeId != null;
    }
}
