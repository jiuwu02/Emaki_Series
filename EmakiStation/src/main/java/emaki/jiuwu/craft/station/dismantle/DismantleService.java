package emaki.jiuwu.craft.station.dismantle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.math.Randoms;

/**
 * Core dismantle logic: recipe lookup and output rolling.
 *
 * <p>Recipes are indexed by station id and by input item source so lookups are O(1) on the hot
 * path. An empty station id ({@code ""}) means the recipe is available at any station.
 */
public final class DismantleService {

    /** Keyed by recipe id. */
    private final Map<String, DismantleRecipeDefinition> byId = new ConcurrentHashMap<>();

    /**
     * Replaces the full recipe index.
     *
     * @param recipes the newly loaded recipes; {@code null} clears the index
     */
    public void reload(List<DismantleRecipeDefinition> recipes) {
        byId.clear();
        if (recipes == null) {
            return;
        }
        for (DismantleRecipeDefinition recipe : recipes) {
            if (recipe != null) {
                byId.put(recipe.id(), recipe);
            }
        }
    }

    /**
     * Finds all dismantle recipes whose {@code input_source} matches the given item source reference.
     *
     * <p>Recipes scoped to a different station are excluded when {@code stationId} is non-blank.
     *
     * @param inputRef  the item source reference representing the input item
     * @param stationId the station the player is at; empty means any
     * @return an unmodifiable list of matching recipes, possibly empty
     */
    public List<DismantleRecipeDefinition> findMatching(ItemSourceRef inputRef, String stationId) {
        if (inputRef == null) {
            return List.of();
        }
        List<DismantleRecipeDefinition> results = new ArrayList<>();
        for (DismantleRecipeDefinition recipe : byId.values()) {
            if (!inputRef.equals(recipe.inputSource())) {
                continue;
            }
            if (recipe.hasScopedStation() && !recipe.stationId().equals(stationId)) {
                continue;
            }
            results.add(recipe);
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Looks up a recipe by its id.
     *
     * @param recipeId the recipe id
     * @return the recipe, or empty when unknown
     */
    public Optional<DismantleRecipeDefinition> findById(String recipeId) {
        if (recipeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(recipeId));
    }

    /**
     * Rolls the output for a given recipe.
     *
     * <p>Rolls are independent: each roll picks one weighted pool entry, then resolves a random
     * amount from that entry's {@link AmountRange}. Outputs for the same item source are not
     * merged — the caller is responsible for aggregating duplicates if needed.
     *
     * @param recipe the recipe to roll
     * @return the resolved outputs, one element per roll; never {@code null}, may be empty when
     *         the pool is empty
     */
    public List<DismantleOutput> roll(DismantleRecipeDefinition recipe) {
        if (recipe == null || recipe.pool().isEmpty()) {
            return List.of();
        }
        int rollCount = Randoms.randomInt(recipe.rolls().min(), recipe.rolls().max());
        List<Randoms.Weighted<DismantlePoolEntry>> weighted = new ArrayList<>(recipe.pool().size());
        for (DismantlePoolEntry entry : recipe.pool()) {
            weighted.add(new Randoms.Weighted<>(entry, entry.weight()));
        }
        List<DismantleOutput> outputs = new ArrayList<>(rollCount);
        for (int i = 0; i < rollCount; i++) {
            DismantlePoolEntry picked = Randoms.weightedRandom(weighted);
            if (picked == null) {
                continue;
            }
            int amount = Randoms.randomInt(picked.amount().min(), picked.amount().max());
            outputs.add(new DismantleOutput(picked.source(), amount));
        }
        return Collections.unmodifiableList(outputs);
    }

    /** {@return the number of loaded recipes} */
    public int size() {
        return byId.size();
    }
}
