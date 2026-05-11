package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

/**
 * Service for transferring strengthen star levels between equipment items.
 * <p>
 * The transfer moves the star level number itself (with optional decay),
 * not the source equipment's specific effects. The target item is rebuilt
 * using its own matched recipe at the transferred star level.
 */
public final class StrengthenTransferService {

    public record TransferRequest(ItemStack source, ItemStack target, double decayRate) {

        public TransferRequest {
            decayRate = Numbers.clamp(decayRate, 0D, 1D);
        }
    }

    public record TransferResult(boolean success, String errorKey, ItemStack resultItem, int transferredStar) {

        public static TransferResult failure(String errorKey) {
            return new TransferResult(false, errorKey, null, 0);
        }
    }

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;

    public StrengthenTransferService(EmakiStrengthenPlugin plugin, StrengthenAttemptService attemptService) {
        this.plugin = plugin;
        this.attemptService = attemptService;
    }

    /**
     * Transfer strengthen star level from source to target.
     *
     * @param player  the player performing the transfer
     * @param request the transfer request containing source, target, and decay rate
     * @return the transfer result
     */
    public TransferResult transfer(Player player, TransferRequest request) {
        if (player == null || request == null) {
            return TransferResult.failure("strengthen.transfer.invalid_request");
        }
        ItemStack source = request.source();
        ItemStack target = request.target();
        if (source == null || source.getType().isAir() || target == null || target.getType().isAir()) {
            return TransferResult.failure("strengthen.transfer.invalid_items");
        }

        // Read source state
        StrengthenState sourceState = attemptService.readState(source);
        if (!sourceState.hasLayer() || sourceState.currentStar() <= 0) {
            return TransferResult.failure("strengthen.transfer.source_not_strengthened");
        }

        // Resolve target recipe
        StrengthenState targetState = attemptService.readState(target);
        if (!targetState.eligible()) {
            return TransferResult.failure("strengthen.transfer.target_not_eligible");
        }
        StrengthenRecipe targetRecipe = plugin.recipeLoader().get(targetState.recipeId());
        if (targetRecipe == null) {
            return TransferResult.failure("strengthen.transfer.target_no_recipe");
        }

        // Calculate transferred star with decay
        int sourceStar = sourceState.currentStar();
        int transferredStar = (int) Math.floor(sourceStar * request.decayRate());
        transferredStar = Numbers.clamp(transferredStar, 0, targetRecipe.limits().maxStar());

        if (transferredStar <= 0) {
            return TransferResult.failure("strengthen.transfer.star_too_low");
        }

        // Check if target recipe has branch tree and transferred star exceeds fork point
        if (targetRecipe.hasBranchTree() && targetRecipe.branchTree().needsForkSelection("", transferredStar)) {
            // Branch selection needed — return a special result indicating GUI interaction required
            return TransferResult.failure("strengthen.transfer.branch_selection_required");
        }

        // Apply transferred state to target using admin state API
        ItemStack result = attemptService.applyAdminState(target, transferredStar, 0, targetState.recipeId());
        if (result == null) {
            return TransferResult.failure("strengthen.transfer.rebuild_failed");
        }

        return new TransferResult(true, "", result, transferredStar);
    }
}
