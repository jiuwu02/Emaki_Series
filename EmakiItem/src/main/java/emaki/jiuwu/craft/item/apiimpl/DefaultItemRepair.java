package emaki.jiuwu.craft.item.apiimpl;

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
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemRepair;
import emaki.jiuwu.craft.item.api.model.RepairCurrencyView;
import emaki.jiuwu.craft.item.api.model.RepairOutcome;
import emaki.jiuwu.craft.item.api.model.RepairQuoteView;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.service.ItemRepairService;

/** Runtime-backed {@link ItemRepair}. */
public final class DefaultItemRepair implements ItemRepair {

    private final EmakiItemPlugin plugin;

    public DefaultItemRepair(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isDisabled(@Nullable ItemStack itemStack) {
        return plugin.repairService() != null && plugin.repairService().isDisabled(itemStack);
    }

    @Override
    public @NotNull EmakiResult<Unit> markDisabled(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("item.repair.item_required");
        }
        ItemRepairService service = plugin.repairService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            service.markDisabled(itemStack);
            return service.isDisabled(itemStack)
                    ? EmakiResult.ok()
                    : EmakiResult.internalError("item.repair.disable_commit_failed");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.repair.disable_internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<Unit> clearDisabled(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("item.repair.item_required");
        }
        ItemRepairService service = plugin.repairService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            service.clearDisabled(itemStack);
            return !service.isDisabled(itemStack)
                    ? EmakiResult.ok()
                    : EmakiResult.internalError("item.repair.enable_commit_failed");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.repair.enable_internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<RepairQuoteView> quote(@Nullable Player player,
            @Nullable ItemStack itemStack) {
        EmakiResult<ResolvedRepair> resolved = resolve(player, itemStack);
        if (resolved.isFailure()) {
            return resolved.retypeFailure();
        }
        ResolvedRepair repair = resolved.orElse(null);
        try {
            ItemRepairService.EconomyQuote quote = plugin.repairService()
                    .quoteEconomy(player, repair.definition(), itemStack);
            RepairQuoteView view = toQuote(repair.definition().id(), quote);
            if (quote.success()
                    || "repair.error.insufficient_funds".equals(quote.errorKey())
                    || "repair.error.economy_provider_unavailable".equals(quote.errorKey())) {
                return EmakiResult.success(view);
            }
            return failure(quote.errorKey(), quote.replacements());
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.repair.quote_internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<RepairOutcome> repair(@Nullable Player player,
            @Nullable ItemStack itemStack) {
        EmakiResult<ResolvedRepair> resolved = resolve(player, itemStack);
        if (resolved.isFailure()) {
            return resolved.retypeFailure();
        }
        ResolvedRepair repair = resolved.orElse(null);
        ItemRepairService service = plugin.repairService();
        try {
            ItemRepairService.RepairResult result = service.repairWithEconomy(player, repair.definition(), itemStack);
            if (!result.success()) {
                return failure(result.errorKey(), result.replacements());
            }
            return EmakiResult.success(new RepairOutcome(
                    repair.definition().id(),
                    result.restoreAmount(),
                    service.currentDamage(itemStack),
                    service.maxDamage(itemStack),
                    service.isDisabled(itemStack)));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.repair.internal_error");
        }
    }

    private EmakiResult<ResolvedRepair> resolve(Player player, ItemStack itemStack) {
        if (player == null) {
            return EmakiResult.invalidInput("item.player.required");
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("item.repair.item_required");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin.threadOwnership() == null || plugin.identifier() == null
                || plugin.idResolver() == null || plugin.repairService() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        String id = plugin.identifier().identify(itemStack);
        if (Texts.isBlank(id)) {
            return EmakiResult.rejected("item.repair.not_managed");
        }
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(id);
        if (definition == null) {
            return EmakiResult.notFound("item.definition.not_found");
        }
        if (!definition.repair().enabled()) {
            return EmakiResult.rejected("item.repair.disabled");
        }
        if (!definition.repair().hasEconomyRepair()) {
            return EmakiResult.rejected("item.repair.economy_disabled");
        }
        return EmakiResult.success(new ResolvedRepair(definition));
    }

    private RepairQuoteView toQuote(String itemId, ItemRepairService.EconomyQuote quote) {
        List<RepairCurrencyView> currencies = quote.currencies().stream()
                .map(currency -> new RepairCurrencyView(
                        currency.cost() == null ? "" : currency.cost().provider(),
                        currency.cost() == null ? "" : currency.cost().currencyId(),
                        currency.cost() == null ? "" : currency.cost().effectiveDisplayName(),
                        currency.amount(),
                        currency.balance(),
                        currency.supported()))
                .toList();
        String reasonKey = Texts.isBlank(quote.errorKey()) ? "" : "item." + quote.errorKey();
        return new RepairQuoteView(itemId, quote.currentDamage(), quote.maxDamage(), quote.restoreAmount(),
                reasonKey, currencies);
    }

    private <T> EmakiResult<T> failure(String reasonKey, Map<String, Object> placeholders) {
        if ("repair.error.cancelled".equals(reasonKey)) {
            return EmakiResult.failure(FailureKind.CANCELLED, "item.repair.cancelled", placeholders);
        }
        if (Texts.isBlank(reasonKey)) {
            return EmakiResult.internalError("item.repair.unknown_failure");
        }
        return EmakiResult.failure(FailureKind.REJECTED, "item." + reasonKey, placeholders);
    }

    private record ResolvedRepair(EmakiItemDefinition definition) {
    }
}
