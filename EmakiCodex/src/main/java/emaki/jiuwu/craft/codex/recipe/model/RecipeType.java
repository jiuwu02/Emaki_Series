package emaki.jiuwu.craft.codex.recipe.model;

import java.util.Locale;

import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

/**
 * Coarse recipe classification used by EmakiCodex when normalizing vanilla
 * recipes into its own model. Merchant recipes are intentionally excluded from
 * bridging and therefore have no entry here.
 */
public enum RecipeType {

    SHAPED,
    SHAPELESS,
    FURNACE,
    BLASTING,
    SMOKING,
    CAMPFIRE,
    SMITHING,
    STONECUTTING,
    OTHER;

    /** {@return the lowercase token used in listings and filters} */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Classifies a Bukkit recipe instance.
     *
     * @param recipe the recipe to classify
     * @return the matching type, or {@link #OTHER} for unrecognized kinds
     */
    public static RecipeType classify(Recipe recipe) {
        if (recipe instanceof ShapedRecipe) {
            return SHAPED;
        }
        if (recipe instanceof ShapelessRecipe) {
            return SHAPELESS;
        }
        if (recipe instanceof FurnaceRecipe) {
            return FURNACE;
        }
        if (recipe instanceof BlastingRecipe) {
            return BLASTING;
        }
        if (recipe instanceof SmokingRecipe) {
            return SMOKING;
        }
        if (recipe instanceof CampfireRecipe) {
            return CAMPFIRE;
        }
        if (recipe instanceof SmithingRecipe) {
            return SMITHING;
        }
        if (recipe instanceof StonecuttingRecipe) {
            return STONECUTTING;
        }
        return OTHER;
    }
}
