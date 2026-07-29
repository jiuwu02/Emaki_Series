package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

public final class StrengthenEconomyService {

    public enum CompensationOutcome {
        NOT_REQUIRED,
        COMPLETED,
        PENDING
    }

    public record ChargeResult(boolean success, String errorKey, List<AttemptCost> appliedCosts) {

        public ChargeResult {
            errorKey = errorKey == null ? "" : errorKey;
            appliedCosts = appliedCosts == null ? List.of() : List.copyOf(appliedCosts);
        }

        public static ChargeResult success(List<AttemptCost> appliedCosts) {
            return new ChargeResult(true, "", appliedCosts);
        }

        public static ChargeResult failure(String errorKey, List<AttemptCost> pendingCompensation) {
            return new ChargeResult(false, errorKey, pendingCompensation);
        }

        public CompensationOutcome compensationOutcome() {
            return success || appliedCosts.isEmpty() ? CompensationOutcome.NOT_REQUIRED : CompensationOutcome.PENDING;
        }

        public boolean compensationPending() {
            return compensationOutcome() == CompensationOutcome.PENDING;
        }
    }

    public record RefundResult(CompensationOutcome outcome,
            List<AttemptCost> refundedCosts,
            List<AttemptCost> pendingCosts,
            String message) {

        public RefundResult {
            outcome = outcome == null ? CompensationOutcome.PENDING : outcome;
            refundedCosts = refundedCosts == null ? List.of() : List.copyOf(refundedCosts);
            pendingCosts = pendingCosts == null ? List.of() : List.copyOf(pendingCosts);
            message = message == null ? "" : message;
        }

        public boolean success() {
            return outcome != CompensationOutcome.PENDING;
        }
    }

    private final EmakiStrengthenPlugin plugin;
    private final Supplier<EconomyManager> economyManagerSupplier;
    private final ItemSourceService itemSourceService;

    public StrengthenEconomyService(EmakiStrengthenPlugin plugin,
            Supplier<EconomyManager> economyManagerSupplier,
            ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.economyManagerSupplier = economyManagerSupplier;
        this.itemSourceService = itemSourceService;
    }

    public List<AttemptCost> quoteCosts(StrengthenRecipe recipe, int targetStar) {
        if (recipe == null) {
            return List.of();
        }
        List<AttemptCost> result = new ArrayList<>();
        for (StrengthenRecipe.CurrencyEntry currency : recipe.effectiveCurrencies(targetStar)) {
            if (currency == null || Texts.isBlank(currency.provider())) {
                continue;
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("base_cost", currency.baseCost());
            variables.put("star", targetStar);
            double evaluated;
            try {
                evaluated = ExpressionEngine.evaluate(currency.costFormula(), variables);
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure("quote", "", exception);
                continue;
            }
            if (!Double.isFinite(evaluated) || evaluated <= 0D) {
                continue;
            }
            long amount = evaluated >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, Math.round(evaluated));
            if (amount <= 0L) {
                continue;
            }
            result.add(new AttemptCost(
                    currency.provider(),
                    currency.currencyId(),
                    resolveCostDisplayName(currency),
                    amount
            ));
        }
        return List.copyOf(result);
    }

    public ChargeResult charge(Player player, List<AttemptCost> costs) {
        return charge(player, costs, "");
    }

    public ChargeResult charge(Player player, List<AttemptCost> costs, String operationId) {
        if (player == null || player.getInventory() == null) {
            return ChargeResult.failure("strengthen.error.economy_provider_unavailable", List.of());
        }
        if (costs != null && costs.stream().anyMatch(cost -> !validCost(cost))) {
            return ChargeResult.failure("strengthen.error.economy_provider_unavailable", List.of());
        }
        List<AttemptCost> normalized = aggregateCosts(costs);
        EconomyManager economyManager = safeEconomyManager(operationId);
        for (AttemptCost cost : normalized) {
            if (!validCost(cost)) {
                return ChargeResult.failure("strengthen.error.economy_provider_unavailable", List.of());
            }
            if (cost.itemCost()) {
                try {
                    if (countItemCost(player, cost.currencyId()) < cost.amount()) {
                        return ChargeResult.failure("strengthen.error.insufficient_funds", List.of());
                    }
                } catch (RuntimeException | LinkageError exception) {
                    logProviderFailure("item-balance", operationId, exception);
                    return ChargeResult.failure("strengthen.error.economy_provider_unavailable", List.of());
                }
                continue;
            }
            BalanceRead balance = readBalance(economyManager, player, cost, operationId);
            if (!balance.valid()) {
                return ChargeResult.failure("strengthen.error.economy_provider_unavailable", List.of());
            }
            if (balance.value() + 1.0E-9D < cost.amount()) {
                return ChargeResult.failure("strengthen.error.insufficient_funds", List.of());
            }
        }

        List<InventoryItemUtil.RemovalPlan> itemDebits = new ArrayList<>();
        List<AttemptCost> itemDebitCosts = new ArrayList<>();
        for (AttemptCost cost : normalized) {
            if (!cost.itemCost()) {
                continue;
            }
            try {
                InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                        player.getInventory(),
                        itemSourceService,
                        ItemSourceUtil.parse(cost.currencyId()),
                        cost.amount()
                );
                if (!plan.complete() || !InventoryItemUtil.applyRemoval(player.getInventory(), plan)) {
                    List<AttemptCost> pending = rollbackItems(player, itemDebits, itemDebitCosts, operationId);
                    return ChargeResult.failure(pending.isEmpty()
                            ? "strengthen.error.insufficient_funds"
                            : "strengthen.error.economy_provider_unavailable", pending);
                }
                itemDebits.add(plan);
                itemDebitCosts.add(cost);
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure("item-charge", operationId, exception);
                List<AttemptCost> pending = rollbackItems(player, itemDebits, itemDebitCosts, operationId);
                return ChargeResult.failure("strengthen.error.economy_provider_unavailable", pending);
            }
        }

        List<CurrencyDebit> currencyDebits = new ArrayList<>();
        List<AttemptCost> uncertainCurrencyDebits = new ArrayList<>();
        for (AttemptCost cost : normalized) {
            if (cost.itemCost()) {
                continue;
            }
            BalanceRead before = readBalance(economyManager, player, cost, operationId);
            if (!before.valid()) {
                return failedWithCompensation(player, economyManager, currencyDebits, uncertainCurrencyDebits,
                        itemDebits, itemDebitCosts, operationId, ActionErrorType.PROVIDER_UNAVAILABLE);
            }
            ActionResult result;
            try {
                result = economyManager.remove(player, cost.provider(), cost.currencyId(), cost.amount());
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure("currency-charge", operationId, exception);
                result = null;
            }
            BalanceRead after = readBalance(economyManager, player, cost, operationId);
            if (after.valid()) {
                double debited = Math.max(0D, before.value() - after.value());
                if (debited > 1.0E-9D) {
                    currencyDebits.add(new CurrencyDebit(cost, debited, before.value()));
                }
            } else if (result != null && result.success()) {
                currencyDebits.add(new CurrencyDebit(cost, cost.amount(), before.value()));
            } else {
                uncertainCurrencyDebits.add(cost);
            }
            if (result == null || !result.success() || !after.valid()
                    || before.value() - after.value() + 1.0E-9D < cost.amount()) {
                ActionErrorType errorType = result == null ? ActionErrorType.PROVIDER_UNAVAILABLE : result.errorType();
                return failedWithCompensation(player, economyManager, currencyDebits, uncertainCurrencyDebits,
                        itemDebits, itemDebitCosts, operationId, errorType);
            }
        }
        return ChargeResult.success(normalized);
    }

    private ChargeResult failedWithCompensation(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits,
            List<AttemptCost> uncertainCurrencyDebits,
            List<InventoryItemUtil.RemovalPlan> itemDebits,
            List<AttemptCost> itemDebitCosts,
            String operationId,
            ActionErrorType errorType) {
        List<AttemptCost> pending = new ArrayList<>();
        pending.addAll(rollbackCurrencies(player, economyManager, currencyDebits, operationId));
        if (uncertainCurrencyDebits != null && !uncertainCurrencyDebits.isEmpty()) {
            pending.addAll(uncertainCurrencyDebits);
            logCompensationPending(player, operationId, uncertainCurrencyDebits);
        }
        pending.addAll(rollbackItems(player, itemDebits, itemDebitCosts, operationId));
        String errorKey = pending.isEmpty() && errorType == ActionErrorType.INSUFFICIENT_BALANCE
                ? "strengthen.error.insufficient_funds"
                : "strengthen.error.economy_provider_unavailable";
        return ChargeResult.failure(errorKey, pending);
    }

    private List<AttemptCost> rollbackItems(Player player,
            List<InventoryItemUtil.RemovalPlan> itemDebits,
            List<AttemptCost> itemDebitCosts,
            String operationId) {
        if (itemDebits == null || itemDebits.isEmpty()) {
            return List.of();
        }
        List<AttemptCost> pending = new ArrayList<>();
        for (int index = itemDebits.size() - 1; index >= 0; index--) {
            boolean restored;
            try {
                restored = player != null && player.getInventory() != null
                        && InventoryItemUtil.rollbackRemoval(player.getInventory(), itemDebits.get(index));
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure("item-compensation", operationId, exception);
                restored = false;
            }
            if (!restored && index < itemDebitCosts.size()) {
                pending.add(itemDebitCosts.get(index));
            }
        }
        if (!pending.isEmpty()) {
            logCompensationPending(player, operationId, pending);
        }
        return List.copyOf(pending);
    }

    private List<AttemptCost> rollbackCurrencies(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits,
            String operationId) {
        if (currencyDebits == null || currencyDebits.isEmpty()) {
            return List.of();
        }
        List<AttemptCost> pending = new ArrayList<>();
        for (int index = currencyDebits.size() - 1; index >= 0; index--) {
            CurrencyDebit debit = currencyDebits.get(index);
            boolean restored = false;
            try {
                ActionResult result = economyManager == null ? null
                        : economyManager.add(player, debit.cost().provider(), debit.cost().currencyId(), debit.amount());
                BalanceRead balance = readBalance(economyManager, player, debit.cost(), operationId);
                restored = result != null && result.success() && balance.valid()
                        && Math.abs(balance.value() - debit.balanceBefore()) <= 1.0E-6D;
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure("currency-compensation", operationId, exception);
            }
            if (!restored) {
                pending.add(new AttemptCost(debit.cost().provider(), debit.cost().currencyId(),
                        debit.cost().displayName(), Math.max(1L, Math.round(debit.amount()))));
            }
        }
        if (!pending.isEmpty()) {
            logCompensationPending(player, operationId, pending);
        }
        return List.copyOf(pending);
    }

    public void refund(Player player, List<AttemptCost> costs) {
        refundWithResult(player, costs, "");
    }

    public RefundResult refundWithResult(Player player, List<AttemptCost> costs, String operationId) {
        List<AttemptCost> normalized = aggregateCosts(costs);
        if (normalized.isEmpty()) {
            return new RefundResult(CompensationOutcome.NOT_REQUIRED, List.of(), List.of(), "");
        }
        List<AttemptCost> refunded = new ArrayList<>();
        List<AttemptCost> pending = new ArrayList<>();
        EconomyManager economyManager = safeEconomyManager(operationId);
        for (AttemptCost cost : normalized) {
            boolean success;
            try {
                if (cost.itemCost()) {
                    success = addItemCost(player, cost.currencyId(), cost.amount());
                } else if (economyManager == null) {
                    success = false;
                } else {
                    ActionResult result = economyManager.add(player, cost.provider(), cost.currencyId(), cost.amount());
                    success = result != null && result.success();
                }
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure("refund", operationId, exception);
                success = false;
            }
            (success ? refunded : pending).add(cost);
        }
        if (!pending.isEmpty()) {
            logCompensationPending(player, operationId, pending);
        }
        return new RefundResult(pending.isEmpty() ? CompensationOutcome.COMPLETED : CompensationOutcome.PENDING,
                refunded, pending, pending.isEmpty() ? "" : "refund_incomplete");
    }

    private BalanceRead readBalance(EconomyManager economyManager, Player player, AttemptCost cost, String operationId) {
        if (economyManager == null || player == null || cost == null) {
            return BalanceRead.invalid();
        }
        try {
            double value = economyManager.getBalance(player, cost.provider(), cost.currencyId());
            return Double.isFinite(value) && value >= 0D ? new BalanceRead(true, value) : BalanceRead.invalid();
        } catch (RuntimeException | LinkageError exception) {
            logProviderFailure("balance", operationId, exception);
            return BalanceRead.invalid();
        }
    }

    private EconomyManager safeEconomyManager(String operationId) {
        try {
            return economyManagerSupplier == null ? null : economyManagerSupplier.get();
        } catch (RuntimeException | LinkageError exception) {
            logProviderFailure("provider-resolve", operationId, exception);
            return null;
        }
    }

    private List<AttemptCost> aggregateCosts(List<AttemptCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return List.of();
        }
        Map<CostKey, AttemptCost> aggregated = new LinkedHashMap<>();
        for (AttemptCost cost : costs) {
            if (!validCost(cost)) {
                continue;
            }
            CostKey key = new CostKey(cost.provider(), cost.currencyId());
            AttemptCost existing = aggregated.get(key);
            long amount = existing == null ? cost.amount() : saturatedAdd(existing.amount(), cost.amount());
            String displayName = existing == null ? cost.displayName() : existing.displayName();
            aggregated.put(key, new AttemptCost(cost.provider(), cost.currencyId(), displayName, amount));
        }
        return List.copyOf(aggregated.values());
    }

    private boolean validCost(AttemptCost cost) {
        return cost != null && cost.amount() > 0L && Texts.isNotBlank(cost.provider())
                && (cost.itemCost() ? Texts.isNotBlank(cost.currencyId()) : true);
    }

    private long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private long countItemCost(Player player, String itemToken) {
        return InventoryItemUtil.countItems(player, itemSourceService, itemToken);
    }

    private boolean addItemCost(Player player, String itemToken, long amount) {
        if (player == null || player.getInventory() == null || amount <= 0L || amount > Integer.MAX_VALUE) {
            return false;
        }
        ItemSource source = ItemSourceUtil.parse(itemToken);
        ItemStack itemStack = source == null ? null : plugin.coreItemFactory().create(source, (int) amount);
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        InventoryItemUtil.giveOrDrop(player, itemStack);
        return true;
    }

    private String resolveCostDisplayName(StrengthenRecipe.CurrencyEntry currency) {
        if (currency == null) {
            return "";
        }
        if (Texts.isNotBlank(currency.displayName())) {
            return currency.displayName();
        }
        if ("items".equals(currency.provider())) {
            return currency.currencyId();
        }
        return Texts.isBlank(currency.currencyId()) ? currency.provider() : currency.currencyId();
    }

    private void logProviderFailure(String phase, String operationId, Throwable throwable) {
        plugin.getLogger().warning("Strengthen economy boundary failed | operationId=" + safeOperationId(operationId)
                + " | phase=" + phase + " | error=" + (throwable == null ? "unknown" : throwable.getMessage()));
    }

    private void logCompensationPending(Player player, String operationId, List<AttemptCost> pending) {
        plugin.getLogger().severe("Strengthen compensation pending | operationId=" + safeOperationId(operationId)
                + " | player=" + (player == null ? "-" : player.getUniqueId()) + " | costs=" + pending);
    }

    private String safeOperationId(String operationId) {
        return Texts.isBlank(operationId) ? "-" : operationId;
    }

    private record CostKey(String provider, String currencyId) {
    }

    private record CurrencyDebit(AttemptCost cost, double amount, double balanceBefore) {
    }

    private record BalanceRead(boolean valid, double value) {
        private static BalanceRead invalid() {
            return new BalanceRead(false, 0D);
        }
    }
}
