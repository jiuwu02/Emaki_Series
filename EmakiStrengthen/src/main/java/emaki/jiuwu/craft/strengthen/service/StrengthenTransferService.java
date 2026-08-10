package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.event.StrengthenTransferCompletedEvent;
import emaki.jiuwu.craft.strengthen.api.event.StrengthenTransferEvent;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;

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

    public TransferResult transfer(Player player, TransferRequest request) {
        if (player == null || request == null) {
            return TransferResult.failure("strengthen.transfer.invalid_request");
        }
        ItemStack source = request.source();
        ItemStack target = request.target();
        if (source == null || source.getType().isAir() || target == null || target.getType().isAir()) {
            return TransferResult.failure("strengthen.transfer.invalid_items");
        }
        if (plugin.threadOwnership() == null || !plugin.threadOwnership().isEntityOwned(player)) {
            return TransferResult.failure("strengthen.transfer.wrong_thread");
        }

        StrengthenState sourceState = attemptService.readState(source);
        if (!sourceState.hasLayer() || sourceState.currentStar() <= 0) {
            return TransferResult.failure("strengthen.transfer.source_not_strengthened");
        }

        StrengthenState targetState = attemptService.readState(target);
        if (!targetState.eligible()) {
            return TransferResult.failure("strengthen.transfer.target_not_eligible");
        }
        StrengthenRecipe targetRecipe = plugin.recipeLoader().get(targetState.recipeId());
        if (targetRecipe == null) {
            return TransferResult.failure("strengthen.transfer.target_no_recipe");
        }

        int sourceStar = sourceState.currentStar();
        int transferredStar = (int) Math.floor(sourceStar * request.decayRate());
        transferredStar = Numbers.clamp(transferredStar, 0, targetRecipe.limits().maxStar());

        StrengthenTransferEvent transferEvent = new StrengthenTransferEvent(
                player, source, target, targetState.recipeId(), sourceStar, transferredStar, request.decayRate());
        Bukkit.getPluginManager().callEvent(transferEvent);
        if (transferEvent.isCancelled()) {
            return TransferResult.failure("strengthen.transfer.cancelled");
        }

        transferredStar = Numbers.clamp(transferEvent.getTransferredStar(), 0, targetRecipe.limits().maxStar());

        if (transferredStar <= 0) {
            return TransferResult.failure("strengthen.transfer.star_too_low");
        }

        if (targetRecipe.hasBranchTree() && targetRecipe.branchTree().needsForkSelection("", transferredStar)) {
            return TransferResult.failure("strengthen.transfer.branch_selection_required");
        }

        ItemStack result = attemptService.applyAdminState(target, transferredStar, 0, targetState.recipeId());
        if (result == null) {
            return TransferResult.failure("strengthen.transfer.rebuild_failed");
        }

        StrengthenTransferOutcome outcome = new StrengthenTransferOutcome(result, transferredStar);
        try {
            Bukkit.getPluginManager().callEvent(new StrengthenTransferCompletedEvent(
                    player,
                    source,
                    target,
                    targetState.recipeId(),
                    sourceStar,
                    request.decayRate(),
                    outcome));
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Strengthen transfer result event dispatch failed: " + exception.getMessage());
        }
        return new TransferResult(true, "", outcome.resultItem(), outcome.transferredStar());
    }
}
