package emaki.jiuwu.craft.codex.recipe.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified recipe model. Every bridged recipe, whether vanilla or registered on the
 * vanilla system by CraftEngine/ItemsAdder, is normalized into this shape so the
 * sync and visibility layers never touch plugin-specific recipe APIs.
 *
 * @param recipeId    the unified id, equal to the vanilla {@code NamespacedKey} string
 * @param type        the coarse recipe classification
 * @param namespace   the namespace parsed from {@code recipeId} (for filtering/display only)
 * @param ingredients the input item references
 * @param result      the output item reference
 * @param metadata    extra fields such as cook time or experience, keyed by name
 */
public record CodexRecipe(String recipeId,
        RecipeType type,
        String namespace,
        List<ItemRef> ingredients,
        ItemRef result,
        Map<String, Object> metadata) {

    public CodexRecipe {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Parses the namespace portion of a recipe id.
     *
     * @param recipeId the full recipe id
     * @return the namespace, or {@code minecraft} when the id has no colon
     */
    public static String namespaceOf(String recipeId) {
        if (recipeId == null) {
            return "minecraft";
        }
        int colon = recipeId.indexOf(':');
        return colon <= 0 ? "minecraft" : recipeId.substring(0, colon).toLowerCase(Locale.ROOT);
    }

    /** {@return whether the recipe result or any ingredient uses a custom item source} */
    public boolean hasCustomItems() {
        if (result != null && result.custom()) {
            return true;
        }
        return ingredients.stream().anyMatch(ItemRef::custom);
    }
}
