package emaki.jiuwu.craft.station.recipe;

import java.util.List;

/**
 * The outcome of matching available materials against a station's recipes.
 *
 * @param recipe       the winning recipe, or {@code null} when nothing matched
 * @param alternatives every recipe that also matched, including the winner, in ranking order
 * @param maxBatch     how many batches the available materials support for {@link #recipe()}
 */
public record RecipeMatch(RecipeDefinition recipe, List<RecipeDefinition> alternatives, long maxBatch) {

    private static final RecipeMatch NONE = new RecipeMatch(null, List.of(), 0L);

    /**
     * Creates a match with a defensively copied alternative list.
     *
     * @param recipe       the winning recipe
     * @param alternatives all matching recipes in ranking order; {@code null} becomes empty
     * @param maxBatch     the supported batch count; negatives are clamped to zero
     */
    public RecipeMatch {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        maxBatch = Math.max(0L, maxBatch);
    }

    /** {@return the canonical "nothing matched" result} */
    public static RecipeMatch none() {
        return NONE;
    }

    /** {@return whether a recipe matched} */
    public boolean matched() {
        return recipe != null;
    }

    /** {@return how many recipes matched besides the winner} */
    public int otherCount() {
        return Math.max(0, alternatives.size() - 1);
    }
}
