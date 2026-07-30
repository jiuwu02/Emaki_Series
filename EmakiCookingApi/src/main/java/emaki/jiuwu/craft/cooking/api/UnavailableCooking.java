package emaki.jiuwu.craft.cooking.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.cooking.api.model.CookingRecipeView;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.api.model.CookingStationView;
import emaki.jiuwu.craft.cooking.api.model.NutritionChange;
import emaki.jiuwu.craft.cooking.api.model.NutritionTypeView;

/** No-op layers used while EmakiCooking has no installed bridge. */
final class UnavailableCooking implements CookingCatalog, CookingOperations, CookingNutrition {

    private static final UnavailableCooking INSTANCE = new UnavailableCooking();

    static final CookingCatalog CATALOG = INSTANCE;
    static final CookingOperations OPERATIONS = INSTANCE;
    static final CookingNutrition NUTRITION = INSTANCE;

    private UnavailableCooking() {
    }

    @Override
    public List<CookingRecipeView> recipes(CookingStationType stationType) {
        return List.of();
    }

    @Override
    public Optional<CookingRecipeView> recipe(CookingStationType stationType, String recipeId) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<CookingRecipeView> matchRecipe(CookingStationType stationType,
            ItemStack input,
            Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<CookingStationView> stationAt(Location location) {
        return EmakiResult.unavailable();
    }

    @Override
    public Optional<Location> recentStation(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<List<ItemStack>> createOutputs(CookingStationType stationType, String recipeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Boolean> completionConditionPasses(String recipeId, Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public EmakiResult<Double> value(UUID playerId, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<NutritionChange> add(UUID playerId, String typeId, double amount) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<NutritionChange> remove(UUID playerId, String typeId, double amount) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<NutritionChange> set(UUID playerId, String typeId, double amount) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> applyFood(Player player, ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> recheckThresholds(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public List<NutritionTypeView> types() {
        return List.of();
    }

    @Override
    public Optional<NutritionTypeView> type(String typeId) {
        return Optional.empty();
    }
}
