package emaki.jiuwu.craft.codex.recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import emaki.jiuwu.craft.codex.recipe.model.CodexRecipe;
import emaki.jiuwu.craft.codex.recipe.model.ItemRef;
import emaki.jiuwu.craft.codex.recipe.model.RecipeType;

/**
 * Traverses the vanilla recipe system and normalizes every recipe into a
 * {@link CodexRecipe}. CraftEngine/ItemsAdder recipes register on the vanilla
 * system, so iterating {@link Bukkit#recipeIterator()} covers them without any
 * plugin-specific adapter. Merchant recipes carry no key and are skipped.
 *
 * <p>Bukkit recipe registry access must happen on the main thread; callers are
 * responsible for invoking {@link #collect()} on the correct thread.
 */
@SuppressWarnings("deprecation") // RecipeChoice#getItemStack is soft-deprecated but the stable way to sample a choice
public final class RecipeCollector {

    private final ItemRefFactory itemRefFactory;

    public RecipeCollector(ItemRefFactory itemRefFactory) {
        this.itemRefFactory = itemRefFactory;
    }

    /**
     * Iterates the whole vanilla recipe registry.
     *
     * @return normalized recipes keyed by their id, in registry order
     */
    public Map<String, CodexRecipe> collect() {
        Map<String, CodexRecipe> collected = new LinkedHashMap<>();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            CodexRecipe codexRecipe = convert(recipe);
            if (codexRecipe != null) {
                collected.putIfAbsent(codexRecipe.recipeId(), codexRecipe);
            }
        }
        return collected;
    }

    private CodexRecipe convert(Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        String recipeId = recipeKey(recipe);
        if (recipeId == null) {
            return null;
        }
        RecipeType type = RecipeType.classify(recipe);
        ItemRef result = itemRefFactory.toRef(recipe.getResult());
        List<ItemRef> ingredients = extractIngredients(recipe);
        Map<String, Object> metadata = extractMetadata(recipe);
        return new CodexRecipe(recipeId, type, CodexRecipe.namespaceOf(recipeId), ingredients, result, metadata);
    }

    private String recipeKey(Recipe recipe) {
        if (recipe instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            return key == null ? null : key.toString();
        }
        return null;
    }

    private List<ItemRef> extractIngredients(Recipe recipe) {
        List<ItemRef> refs = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            for (String row : shaped.getShape()) {
                for (char symbol : row.toCharArray()) {
                    if (symbol == ' ') {
                        continue;
                    }
                    ItemStack ingredient = shaped.getIngredientMap().get(symbol);
                    if (ingredient != null) {
                        refs.add(itemRefFactory.toRef(ingredient));
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (ItemStack ingredient : shapeless.getIngredientList()) {
                refs.add(itemRefFactory.toRef(ingredient));
            }
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            refs.add(itemRefFactory.toRef(cooking.getInput()));
        } else if (recipe instanceof StonecuttingRecipe stonecutting) {
            refs.add(itemRefFactory.toRef(stonecutting.getInput()));
        } else if (recipe instanceof SmithingRecipe smithing) {
            addChoiceItem(refs, smithing.getBase());
            addChoiceItem(refs, smithing.getAddition());
        }
        return refs;
    }

    private void addChoiceItem(List<ItemRef> refs, RecipeChoice choice) {
        if (choice == null) {
            return;
        }
        refs.add(itemRefFactory.toRef(choice.getItemStack()));
    }

    private Map<String, Object> extractMetadata(Recipe recipe) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (recipe instanceof CookingRecipe<?> cooking) {
            metadata.put("cooking_time", cooking.getCookingTime());
            metadata.put("experience", cooking.getExperience());
            if (recipe instanceof FurnaceRecipe) {
                metadata.put("station", "furnace");
            } else if (recipe instanceof BlastingRecipe) {
                metadata.put("station", "blast_furnace");
            } else if (recipe instanceof CampfireRecipe) {
                metadata.put("station", "campfire");
            } else {
                metadata.put("station", "smoker");
            }
        }
        return metadata;
    }
}
