package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeLookupIndex {

    private final EmakiForgePlugin plugin;
    private volatile Map<String, ForgeMaterial> materialsBySource = Map.of();
    private volatile Map<String, BlueprintRequirement> blueprintsBySource = Map.of();
    private volatile List<Recipe> sortedRecipes = List.of();

    ForgeLookupIndex(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    void refresh() {
        Map<String, ForgeMaterial> materialIndex = new LinkedHashMap<>();
        Map<String, BlueprintRequirement> blueprintIndex = new LinkedHashMap<>();
        List<Recipe> recipes = plugin.recipeLoader() == null
                ? new ArrayList<>()
                : new ArrayList<>(plugin.recipeLoader().all().values());
        recipes.sort(Comparator.comparing(recipe -> Texts.lower(recipe.id())));
        for (Recipe recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            for (ForgeMaterial material : recipe.materials()) {
                if (material == null) {
                    continue;
                }
                String key = shorthand(material.source());
                if (!key.isBlank()) {
                    materialIndex.putIfAbsent(key, material);
                }
            }
            for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
                if (requirement == null) {
                    continue;
                }
                String key = shorthand(requirement.source());
                if (!key.isBlank()) {
                    blueprintIndex.putIfAbsent(key, requirement);
                }
            }
        }
        materialsBySource = Map.copyOf(materialIndex);
        blueprintsBySource = Map.copyOf(blueprintIndex);
        sortedRecipes = List.copyOf(recipes);
    }

    ForgeMaterial findMaterialBySource(ItemSource source) {
        return source == null ? null : materialsBySource.get(shorthand(source));
    }

    BlueprintRequirement findBlueprintRequirementBySource(ItemSource source) {
        return source == null ? null : blueprintsBySource.get(shorthand(source));
    }

    List<Recipe> sortedRecipes() {
        return sortedRecipes;
    }

    private String shorthand(ItemSource source) {
        return source == null ? "" : Texts.lower(ItemSourceUtil.toShorthand(source));
    }
}
