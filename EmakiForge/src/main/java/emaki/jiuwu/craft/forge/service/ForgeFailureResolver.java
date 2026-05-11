package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

/**
 * Resolves the outcome when a forge attempt fails (success_rate check did not pass).
 * <p>
 * Selects a failure outcome from the recipe's configured list by weighted random,
 * then executes the corresponding logic (return materials, consume, produce byproduct, etc.).
 */
final class ForgeFailureResolver {

    record ForgeFailureResult(String outcomeType, boolean shouldReturnMaterials, double returnRate) {

        static ForgeFailureResult defaultReturn() {
            return new ForgeFailureResult("return_materials", true, 1.0D);
        }
    }

    ForgeFailureResolver() {
    }

    ForgeFailureResult resolve(Recipe recipe, GuiItems guiItems, Player player) {
        if (recipe == null) {
            return ForgeFailureResult.defaultReturn();
        }
        List<Recipe.FailureOutcome> outcomes = recipe.failureOutcomes();
        if (outcomes == null || outcomes.isEmpty()) {
            return ForgeFailureResult.defaultReturn();
        }
        Recipe.FailureOutcome selected = selectByWeight(outcomes);
        if (selected == null) {
            return ForgeFailureResult.defaultReturn();
        }
        return switch (selected.type()) {
            case "consume_materials" -> new ForgeFailureResult("consume_materials", false, 0D);
            case "partial_consume" -> {
                double rate = parseDouble(selected.params().get("return_rate"), 0.5D);
                yield new ForgeFailureResult("partial_consume", true, rate);
            }
            case "return_materials" -> {
                double rate = parseDouble(selected.params().get("return_rate"), 1.0D);
                yield new ForgeFailureResult("return_materials", true, rate);
            }
            default -> new ForgeFailureResult(selected.type(), true, 1.0D);
        };
    }

    private Recipe.FailureOutcome selectByWeight(List<Recipe.FailureOutcome> outcomes) {
        int totalWeight = 0;
        for (Recipe.FailureOutcome outcome : outcomes) {
            totalWeight += outcome.weight();
        }
        if (totalWeight <= 0) {
            return outcomes.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (Recipe.FailureOutcome outcome : outcomes) {
            cumulative += outcome.weight();
            if (roll < cumulative) {
                return outcome;
            }
        }
        return outcomes.get(outcomes.size() - 1);
    }

    private double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException _) {
                return fallback;
            }
        }
        return fallback;
    }
}
