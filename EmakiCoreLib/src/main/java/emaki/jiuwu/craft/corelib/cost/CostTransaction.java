package emaki.jiuwu.craft.corelib.cost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class CostTransaction {

    private CostTransaction() {
    }

    public record CurrencyCharge(String provider, String currencyId, double amount) {

        public CurrencyCharge {
            provider = provider == null ? "" : provider;
            currencyId = currencyId == null ? "" : currencyId;
            amount = Math.max(0D, amount);
        }
    }

    public interface MaterialSource {
        List<ItemSourceRef> sources();
        List<String> itemTokens();
        long amount();
    }

    public static MaterialSource ofParsed(List<ItemSourceRef> sources, List<String> tokens, long amount) {
        List<ItemSourceRef> srcCopy = sources == null ? List.of() : List.copyOf(sources);
        List<String> tokenCopy = tokens == null ? List.of() : List.copyOf(tokens);
        long amt = Math.max(0L, amount);
        return new MaterialSource() {
            public List<ItemSourceRef> sources() { return srcCopy; }
            public List<String> itemTokens() { return tokenCopy; }
            public long amount() { return amt; }
        };
    }

    public static CostReceipt execute(Player player,
            @Nullable EconomyManager economyManager,
            @Nullable ItemSourceService itemSourceService,
            List<CurrencyCharge> currencies,
            List<MaterialSource> materials) {
        if (player == null || player.getInventory() == null) {
            return CostReceipt.failure(CostReceipt.FailureReason.PLAYER_UNAVAILABLE);
        }
        List<CurrencyCharge> aggregatedCurrencies = aggregateCurrencies(currencies);
        if (!aggregatedCurrencies.isEmpty() && economyManager == null) {
            return CostReceipt.failure(CostReceipt.FailureReason.ECONOMY_UNAVAILABLE);
        }
        for (CurrencyCharge currency : aggregatedCurrencies) {
            double balance;
            try {
                balance = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                return CostReceipt.failure(CostReceipt.FailureReason.ECONOMY_UNAVAILABLE);
            }
            if (balance + 1.0E-9D < currency.amount()) {
                return CostReceipt.failure(CostReceipt.FailureReason.INSUFFICIENT_FUNDS);
            }
        }
        List<ItemDebit> itemDebits = new ArrayList<>();
        for (MaterialSource material : safeMaterials(materials)) {
            long remaining = material.amount();
            List<ItemSourceRef> srcs = material.sources();
            List<String> tokens = material.itemTokens();
            for (int i = 0; i < srcs.size() && remaining > 0L; i++) {
                ItemSourceRef source = srcs.get(i);
                String token = i < tokens.size() ? tokens.get(i) : "";
                InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                        player.getInventory(), itemSourceService, source, remaining);
                if (plan.removedAmount() <= 0L) {
                    continue;
                }
                if (!InventoryItemUtil.applyRemoval(player.getInventory(), plan)) {
                    CostReceipt.RollbackResult rb = rollback(player, economyManager, List.of(), itemDebits);
                    return CostReceipt.failure(rb.complete()
                            ? CostReceipt.FailureReason.INSUFFICIENT_MATERIALS
                            : CostReceipt.FailureReason.COMPENSATION_FAILED, rb);
                }
                itemDebits.add(new ItemDebit(token, plan));
                remaining -= plan.removedAmount();
            }
            if (remaining > 0L) {
                CostReceipt.RollbackResult rb = rollback(player, economyManager, List.of(), itemDebits);
                return CostReceipt.failure(rb.complete()
                        ? CostReceipt.FailureReason.INSUFFICIENT_MATERIALS
                        : CostReceipt.FailureReason.COMPENSATION_FAILED, rb);
            }
        }
        List<CurrencyDebit> currencyDebits = new ArrayList<>();
        for (CurrencyCharge currency : aggregatedCurrencies) {
            double balanceBefore;
            try {
                balanceBefore = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                CostReceipt.RollbackResult rb = rollback(player, economyManager, currencyDebits, itemDebits);
                return CostReceipt.failure(rb.complete()
                        ? CostReceipt.FailureReason.ECONOMY_UNAVAILABLE
                        : CostReceipt.FailureReason.COMPENSATION_FAILED, rb);
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
                currencyDebits.add(new CurrencyDebit(currency.provider(), currency.currencyId(), debited, balanceBefore));
            }
            if (providerFailed || result == null || !result.success()
                    || debited + 1.0E-9D < currency.amount()) {
                CostReceipt.RollbackResult rb = rollback(player, economyManager, currencyDebits, itemDebits);
                return CostReceipt.failure(rb.complete()
                        ? CostReceipt.FailureReason.INSUFFICIENT_FUNDS
                        : CostReceipt.FailureReason.COMPENSATION_FAILED, rb);
            }
        }
        List<CostReceipt.CurrencyRecord> charged = toCurrencyRecords(currencyDebits);
        List<CostReceipt.MaterialRecord> chargedMats = toMaterialRecords(itemDebits);
        List<CurrencyDebit> capturedCurrency = List.copyOf(currencyDebits);
        List<ItemDebit> capturedItems = List.copyOf(itemDebits);
        return CostReceipt.success(charged, chargedMats,
                () -> rollback(player, economyManager, capturedCurrency, capturedItems));
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

    private static List<MaterialSource> safeMaterials(List<MaterialSource> materials) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        return materials.stream()
                .filter(m -> m != null && m.amount() > 0L && !m.sources().isEmpty())
                .toList();
    }

    private static CostReceipt.RollbackResult rollback(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits,
            List<ItemDebit> itemDebits) {
        List<CostReceipt.CurrencyRecord> remainingCurrencies =
                rollbackCurrencies(player, economyManager, currencyDebits);
        List<CostReceipt.MaterialRecord> remainingMaterials = rollbackItems(player, itemDebits);
        boolean complete = remainingCurrencies.isEmpty() && remainingMaterials.isEmpty();
        return new CostReceipt.RollbackResult(complete, remainingCurrencies, remainingMaterials);
    }

    private static List<CostReceipt.CurrencyRecord> rollbackCurrencies(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits) {
        if (currencyDebits == null || currencyDebits.isEmpty()) {
            return List.of();
        }
        List<CurrencyCharge> remaining = new ArrayList<>();
        for (int i = currencyDebits.size() - 1; i >= 0; i--) {
            CurrencyDebit debit = currencyDebits.get(i);
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
        if (remaining.isEmpty()) {
            return List.of();
        }
        List<CostReceipt.CurrencyRecord> records = new ArrayList<>();
        for (CurrencyCharge charge : aggregateCurrencies(remaining)) {
            records.add(new CostReceipt.CurrencyRecord(charge.provider(), charge.currencyId(), charge.amount()));
        }
        return List.copyOf(records);
    }

    private static List<CostReceipt.MaterialRecord> rollbackItems(Player player, List<ItemDebit> itemDebits) {
        if (itemDebits == null || itemDebits.isEmpty()) {
            return List.of();
        }
        Map<String, Long> remaining = new LinkedHashMap<>();
        for (int i = itemDebits.size() - 1; i >= 0; i--) {
            ItemDebit debit = itemDebits.get(i);
            if (!InventoryItemUtil.rollbackRemoval(player.getInventory(), debit.plan())) {
                remaining.merge(debit.token(), debit.plan().removedAmount(), CostTransaction::saturatedAdd);
            }
        }
        if (remaining.isEmpty()) {
            return List.of();
        }
        List<CostReceipt.MaterialRecord> result = new ArrayList<>(remaining.size());
        remaining.forEach((token, amt) -> result.add(new CostReceipt.MaterialRecord(List.of(token), amt)));
        return List.copyOf(result);
    }

    private static List<CostReceipt.CurrencyRecord> toCurrencyRecords(List<CurrencyDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return List.of();
        }
        List<CurrencyCharge> charges = new ArrayList<>();
        for (CurrencyDebit debit : debits) {
            if (debit != null && debit.amount() > 1.0E-9D) {
                charges.add(new CurrencyCharge(debit.provider(), debit.currencyId(), debit.amount()));
            }
        }
        List<CostReceipt.CurrencyRecord> records = new ArrayList<>();
        for (CurrencyCharge charge : aggregateCurrencies(charges)) {
            records.add(new CostReceipt.CurrencyRecord(charge.provider(), charge.currencyId(), charge.amount()));
        }
        return List.copyOf(records);
    }

    private static List<CostReceipt.MaterialRecord> toMaterialRecords(List<ItemDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return List.of();
        }
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (ItemDebit debit : debits) {
            if (debit != null && debit.plan() != null && debit.plan().removedAmount() > 0L) {
                amounts.merge(debit.token(), debit.plan().removedAmount(), CostTransaction::saturatedAdd);
            }
        }
        List<CostReceipt.MaterialRecord> result = new ArrayList<>(amounts.size());
        amounts.forEach((token, amt) -> result.add(new CostReceipt.MaterialRecord(List.of(token), amt)));
        return List.copyOf(result);
    }

    private static long saturatedAdd(long a, long b) {
        if (a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private record CurrencyKey(String provider, String currencyId) {}

    private record CurrencyDebit(String provider, String currencyId, double amount, double balanceBefore) {}

    private record ItemDebit(String token, InventoryItemUtil.RemovalPlan plan) {}
}
