package emaki.jiuwu.craft.forge.api;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.forge.api.model.ForgeInputs;
import emaki.jiuwu.craft.forge.api.model.ForgeMaterialView;
import emaki.jiuwu.craft.forge.api.model.ForgeRecipeView;
import emaki.jiuwu.craft.forge.api.model.ForgeValidation;

/**
 * Layers returned when EmakiForge is not installed.
 *
 * <p>Queries answer empty and operations report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}, so callers never need a
 * null check on {@code EmakiForgeApi.catalog()} or {@code operations()}.
 */
final class UnavailableForge implements ForgeCatalog, ForgeOperations {

    static final ForgeCatalog CATALOG = new UnavailableForge();
    static final ForgeOperations OPERATIONS = (ForgeOperations) CATALOG;

    private UnavailableForge() {
    }

    @Override
    public List<ForgeRecipeView> recipes() {
        return List.of();
    }

    @Override
    public Optional<ForgeRecipeView> recipe(String recipeId) {
        return Optional.empty();
    }

    @Override
    public Optional<ForgeMaterialView> materialById(String materialId) {
        return Optional.empty();
    }

    @Override
    public Optional<ForgeMaterialView> materialByItem(ItemStack itemStack) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<ForgeRecipeView> matchRecipe(Player player, ForgeInputs inputs) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ForgeValidation> validate(Player player, String recipeId, ForgeInputs inputs) {
        return EmakiResult.unavailable();
    }

    @Override
    public boolean accepting() {
        return false;
    }

    @Override
    public EmakiResult<Unit> openForgeGui(Player player, String recipeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openForgeGui(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openRecipeBook(Player player, int page) {
        return EmakiResult.unavailable();
    }

    @Override
    public boolean viewingRecipeBook(Player player) {
        return false;
    }

    @Override
    public EmakiResult<ItemStack> refreshItem(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> refreshPlayer(Player player) {
        return EmakiResult.unavailable();
    }
}
