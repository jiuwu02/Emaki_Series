package emaki.jiuwu.craft.item.apiimpl;

import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemOperations;
import emaki.jiuwu.craft.item.api.model.ItemRefreshSummary;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemRefreshResult;

/** Runtime-backed {@link ItemOperations}. */
public final class DefaultItemOperations implements ItemOperations {

    private static final String STACK_REFRESH_TRIGGER = "command";

    private final EmakiItemPlugin plugin;

    public DefaultItemOperations(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<ItemStack> create(@Nullable String id, int amount) {
        if (Texts.isBlank(id)) {
            return EmakiResult.invalidInput("item.definition.id_required");
        }
        if (amount <= 0) {
            return EmakiResult.invalidInput("item.create.amount_positive");
        }
        EmakiItemFactory factory = plugin.itemFactory();
        if (factory == null || plugin.idResolver() == null || plugin.threadOwnership() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isGlobalOwned()) {
            return EmakiResult.wrongThread();
        }
        if (plugin.idResolver().resolveDefinition(id) == null) {
            return EmakiResult.notFound("item.definition.not_found");
        }
        try {
            EmakiItemFactory.CreateResult result = factory.createDetailed(id, amount);
            if (result.cancelled()) {
                return EmakiResult.failure(FailureKind.CANCELLED, "item.create.cancelled");
            }
            return result.itemStack() == null
                    ? EmakiResult.internalError("item.create.build_failed")
                    : EmakiResult.success(result.itemStack());
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.create.internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<ItemStack> refresh(@Nullable ItemStack itemStack) {
        EmakiResult<PreparedRefresh> prepared = prepareRefresh(itemStack, false);
        if (prepared.isFailure()) {
            return prepared.retypeFailure();
        }
        PreparedRefresh refresh = prepared.orElse(null);
        if (!refresh.aliasMigration()
                && !refresh.definition().updatePolicy().resolve().triggerEnabled(STACK_REFRESH_TRIGGER)) {
            return EmakiResult.rejected("item.refresh.trigger_disabled");
        }
        try {
            return EmakiResult.success(plugin.updateService().updateIfNeeded(itemStack, STACK_REFRESH_TRIGGER));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.refresh.internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<ItemStack> forceRefresh(@Nullable ItemStack itemStack) {
        EmakiResult<PreparedRefresh> prepared = prepareRefresh(itemStack, true);
        if (prepared.isFailure()) {
            return prepared.retypeFailure();
        }
        try {
            ItemStack refreshed = plugin.updateService().forceUpdate(itemStack);
            return refreshed == null
                    ? EmakiResult.internalError("item.refresh.build_failed")
                    : EmakiResult.success(refreshed);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.refresh.internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<ItemRefreshSummary> refreshPlayer(@Nullable Player player,
            @Nullable String trigger) {
        EmakiResult<Unit> playerCheck = checkPlayer(player, trigger);
        if (playerCheck.isFailure()) {
            return playerCheck.retypeFailure();
        }
        EmakiItemUpdateService service = plugin.updateService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            ItemRefreshResult result = service.updatePlayerItemsDetailed(
                    player, List.of(trigger), Set.of(), true, Set.of(RefreshFullReason.EXPLICIT_FULL));
            return mapBatch(result, "item.refresh.partial_conflicts");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.refresh.internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<ItemRefreshSummary> refreshEquippedSets(@Nullable Player player,
            @Nullable String trigger) {
        EmakiResult<Unit> playerCheck = checkPlayer(player, trigger);
        if (playerCheck.isFailure()) {
            return playerCheck.retypeFailure();
        }
        EmakiItemSetService service = plugin.setService();
        if (service == null || plugin.appConfig() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.appConfig().setBonus().enabled()) {
            return EmakiResult.rejected("item.set_bonus.disabled");
        }
        if (!plugin.appConfig().setBonus().triggerEnabled(trigger)) {
            return EmakiResult.rejected("item.set_bonus.trigger_disabled");
        }
        try {
            ItemRefreshResult result = service.refreshListenerScopeDetailed(
                    player, List.of(trigger), Set.of(), true, true, Set.of(RefreshFullReason.EXPLICIT_FULL));
            return mapBatch(result, "item.set_bonus.partial_conflicts");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.set_bonus.internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> openRepairGui(@Nullable Player player) {
        EmakiResult<Unit> playerCheck = checkPlayer(player, "repair_gui");
        if (playerCheck.isFailure()) {
            return playerCheck;
        }
        if (plugin.repairGuiService() == null) {
            return EmakiResult.unavailable();
        }
        try {
            return plugin.repairGuiService().open(player)
                    ? EmakiResult.ok()
                    : EmakiResult.rejected("item.repair_gui.open_rejected");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.repair_gui.internal_error");
        }
    }

    private EmakiResult<PreparedRefresh> prepareRefresh(ItemStack itemStack, boolean force) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("item.refresh.item_required");
        }
        EmakiItemUpdateService updateService = plugin.updateService();
        EmakiItemIdentifier identifier = plugin.identifier();
        if (updateService == null || identifier == null || plugin.idResolver() == null) {
            return EmakiResult.unavailable();
        }
        String id = identifier.identify(itemStack);
        if (Texts.isBlank(id)) {
            return EmakiResult.rejected("item.refresh.not_managed");
        }
        boolean aliasMigration = plugin.idResolver().aliasFor(id) != null;
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(id);
        if (definition == null) {
            return EmakiResult.notFound("item.definition.not_found");
        }
        if (force && !aliasMigration && !definition.updatePolicy().resolve().enabled()) {
            return EmakiResult.rejected("item.refresh.disabled");
        }
        return EmakiResult.success(new PreparedRefresh(definition, aliasMigration));
    }

    private EmakiResult<Unit> checkPlayer(Player player, String trigger) {
        if (player == null) {
            return EmakiResult.invalidInput("item.player.required");
        }
        if (Texts.isBlank(trigger)) {
            return EmakiResult.invalidInput("item.refresh.trigger_required");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin.threadOwnership() == null) {
            return EmakiResult.unavailable();
        }
        return plugin.threadOwnership().isEntityOwned(player) ? EmakiResult.ok() : EmakiResult.wrongThread();
    }

    private record PreparedRefresh(EmakiItemDefinition definition, boolean aliasMigration) {
    }

    private EmakiResult<ItemRefreshSummary> mapBatch(ItemRefreshResult result, String partialReason) {
        if (result == null) {
            return EmakiResult.internalError("item.refresh.no_result");
        }
        ItemRefreshSummary summary = new ItemRefreshSummary(
                result.changed(),
                result.conflicts(),
                result.updateScannedSlots(),
                result.setScannedSlots(),
                result.ledgerDecodes(),
                result.setCompiles(),
                result.cacheHit(),
                result.cacheValid(),
                result.requestedScope().name(),
                result.actualUpdateScope().name(),
                result.actualSetScope().name(),
                result.fullReasons().stream().map(Enum::name).sorted().toList(),
                result.effectiveTrigger(),
                result.elapsedNanos());
        return result.conflicts() > 0
                ? EmakiResult.partial(summary, partialReason)
                : EmakiResult.success(summary);
    }
}
