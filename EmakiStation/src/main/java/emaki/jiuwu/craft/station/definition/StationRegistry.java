package emaki.jiuwu.craft.station.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeIndex;

public final class StationRegistry {

    private static final StationRegistry EMPTY =
            new StationRegistry(Map.of(), Map.of(), Map.of(), RecipeIndex.empty());

    private final Map<String, StationDefinition> stations;
    private final Map<String, RecipeDefinition> recipes;
    private final Map<String, Set<String>> recipeIdsByStation;
    private final RecipeIndex recipeIndex;

    private StationRegistry(Map<String, StationDefinition> stations,
            Map<String, RecipeDefinition> recipes,
            Map<String, Set<String>> recipeIdsByStation,
            RecipeIndex recipeIndex) {
        this.stations = stations;
        this.recipes = recipes;
        this.recipeIdsByStation = recipeIdsByStation;
        this.recipeIndex = recipeIndex;
    }

    public static StationRegistry empty() {
        return EMPTY;
    }

    public static StationRegistry resolve(Map<String, StationDefinition> loadedStations,
            Map<String, RecipeDefinition> loadedRecipes) {
        if (loadedStations == null || loadedStations.isEmpty()) {
            return new StationRegistry(Map.of(),
                    loadedRecipes == null ? Map.of() : Map.copyOf(loadedRecipes),
                    Map.of(),
                    RecipeIndex.build(loadedRecipes == null ? List.of() : loadedRecipes.values()));
        }
        Map<String, RecipeDefinition> recipes = loadedRecipes == null
                ? Map.of()
                : Map.copyOf(loadedRecipes);
        Map<String, Set<String>> byStation = new LinkedHashMap<>();
        for (StationDefinition station : loadedStations.values()) {
            if (station != null) {
                byStation.put(station.id(), resolveRecipeIds(station, recipes));
            }
        }
        return new StationRegistry(Map.copyOf(loadedStations),
                recipes,
                Map.copyOf(byStation),
                RecipeIndex.build(recipes.values()));
    }

    private static Set<String> resolveRecipeIds(StationDefinition station,
            Map<String, RecipeDefinition> recipes) {
        Set<String> resolved = new LinkedHashSet<>();
        for (RecipeDefinition recipe : recipes.values()) {
            if (recipe == null) {
                continue;
            }
            if (recipe.belongsTo(station.id())) {
                resolved.add(recipe.id());
            }
        }
        return Set.copyOf(resolved);
    }

    public StationDefinition station(String stationId) {
        return stationId == null ? null : stations.get(normalize(stationId));
    }

    public RecipeDefinition recipe(String recipeId) {
        return recipeId == null ? null : recipes.get(normalize(recipeId));
    }

    public List<StationDefinition> stations() {
        return List.copyOf(stations.values());
    }

    public List<RecipeDefinition> recipes() {
        return List.copyOf(recipes.values());
    }

    public Set<String> recipeIdsOf(String stationId) {
        if (stationId == null) {
            return Set.of();
        }
        Set<String> ids = recipeIdsByStation.get(normalize(stationId));
        return ids == null ? Set.of() : ids;
    }

    public List<RecipeDefinition> recipesOf(String stationId) {
        Set<String> ids = recipeIdsOf(stationId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<RecipeDefinition> resolved = new ArrayList<>(ids.size());
        for (RecipeDefinition recipe : recipes.values()) {
            if (ids.contains(recipe.id())) {
                resolved.add(recipe);
            }
        }
        return List.copyOf(resolved);
    }

    public RecipeIndex recipeIndex() {
        return recipeIndex;
    }

    public int stationCount() {
        return stations.size();
    }

    public int recipeCount() {
        return recipes.size();
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
