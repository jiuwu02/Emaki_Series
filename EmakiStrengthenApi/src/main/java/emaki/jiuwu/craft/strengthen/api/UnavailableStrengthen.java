package emaki.jiuwu.craft.strengthen.api;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

/** Unavailable no-op layers returned while no runtime bridge is installed. */
final class UnavailableStrengthen implements StrengthenCatalog, StrengthenOperations {

    static final StrengthenCatalog CATALOG = new UnavailableStrengthen();
    static final StrengthenOperations OPERATIONS = (StrengthenOperations) CATALOG;

    private UnavailableStrengthen() {
    }

    @Override
    public EmakiResult<StrengthenState> readState(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public List<StrengthenRecipe> recipes() {
        return List.of();
    }

    @Override
    public Optional<StrengthenRecipe> recipe(String recipeId) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<AttemptPreview> preview(Player player, AttemptContext context) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Double> successRate(Player player, AttemptContext context) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<AttemptResult> attempt(Player player, AttemptContext context) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<StrengthenTransferOutcome> transfer(Player player,
            ItemStack source,
            ItemStack target,
            double decayRate) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> rebuild(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openGui(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> refreshItem(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Integer> refreshPlayer(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> registerEnhancementTarget(EnhancementTargetProvider provider) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> unregisterEnhancementTarget(String providerId) {
        return EmakiResult.unavailable();
    }
}
