package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.forge.model.ValidationResult;

final class RecipeMatchingService {

    private final Function<GuiItems, List<Recipe>> candidateSelector;
    private final Supplier<List<Recipe>> recipesSupplier;
    private final BiFunction<Player, Recipe, RecipeValidator> validatorFactory;

    RecipeMatchingService(Function<GuiItems, List<Recipe>> candidateSelector,
            Supplier<List<Recipe>> recipesSupplier,
            BiFunction<Player, Recipe, RecipeValidator> validatorFactory) {
        this.candidateSelector = candidateSelector;
        this.recipesSupplier = recipesSupplier;
        this.validatorFactory = validatorFactory;
    }

    RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        ValidationResult firstError = null;
        ValidationResult firstTargetError = null;
        for (Recipe recipe : candidateRecipes(guiItems)) {
            ValidationResult validation = validatorFactory.apply(player, recipe).validate(guiItems);
            if (validation.success()) {
                return new RecipeMatch(recipe, null, Map.of());
            }
            if (recipe.requiresTargetInput()
                    && ("forge.error.no_target_item".equals(validation.errorKey())
                    || "forge.error.invalid_target_item".equals(validation.errorKey()))) {
                if (firstTargetError == null) {
                    firstTargetError = validation;
                }
                continue;
            }
            if (firstError == null) {
                firstError = validation;
            }
        }
        ValidationResult resolvedError = firstError != null ? firstError : firstTargetError;
        return new RecipeMatch(
                null,
                resolvedError == null ? "forge.error.no_recipe" : resolvedError.errorKey(),
                resolvedError == null ? Map.of() : resolvedError.replacements()
        );
    }

    private List<Recipe> safeRecipes() {
        List<Recipe> recipes = recipesSupplier == null ? null : recipesSupplier.get();
        return recipes == null ? List.of() : recipes;
    }

    private List<Recipe> candidateRecipes(GuiItems guiItems) {
        List<Recipe> candidates = candidateSelector == null ? null : candidateSelector.apply(guiItems);
        if (candidates == null || candidates.isEmpty()) {
            return safeRecipes();
        }
        return candidates;
    }

    @FunctionalInterface
    interface RecipeValidator {

        ValidationResult validate(GuiItems guiItems);
    }
}
