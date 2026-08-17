package emaki.jiuwu.craft.strengthen.apiimpl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.StrengthenOperations;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenTransferService;

public final class DefaultStrengthenOperations implements StrengthenOperations {

    private final EmakiStrengthenPlugin plugin;

    public DefaultStrengthenOperations(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<AttemptResult> attempt(@Nullable Player player,
            @Nullable AttemptContext context) {
        EmakiResult<AttemptResult> validation = validatePlayer(player, "strengthen.error.no_player");
        if (validation != null) {
            return validation;
        }
        if (context == null || context.targetItem() == null) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        StrengthenAttemptService service = plugin.attemptService();
        if (service == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        try {
            AttemptResult result = service.attempt(player, context);
            if (result == null) {
                return EmakiResult.internalError("strengthen.error.internal");
            }
            if (result.committed()) {
                return EmakiResult.success(result);
            }
            String reasonKey = Texts.isBlank(result.errorKey())
                    ? "strengthen.error.attempt_failed" : result.errorKey();
            if (result.compensationPending()) {
                return EmakiResult.partial(result, reasonKey);
            }
            return EmakiResult.failure(attemptFailureKind(reasonKey), reasonKey, result.replacements());
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<StrengthenTransferOutcome> transfer(@Nullable Player player,
            @Nullable ItemStack source,
            @Nullable ItemStack target,
            double decayRate) {
        EmakiResult<StrengthenTransferOutcome> validation = validatePlayer(player,
                "strengthen.transfer.invalid_request");
        if (validation != null) {
            return validation;
        }
        if (source == null || source.getType().isAir() || target == null || target.getType().isAir()
                || !Double.isFinite(decayRate)) {
            return EmakiResult.invalidInput("strengthen.transfer.invalid_items");
        }
        StrengthenTransferService service = plugin.transferService();
        if (service == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        try {
            StrengthenTransferService.TransferResult result = service.transfer(player,
                    new StrengthenTransferService.TransferRequest(source, target, decayRate));
            if (result == null) {
                return EmakiResult.internalError("strengthen.transfer.internal");
            }
            if (result.success() && result.resultItem() != null) {
                return EmakiResult.success(new StrengthenTransferOutcome(result.resultItem(), result.transferredStar()));
            }
            String reasonKey = Texts.isBlank(result.errorKey())
                    ? "strengthen.transfer.failed" : result.errorKey();
            return EmakiResult.failure(transferFailureKind(reasonKey), reasonKey);
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.transfer.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<ItemStack> rebuild(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        StrengthenAttemptService service = plugin.attemptService();

        if (service == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        try {
            StrengthenState state = service.readState(itemStack);
            if (!state.hasLayer()) {
                return EmakiResult.partial(itemStack.clone(), "strengthen.rebuild.not_applicable");
            }
            if (Texts.isBlank(state.recipeId()) || plugin.recipeLoader() == null
                    || plugin.recipeLoader().get(state.recipeId()) == null) {
                return EmakiResult.notFound("strengthen.error.no_recipe");
            }
            ItemStack rebuilt = service.rebuild(itemStack);
            return rebuilt == null
                    ? EmakiResult.internalError("strengthen.error.rebuild_failed")
                    : EmakiResult.success(rebuilt);
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.rebuild_failed");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> openGui(@Nullable Player player) {
        EmakiResult<Unit> validation = validatePlayer(player, "strengthen.error.no_player");
        if (validation != null) {
            return validation;
        }
        if (plugin.strengthenGuiService() == null || plugin.attemptService() == null
                || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        return plugin.strengthenGuiService().open(player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "strengthen.error.gui_open_failed");
    }

    @Override
    public @NotNull EmakiResult<ItemStack> refreshItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        StrengthenRefreshService service = plugin.refreshService();

        if (service == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        try {
            ItemStack refreshed = service.refreshItem(itemStack);
            if (refreshed == null) {
                return EmakiResult.internalError("strengthen.refresh.failed");
            }
            return refreshed == itemStack
                    ? EmakiResult.partial(itemStack.clone(), "strengthen.refresh.not_applicable")
                    : EmakiResult.success(refreshed);
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.refresh.failed");
        }
    }

    @Override
    public @NotNull EmakiResult<Integer> refreshPlayer(@Nullable Player player) {
        EmakiResult<Integer> validation = validatePlayer(player, "strengthen.error.no_player");
        if (validation != null) {
            return validation;
        }
        StrengthenRefreshService service = plugin.refreshService();
        if (service == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiResult.success(service.refreshPlayerItems(player));
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.refresh.player_failed");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> registerEnhancementTarget(@Nullable EnhancementTargetProvider provider) {
        if (provider == null) {
            return EmakiResult.invalidInput("strengthen.enhancement.no_provider");
        }
        String providerId;
        try {
            providerId = provider.id();
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.provider_rejected");
        }
        if (Texts.isBlank(providerId)) {
            return EmakiResult.invalidInput("strengthen.enhancement.blank_provider_id");
        }
        EnhancementTargetRegistry registry = plugin.enhancementTargetRegistry();
        if (registry == null) {
            return EmakiResult.unavailable();
        }
        try {
            registry.register(provider);
            return EmakiResult.ok();
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.provider_rejected");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> unregisterEnhancementTarget(@Nullable String providerId) {
        if (Texts.isBlank(providerId)) {
            return EmakiResult.invalidInput("strengthen.enhancement.blank_provider_id");
        }
        EnhancementTargetRegistry registry = plugin.enhancementTargetRegistry();
        if (registry == null) {
            return EmakiResult.unavailable();
        }
        if (registry.get(providerId) == null) {
            return EmakiResult.notFound("strengthen.enhancement.provider_not_found");
        }
        registry.unregister(providerId);
        return EmakiResult.ok();
    }

    private <T> EmakiResult<T> validatePlayer(Player player, String nullReasonKey) {
        if (player == null) {
            return EmakiResult.invalidInput(nullReasonKey);
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin.threadOwnership() == null || !plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return null;
    }

    private static FailureKind attemptFailureKind(String reasonKey) {
        return switch (reasonKey) {
            case "strengthen.error.cancelled" -> FailureKind.CANCELLED;
            case "strengthen.error.no_recipe" -> FailureKind.NOT_FOUND;
            case "strengthen.error.internal", "strengthen.error.rebuild_failed",
                    "strengthen.error.economy_provider_unavailable" -> FailureKind.INTERNAL_ERROR;
            default -> FailureKind.REJECTED;
        };
    }

    private static FailureKind transferFailureKind(String reasonKey) {
        return switch (reasonKey) {
            case "strengthen.transfer.invalid_request", "strengthen.transfer.invalid_items" -> FailureKind.INVALID_INPUT;
            case "strengthen.transfer.target_no_recipe" -> FailureKind.NOT_FOUND;
            case "strengthen.transfer.cancelled" -> FailureKind.CANCELLED;
            case "strengthen.transfer.wrong_thread" -> FailureKind.WRONG_THREAD;
            case "strengthen.transfer.rebuild_failed", "strengthen.transfer.internal" -> FailureKind.INTERNAL_ERROR;
            default -> FailureKind.REJECTED;
        };
    }
}
