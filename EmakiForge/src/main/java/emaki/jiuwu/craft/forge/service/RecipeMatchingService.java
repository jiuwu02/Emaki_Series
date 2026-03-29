package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

final class RecipeMatchingService {

    private final Supplier<List<Recipe>> recipesSupplier;
    private final BiFunction<Player, Recipe, RecipeValidator> validatorFactory;

    RecipeMatchingService(Supplier<List<Recipe>> recipesSupplier,
                          BiFunction<Player, Recipe, RecipeValidator> validatorFactory) {
        this.recipesSupplier = recipesSupplier;
        this.validatorFactory = validatorFactory;
    }

    RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        ValidationResult firstError = null;
        for (Recipe recipe : safeRecipes()) {
            ValidationResult validation = validatorFactory.apply(player, recipe).validate(guiItems);
            if (validation.success()) {
                return new RecipeMatch(recipe, null, Map.of());
            }
            if (recipe.targetItemSource() != null
                && ("forge.error.no_target_item".equals(validation.errorKey())
                || "forge.error.invalid_target_item".equals(validation.errorKey()))) {
                continue;
            }
            if (firstError == null) {
                firstError = validation;
            }
        }
        return new RecipeMatch(
            null,
            firstError == null ? "forge.error.no_recipe" : firstError.errorKey(),
            firstError == null ? Map.of() : firstError.replacements()
        );
    }

    private List<Recipe> safeRecipes() {
        List<Recipe> recipes = recipesSupplier == null ? null : recipesSupplier.get();
        return recipes == null ? List.of() : recipes;
    }

    @FunctionalInterface
    interface RecipeValidator {
        ValidationResult validate(GuiItems guiItems);
    }
}
