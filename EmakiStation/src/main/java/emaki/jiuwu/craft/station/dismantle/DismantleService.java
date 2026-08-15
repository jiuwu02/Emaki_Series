package emaki.jiuwu.craft.station.dismantle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.math.Randoms;

public final class DismantleService {

    private final Map<String, DismantleRecipeDefinition> byId = new ConcurrentHashMap<>();

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

    public Optional<DismantleRecipeDefinition> findById(String recipeId) {
        if (recipeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(recipeId));
    }

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

    public int size() {
        return byId.size();
    }
}
