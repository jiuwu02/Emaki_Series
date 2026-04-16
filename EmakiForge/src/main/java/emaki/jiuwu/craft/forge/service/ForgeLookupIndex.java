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
    private volatile Map<String, ForgeMaterial> materialsById = Map.of();
    private volatile Map<String, BlueprintRequirement> blueprintsBySource = Map.of();
    private volatile Map<String, List<Recipe>> recipesByTargetSource = Map.of();
    private volatile List<Recipe> genericRecipes = List.of();
    private volatile List<Recipe> sortedRecipes = List.of();

    ForgeLookupIndex(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    void refresh() {
        Map<String, ForgeMaterial> materialIndex = new LinkedHashMap<>();
        Map<String, ForgeMaterial> materialIdIndex = new LinkedHashMap<>();
        Map<String, BlueprintRequirement> blueprintIndex = new LinkedHashMap<>();
        Map<String, List<Recipe>> targetRecipeIndex = new LinkedHashMap<>();
        List<Recipe> genericRecipeList = new ArrayList<>();
        List<Recipe> recipes = plugin.recipeLoader() == null
                ? new ArrayList<>()
                : new ArrayList<>(plugin.recipeLoader().all().values());
        recipes.sort(Comparator.comparing(recipe -> Texts.lower(recipe.id())));
        for (Recipe recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            ItemSource targetSource = recipe.targetItemSource();
            if (targetSource == null) {
                genericRecipeList.add(recipe);
            } else {
                targetRecipeIndex.computeIfAbsent(shorthand(targetSource), ignored -> new ArrayList<>()).add(recipe);
            }
            for (ForgeMaterial material : recipe.materials()) {
                if (material == null) {
                    continue;
                }
                String key = shorthand(material.source());
                if (!key.isBlank()) {
                    materialIndex.putIfAbsent(key, material);
                }
                String materialId = Texts.lower(material.key());
                if (!materialId.isBlank()) {
                    materialIdIndex.putIfAbsent(materialId, material);
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
        materialsById = Map.copyOf(materialIdIndex);
        blueprintsBySource = Map.copyOf(blueprintIndex);
        recipesByTargetSource = freezeRecipeIndex(targetRecipeIndex);
        genericRecipes = genericRecipeList.isEmpty() ? List.of() : List.copyOf(genericRecipeList);
        sortedRecipes = List.copyOf(recipes);
    }

    ForgeMaterial findMaterialBySource(ItemSource source) {
        return source == null ? null : materialsBySource.get(shorthand(source));
    }

    ForgeMaterial findMaterialById(String materialId) {
        return Texts.isBlank(materialId) ? null : materialsById.get(Texts.lower(materialId));
    }

    BlueprintRequirement findBlueprintRequirementBySource(ItemSource source) {
        return source == null ? null : blueprintsBySource.get(shorthand(source));
    }

    List<Recipe> sortedRecipes() {
        return sortedRecipes;
    }

    List<Recipe> findRecipesByTargetSource(ItemSource source) {
        return source == null ? genericRecipes() : recipesByTargetSource.getOrDefault(shorthand(source), List.of());
    }

    List<Recipe> genericRecipes() {
        return genericRecipes;
    }

    private Map<String, List<Recipe>> freezeRecipeIndex(Map<String, List<Recipe>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Recipe>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<Recipe>> entry : source.entrySet()) {
            if (Texts.isBlank(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return frozen.isEmpty() ? Map.of() : Map.copyOf(frozen);
    }

    private String shorthand(ItemSource source) {
        if (source == null) {
            return "";
        }
        String shorthand = Texts.lower(ItemSourceUtil.toShorthand(source));
        if (source.getType() == null) {
            return shorthand;
        }
        if (source.getType() == emaki.jiuwu.craft.corelib.item.ItemSourceType.VANILLA
                && shorthand.startsWith("minecraft:")) {
            return shorthand.substring("minecraft:".length());
        }
        return shorthand;
    }
}
