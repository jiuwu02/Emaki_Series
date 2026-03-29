package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ForgeLookupIndex {

    private final EmakiForgePlugin plugin;
    private volatile Map<String, ForgeMaterial> materialsBySource = Map.of();
    private volatile Map<String, Blueprint> blueprintsBySource = Map.of();
    private volatile List<Recipe> sortedRecipes = List.of();

    ForgeLookupIndex(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    void refresh() {
        Map<String, ForgeMaterial> materials = new LinkedHashMap<>();
        if (plugin.materialLoader() != null) {
            for (ForgeMaterial material : plugin.materialLoader().all().values()) {
                String key = shorthand(material == null ? null : material.source());
                if (!key.isBlank()) {
                    materials.put(key, material);
                }
            }
        }
        Map<String, Blueprint> blueprints = new LinkedHashMap<>();
        if (plugin.blueprintLoader() != null) {
            for (Blueprint blueprint : plugin.blueprintLoader().all().values()) {
                String key = shorthand(blueprint == null ? null : blueprint.source());
                if (!key.isBlank()) {
                    blueprints.put(key, blueprint);
                }
            }
        }
        // Services are constructed before loaders are attached to the plugin instance.
        List<Recipe> recipes = plugin.recipeLoader() == null
            ? new ArrayList<>()
            : new ArrayList<>(plugin.recipeLoader().all().values());
        recipes.sort(Comparator.comparing(recipe -> Texts.lower(recipe.id())));
        materialsBySource = Map.copyOf(materials);
        blueprintsBySource = Map.copyOf(blueprints);
        sortedRecipes = List.copyOf(recipes);
    }

    ForgeMaterial findMaterialBySource(ItemSource source) {
        return source == null ? null : materialsBySource.get(shorthand(source));
    }

    Blueprint findBlueprintBySource(ItemSource source) {
        return source == null ? null : blueprintsBySource.get(shorthand(source));
    }

    List<Recipe> sortedRecipes() {
        return sortedRecipes;
    }

    private String shorthand(ItemSource source) {
        return source == null ? "" : Texts.lower(ItemSourceUtil.toShorthand(source));
    }
}
