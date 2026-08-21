package emaki.jiuwu.craft.station.dismantle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.random.WeightedPool;

public final class DismantleService {

    private final Map<String, DismantleRecipeDefinition> byId = new ConcurrentHashMap<>();

    private final ItemSourceService itemSourceService;

    public DismantleService() {
        this(null);
    }

    public DismantleService(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

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

    public List<DismantleRecipeDefinition> findMatching(ItemStack input, String stationId) {
        return findMatching(input, stationId, null);
    }

    public List<DismantleRecipeDefinition> findMatching(ItemStack input, String stationId, Player player) {
        if (input == null || input.getType().isAir()) {
            return List.of();
        }
        MatchContext context = MatchContext.of(input, identify(input), player);
        List<DismantleRecipeDefinition> results = new ArrayList<>();
        for (DismantleRecipeDefinition recipe : byId.values()) {
            if (!recipe.acceptsInput(context)) {
                continue;
            }
            if (recipe.hasScopedStation() && !recipe.stationId().equals(stationId)) {
                continue;
            }
            results.add(recipe);
        }
        return Collections.unmodifiableList(results);
    }

    public boolean accepts(DismantleRecipeDefinition recipe, ItemStack input, Player player) {
        if (recipe == null || input == null || input.getType().isAir()) {
            return false;
        }
        return recipe.acceptsInput(MatchContext.of(input, identify(input), player));
    }

    public ItemSourceRef identify(ItemStack input) {
        return itemSourceService == null ? null : itemSourceService.identifyItem(input);
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
        WeightedPool<DismantlePoolEntry> pool = new WeightedPool<>();
        for (DismantlePoolEntry entry : recipe.pool()) {
            pool.add(entry, entry.weight());
        }
        List<DismantleOutput> outputs = new ArrayList<>(rollCount);
        for (int i = 0; i < rollCount; i++) {
            Optional<DismantlePoolEntry> picked = pool.roll();
            if (picked.isEmpty()) {
                continue;
            }
            DismantlePoolEntry entry = picked.get();
            int amount = Randoms.randomInt(entry.amount().min(), entry.amount().max());
            outputs.add(new DismantleOutput(entry.source(), amount));
        }
        return Collections.unmodifiableList(outputs);
    }

    public int size() {
        return byId.size();
    }
}
