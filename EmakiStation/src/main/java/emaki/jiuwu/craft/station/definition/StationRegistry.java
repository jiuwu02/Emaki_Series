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

/**
 * Immutable resolved view of every station together with its recipe set.
 *
 * <p>Stations and recipes load from separate directories, so recipe membership can only be resolved once
 * both are present. Doing it here, once per load, keeps the per-click path free of any matching.
 *
 * <p>A reload builds a whole new registry and swaps it in atomically. Nothing mutates in place, so a GUI
 * session that captured the previous registry keeps seeing a self-consistent station and recipe set for
 * as long as it lives.
 */
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

    /** {@return a registry holding no stations and no recipes} */
    public static StationRegistry empty() {
        return EMPTY;
    }

    /**
     * Resolves loaded stations against loaded recipes.
     *
     * <p>Membership is declared on the recipe side: a recipe listing {@code station_ids} belongs only to
     * those stations, and a recipe declaring none belongs to every station.
     *
     * @param loadedStations the loaded stations keyed by id
     * @param loadedRecipes  the loaded recipes keyed by id
     * @return the resolved registry
     */
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

    /**
     * Looks up one station.
     *
     * @param stationId the station id; matched case-insensitively
     * @return the station, or {@code null} when unknown
     */
    public StationDefinition station(String stationId) {
        return stationId == null ? null : stations.get(normalize(stationId));
    }

    /**
     * Looks up one recipe.
     *
     * @param recipeId the recipe id; matched case-insensitively
     * @return the recipe, or {@code null} when unknown
     */
    public RecipeDefinition recipe(String recipeId) {
        return recipeId == null ? null : recipes.get(normalize(recipeId));
    }

    /** {@return every station in load order} */
    public List<StationDefinition> stations() {
        return List.copyOf(stations.values());
    }

    /** {@return every recipe in load order} */
    public List<RecipeDefinition> recipes() {
        return List.copyOf(recipes.values());
    }

    /**
     * Lists the recipe ids one station resolved.
     *
     * @param stationId the station id
     * @return the ids, or an empty set when the station is unknown
     */
    public Set<String> recipeIdsOf(String stationId) {
        if (stationId == null) {
            return Set.of();
        }
        Set<String> ids = recipeIdsByStation.get(normalize(stationId));
        return ids == null ? Set.of() : ids;
    }

    /**
     * Lists the recipes one station resolved, in stable recipe load order.
     *
     * @param stationId the station id
     * @return the recipes, or an empty list when the station is unknown
     */
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

    /** {@return the inverted material index shared by every station} */
    public RecipeIndex recipeIndex() {
        return recipeIndex;
    }

    /** {@return how many stations are loaded} */
    public int stationCount() {
        return stations.size();
    }

    /** {@return how many recipes are loaded} */
    public int recipeCount() {
        return recipes.size();
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
