package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.level.model.LevelFailureReason;

final class LevelCostTransaction {

    private LevelCostTransaction() {
    }

    static Result charge(Player player,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            List<CurrencyCharge> currencies,
            List<MaterialCharge> materials) {
        if (player == null || player.getInventory() == null) {
            return Result.failure(LevelFailureReason.PLAYER_NOT_FOUND, RollbackResult.complete());
        }
        List<CurrencyCharge> aggregatedCurrencies = aggregateCurrencies(currencies);
        if (!aggregatedCurrencies.isEmpty() && economyManager == null) {
            return Result.failure(LevelFailureReason.NOT_ENOUGH_MONEY, RollbackResult.complete());
        }
        for (CurrencyCharge currency : aggregatedCurrencies) {
            double balance;
            try {
                balance = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                return Result.failure(LevelFailureReason.NOT_ENOUGH_MONEY, RollbackResult.complete());
            }
            if (balance + 1.0E-9D < currency.amount()) {
                return Result.failure(LevelFailureReason.NOT_ENOUGH_MONEY, RollbackResult.complete());
            }
        }

        List<ItemDebit> itemDebits = new ArrayList<>();
        for (MaterialCharge material : safeMaterials(materials)) {
            long remaining = material.amount();
            for (String itemToken : material.itemSources()) {
                if (remaining <= 0L) {
                    break;
                }
                ItemSourceRef source = ItemSourceUtil.parse(itemToken);
                if (source == null) {
                    continue;
                }
                InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                        player.getInventory(),
                        itemSourceService,
                        source,
                        remaining
                );
                if (plan.removedAmount() <= 0L) {
                    continue;
                }
                if (!InventoryItemUtil.applyRemoval(player.getInventory(), plan)) {
                    RollbackResult rollback = rollback(player, economyManager, List.of(), itemDebits);
                    return Result.failure(
                            rollback.isComplete()
                                    ? LevelFailureReason.NOT_ENOUGH_MATERIAL
                                    : LevelFailureReason.COST_COMPENSATION_FAILED,
                            rollback
                    );
                }
                itemDebits.add(new ItemDebit(itemToken, plan));
                remaining -= plan.removedAmount();
            }
            if (remaining > 0L) {
                RollbackResult rollback = rollback(player, economyManager, List.of(), itemDebits);
                return Result.failure(
                        rollback.isComplete()
                                ? LevelFailureReason.NOT_ENOUGH_MATERIAL
                                : LevelFailureReason.COST_COMPENSATION_FAILED,
                        rollback
                );
            }
        }

        List<CurrencyDebit> currencyDebits = new ArrayList<>();
        for (CurrencyCharge currency : aggregatedCurrencies) {
            double balanceBefore;
            try {
                balanceBefore = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                RollbackResult rollback = rollback(player, economyManager, currencyDebits, itemDebits);
                return failedCurrencyCharge(rollback);
            }
            ActionResult result = null;
            boolean providerFailed = false;
            try {
                result = economyManager.remove(player, currency.provider(), currency.currencyId(), currency.amount());
            } catch (RuntimeException | LinkageError exception) {
                providerFailed = true;
            }
            double balanceAfter = balanceBefore;
            try {
                balanceAfter = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                providerFailed = true;
            }
            double debited = Math.max(0D, balanceBefore - balanceAfter);
            if (debited > 1.0E-9D) {
                currencyDebits.add(new CurrencyDebit(
                        currency.provider(),
                        currency.currencyId(),
                        debited,
                        balanceBefore));
            }
            if (providerFailed || result == null || !result.success()
                    || debited + 1.0E-9D < currency.amount()) {
                RollbackResult rollback = rollback(player, economyManager, currencyDebits, itemDebits);
                return failedCurrencyCharge(rollback);
            }
        }
        return Result.committed(currencyCharges(currencyDebits), materialCharges(itemDebits));
    }

    private static List<CurrencyCharge> currencyCharges(List<CurrencyDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return List.of();
        }
        List<CurrencyCharge> charges = new ArrayList<>(debits.size());
        for (CurrencyDebit debit : debits) {
            if (debit != null && debit.amount() > 1.0E-9D) {
                charges.add(new CurrencyCharge(debit.provider(), debit.currencyId(), debit.amount()));
            }
        }
        return aggregateCurrencies(charges);
    }

    private static List<MaterialCharge> materialCharges(List<ItemDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return List.of();
        }
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (ItemDebit debit : debits) {
            if (debit != null && debit.plan() != null && debit.plan().removedAmount() > 0L) {
                amounts.merge(debit.itemToken(), debit.plan().removedAmount(), LevelCostTransaction::saturatedAdd);
            }
        }
        List<MaterialCharge> charges = new ArrayList<>(amounts.size());
        amounts.forEach((itemToken, amount) -> charges.add(new MaterialCharge(List.of(itemToken), amount)));
        return List.copyOf(charges);
    }

    private static Result failedCurrencyCharge(RollbackResult rollback) {
        return Result.failure(
                rollback.isComplete()
                        ? LevelFailureReason.NOT_ENOUGH_MONEY
                        : LevelFailureReason.COST_COMPENSATION_FAILED,
                rollback
        );
    }

    private static RollbackResult rollback(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits,
            List<ItemDebit> itemDebits) {
        List<CurrencyCharge> remainingCurrencies = rollbackCurrencies(player, economyManager, currencyDebits);
        List<MaterialCharge> remainingMaterials = rollbackItems(player, itemDebits);
        return new RollbackResult(remainingCurrencies, remainingMaterials);
    }

    private static List<CurrencyCharge> aggregateCurrencies(List<CurrencyCharge> currencies) {
        if (currencies == null || currencies.isEmpty()) {
            return List.of();
        }
        Map<CurrencyKey, Double> amounts = new LinkedHashMap<>();
        for (CurrencyCharge currency : currencies) {
            if (currency == null || currency.amount() <= 0D) {
                continue;
            }
            CurrencyKey key = new CurrencyKey(currency.provider(), currency.currencyId());
            amounts.merge(key, currency.amount(), Double::sum);
        }
        List<CurrencyCharge> result = new ArrayList<>(amounts.size());
        for (Map.Entry<CurrencyKey, Double> entry : amounts.entrySet()) {
            result.add(new CurrencyCharge(entry.getKey().provider(), entry.getKey().currencyId(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    private static List<MaterialCharge> safeMaterials(List<MaterialCharge> materials) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        return materials.stream()
                .filter(material -> material != null && material.amount() > 0L && !material.itemSources().isEmpty())
                .toList();
    }

    private static List<MaterialCharge> rollbackItems(Player player, List<ItemDebit> itemDebits) {
        if (itemDebits == null || itemDebits.isEmpty()) {
            return List.of();
        }
        Map<String, Long> remaining = new LinkedHashMap<>();
        for (int index = itemDebits.size() - 1; index >= 0; index--) {
            ItemDebit debit = itemDebits.get(index);
            if (!InventoryItemUtil.rollbackRemoval(player.getInventory(), debit.plan())) {
                remaining.merge(debit.itemToken(), debit.plan().removedAmount(), LevelCostTransaction::saturatedAdd);
            }
        }
        if (remaining.isEmpty()) {
            return List.of();
        }
        List<MaterialCharge> result = new ArrayList<>(remaining.size());
        remaining.forEach((itemToken, amount) -> result.add(new MaterialCharge(List.of(itemToken), amount)));
        return List.copyOf(result);
    }

    private static List<CurrencyCharge> rollbackCurrencies(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits) {
        if (currencyDebits == null || currencyDebits.isEmpty()) {
            return List.of();
        }
        List<CurrencyCharge> remaining = new ArrayList<>();
        for (int index = currencyDebits.size() - 1; index >= 0; index--) {
            CurrencyDebit debit = currencyDebits.get(index);
            double amount = debit.amount();
            if (player != null && economyManager != null) {
                try {
                    double current = economyManager.getBalance(player, debit.provider(), debit.currencyId());
                    double needed = Math.min(debit.amount(), Math.max(0D, debit.balanceBefore() - current));
                    if (needed > 1.0E-9D) {
                        economyManager.add(player, debit.provider(), debit.currencyId(), needed);
                    }
                    double restored = economyManager.getBalance(player, debit.provider(), debit.currencyId());
                    amount = Math.max(0D, debit.balanceBefore() - restored);
                } catch (RuntimeException | LinkageError ignored) {
                    amount = debit.amount();
                }
            }
            if (amount > 1.0E-9D) {
                remaining.add(new CurrencyCharge(debit.provider(), debit.currencyId(), amount));
            }
        }
        return remaining.isEmpty() ? List.of() : aggregateCurrencies(remaining);
    }

    private static long saturatedAdd(long first, long second) {
        if (first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    record CurrencyCharge(String provider, String currencyId, double amount) {

        CurrencyCharge {
            provider = provider == null ? "" : provider;
            currencyId = currencyId == null ? "" : currencyId;
            amount = Math.max(0D, amount);
        }
    }

    record MaterialCharge(List<String> itemSources, long amount) {

        MaterialCharge {
            itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
            amount = Math.max(0L, amount);
        }
    }

    record Result(boolean success,
            String failureReason,
            boolean compensationComplete,
            List<CurrencyCharge> remainingCurrencies,
            List<MaterialCharge> remainingMaterials,
            List<CurrencyCharge> chargedCurrencies,
            List<MaterialCharge> chargedMaterials) {

        Result {
            failureReason = failureReason == null ? LevelFailureReason.COST_COMPENSATION_FAILED : failureReason;
            remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
            remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
            chargedCurrencies = chargedCurrencies == null ? List.of() : List.copyOf(chargedCurrencies);
            chargedMaterials = chargedMaterials == null ? List.of() : List.copyOf(chargedMaterials);
        }

        static Result committed() {
            return committed(List.of(), List.of());
        }

        static Result committed(List<CurrencyCharge> chargedCurrencies,
                List<MaterialCharge> chargedMaterials) {
            return new Result(true, LevelFailureReason.SUCCESS, true, List.of(), List.of(),
                    chargedCurrencies, chargedMaterials);
        }

        static Result failure(String failureReason) {
            return failure(failureReason, RollbackResult.complete());
        }

        private static Result failure(String failureReason, RollbackResult rollback) {
            RollbackResult actual = rollback == null ? RollbackResult.complete() : rollback;
            return new Result(false, failureReason, actual.isComplete(),
                    actual.remainingCurrencies(), actual.remainingMaterials(), List.of(), List.of());
        }
    }

    private record RollbackResult(List<CurrencyCharge> remainingCurrencies,
            List<MaterialCharge> remainingMaterials) {

        private RollbackResult {
            remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
            remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
        }

        private static RollbackResult complete() {
            return new RollbackResult(List.of(), List.of());
        }

        private boolean isComplete() {
            return remainingCurrencies.isEmpty() && remainingMaterials.isEmpty();
        }
    }

    private record CurrencyKey(String provider, String currencyId) {

    }

    private record CurrencyDebit(String provider, String currencyId, double amount, double balanceBefore) {

    }

    private record ItemDebit(String itemToken, InventoryItemUtil.RemovalPlan plan) {

    }
}
