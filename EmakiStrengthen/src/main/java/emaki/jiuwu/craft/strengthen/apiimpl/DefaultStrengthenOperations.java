package emaki.jiuwu.craft.strengthen.apiimpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import emaki.jiuwu.craft.strengthen.api.model.AttemptOutcome;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptOutcome;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementOperationView;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityStateView;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptResult;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptService;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayer;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryProgressService;
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityState;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;
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
    public @NotNull EmakiResult<EnhancementAttemptOutcome> attemptEnhancement(@Nullable Player player,
            @Nullable EnhancementAttemptContext context) {
        EmakiResult<EnhancementAttemptOutcome> validation = validatePlayer(player,
                "strengthen.enhancement.no_player");
        if (validation != null) {
            return validation;
        }
        if (context == null || context.targetItem() == null || Texts.isBlank(context.recipeId())) {
            return EmakiResult.invalidInput("strengthen.enhancement.invalid_request");
        }
        EnhancementAttemptService service = plugin.enhancementAttemptService();
        EnhancementRecipe recipe = plugin.enhancementRecipeLoader() == null
                ? null
                : plugin.enhancementRecipeLoader().get(context.recipeId());
        if (service == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        if (recipe == null) {
            return EmakiResult.notFound("strengthen.enhancement.recipe_not_found");
        }
        try {
            ItemStack target = context.targetItem();
            List<ItemStack> materials = new ArrayList<>(context.materialInputs());
            EnhancementAttemptResult result = service.attempt(player, recipe, target, materials,
                    context.operationId());
            if (result == null) {
                return EmakiResult.internalError("strengthen.enhancement.internal");
            }
            Map<String, Object> replacements = new LinkedHashMap<>();
            result.toPlaceholders().forEach(replacements::put);
            replacements.put("recipe_id", recipe.id());
            String operationId = result.placeholders().getOrDefault("operation_id", context.operationId());
            replacements.put("operation_id", operationId);
            AttemptOutcome outcome = result.committed()
                    ? (result.success() ? AttemptOutcome.COMMITTED_SUCCESS : AttemptOutcome.COMMITTED_FAILURE)
                    : ("strengthen.error.compensation_pending".equals(result.errorKey())
                            ? AttemptOutcome.COMPENSATION_PENDING
                            : AttemptOutcome.NOT_COMMITTED);
            EnhancementAttemptOutcome payload = new EnhancementAttemptOutcome(
                    outcome,
                    result.success(),
                    recipe.id(),
                    target,
                    materials,
                    result.previousLevel(),
                    result.resultingLevel(),
                    result.successRate(),
                    result.pityResult(),
                    operationId,
                    replacements
            );
            if (result.committed()) {
                return EmakiResult.success(payload);
            }
            String reasonKey = Texts.isBlank(result.errorKey())
                    ? "strengthen.enhancement.attempt_failed"
                    : result.errorKey();
            if (outcome == AttemptOutcome.COMPENSATION_PENDING) {
                return EmakiResult.partial(payload, reasonKey);
            }
            return EmakiResult.failure(enhancementFailureKind(reasonKey), reasonKey, replacements);
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.internal");
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
    public @NotNull EmakiResult<EnhancementOperationView> enhancementOperation(@Nullable String operationId) {
        if (Texts.isBlank(operationId)) {
            return EmakiResult.invalidInput("strengthen.enhancement.invalid_request");
        }
        EnhancementAttemptService service = plugin.enhancementAttemptService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            EnhancementOperationView view = service.operationView(operationId);
            return view == null
                    ? EmakiResult.notFound("strengthen.enhancement.operation_not_found")
                    : EmakiResult.success(view);
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<List<EnhancementOperationView>> enhancementOperations() {
        EnhancementAttemptService service = plugin.enhancementAttemptService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiResult.success(service.operationViews());
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<List<EnhancementPityStateView>> pityStates(@Nullable String group) {
        InMemoryPityStateStore store = plugin.pityStateStore();
        if (store == null) {
            return EmakiResult.unavailable();
        }
        try {
            String filter = Texts.lower(group);
            List<EnhancementPityStateView> views = new ArrayList<>();
            store.snapshot().forEach((composite, state) -> {
                String[] parts = composite.split("\\|", 3);
                if (parts.length != 3) {
                    return;
                }
                EnhancementPityStateView view = new EnhancementPityStateView(parts[0], parts[1], parts[2],
                        state.getCounter(), state.getLastTriggerTime(), state.isTriggered());
                if (Texts.isBlank(filter) || filter.equals(Texts.lower(view.baseGroup()))) {
                    views.add(view);
                }
            });
            return EmakiResult.success(List.copyOf(views));
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> setPityCounter(@Nullable String scope,
            @Nullable String group,
            @Nullable String ownerKey,
            int counter) {
        if (Texts.isBlank(group) || Texts.isBlank(ownerKey)) {
            return EmakiResult.invalidInput("strengthen.enhancement.invalid_request");
        }
        PityScopeEnum resolvedScope = parsePityScope(scope);
        if (resolvedScope == null) {
            return EmakiResult.invalidInput("strengthen.enhancement.invalid_request");
        }
        InMemoryPityStateStore store = plugin.pityStateStore();
        if (store == null) {
            return EmakiResult.unavailable();
        }
        try {
            PityState existing = store.load(resolvedScope.name(), group, ownerKey);
            store.save(resolvedScope.name(), group, ownerKey, new PityState(Math.max(0, counter),
                    existing == null ? System.currentTimeMillis() : existing.getLastTriggerTime(),
                    existing != null && existing.isTriggered()));
            return EmakiResult.ok();
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<Integer> clearPityGroup(@Nullable String group) {
        if (Texts.isBlank(group)) {
            return EmakiResult.invalidInput("strengthen.enhancement.invalid_request");
        }
        InMemoryPityStateStore store = plugin.pityStateStore();
        if (store == null) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiResult.success(store.removeGroup(group));
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.enhancement.internal");
        }
    }

    @Override
    public @NotNull EmakiResult<ItemMasteryView> setMastery(@Nullable ItemStack itemStack, double totalExp) {
        if (itemStack == null || itemStack.getType().isAir() || !Double.isFinite(totalExp) || totalExp < 0D) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        MasteryProgressService service = plugin.masteryProgressService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            if (!service.overwriteTotalExp(itemStack, totalExp)) {
                return EmakiResult.internalError("strengthen.error.state_write_failed");
            }
            MasteryLayer written = service.read(itemStack);
            return written == null
                    ? EmakiResult.internalError("strengthen.error.state_read_failed")
                    : EmakiResult.success(written.toView());
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.state_write_failed");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> clearMastery(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        MasteryProgressService service = plugin.masteryProgressService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            if (service.read(itemStack) == null) {
                return EmakiResult.notFound("strengthen.mastery.absent");
            }
            return service.clear(itemStack)
                    ? EmakiResult.ok()
                    : EmakiResult.internalError("strengthen.error.state_write_failed");
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.state_write_failed");
        }
    }

    private static @Nullable PityScopeEnum parsePityScope(@Nullable String scope) {
        if (Texts.isBlank(scope)) {
            return null;
        }
        for (PityScopeEnum candidate : PityScopeEnum.values()) {
            if (candidate.name().equalsIgnoreCase(scope.trim())) {
                return candidate;
            }
        }
        return null;
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

    private static FailureKind enhancementFailureKind(String reasonKey) {
        return switch (reasonKey) {
            case "strengthen.enhancement.recipe_not_found",
                    "strengthen.enhancement.provider_not_found" -> FailureKind.NOT_FOUND;
            case "strengthen.enhancement.invalid_request",
                    "strengthen.enhancement.no_target" -> FailureKind.INVALID_INPUT;
            case "strengthen.enhancement.cancelled" -> FailureKind.CANCELLED;
            case "strengthen.enhancement.internal",
                    "strengthen.enhancement.write_failed",
                    "strengthen.enhancement.economy_unavailable" -> FailureKind.INTERNAL_ERROR;
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
