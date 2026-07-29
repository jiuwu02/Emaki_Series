package emaki.jiuwu.craft.cooking.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.cooking.api.model.CookingRecipeView;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.api.model.CookingStationView;
import emaki.jiuwu.craft.cooking.api.model.NutritionChange;
import emaki.jiuwu.craft.cooking.api.model.NutritionTypeView;

/**
 * Layers returned when EmakiCooking is not installed.
 *
 * <p>Queries answer empty and operations report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}, so callers never need a null
 * check on the accessors of {@link EmakiCookingApi}.
 */
final class UnavailableCooking implements CookingNutrition, CookingCatalog, CookingOperations {

    private static final UnavailableCooking INSTANCE = new UnavailableCooking();

    static final CookingNutrition NUTRITION = INSTANCE;
    static final CookingCatalog CATALOG = INSTANCE;
    static final CookingOperations OPERATIONS = INSTANCE;

    private UnavailableCooking() {
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public List<NutritionTypeView> types() {
        return List.of();
    }

    @Override
    public Optional<NutritionTypeView> type(String typeId) {
        return Optional.empty();
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
    public Optional<CookingRecipeView> findRecipe(CookingStationType stationType, String inputSource, Player player) {
        return Optional.empty();
    }

    @Override
    public List<CookingRecipeView> wokRecipes() {
        return List.of();
    }

    @Override
    public Optional<CookingStationView> stationAt(Location location) {
        return Optional.empty();
    }

    @Override
    public Optional<RecentStation> recentStation(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<Unit> inspectHeldItem(CommandSender recipient, Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> inspectTargetStation(CommandSender recipient, Player player) {
        return EmakiResult.unavailable();
    }
}
