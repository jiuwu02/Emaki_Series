package emaki.jiuwu.craft.storage.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.storage.api.event.StorageUnlockEvent;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.config.UnlockCostConfig;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

public final class StorageUnlockService {

    public record Quote(int slots,
            double currencyTotal,
            UnlockCostConfig.CurrencyType currencyType,
            String currencyId,
            Map<String, Integer> itemTotals,
            String rejection) {

        public Quote(int slots,
                double currencyTotal,
                UnlockCostConfig.CurrencyType currencyType,
                String currencyId,
                Map<String, Integer> itemTotals,
                String rejection) {
            this.slots = slots;
            this.currencyTotal = currencyTotal;
            this.currencyType = currencyType;
            this.currencyId = currencyId;
            this.itemTotals = Map.copyOf(itemTotals);
            this.rejection = rejection;
        }

        public static Quote rejected(int slots, String rejection) {
            return new Quote(slots, 0.0D, null, "", Map.of(), rejection);
        }

        public boolean valid() {
            return rejection == null;
        }

        public boolean chargesCurrency() {
            return currencyType != null && currencyTotal > 0.0D;
        }
    }

    public record PurchaseResult(int unlocked, Quote quote, String reasonKey) {

        public boolean success() {
            return unlocked > 0 && reasonKey == null;
        }

        public static PurchaseResult failed(Quote quote, String reasonKey) {
            return new PurchaseResult(0, quote, reasonKey);
        }
    }

    private final EconomyManager economyManager;
    private final ItemSourceService itemSourceService;
    private final StorageCapacityService capacityService;
    private final StorageOperationLog operationLog;

    private volatile AppConfig config;
    private volatile UnlockCostConfig costConfig;

    public StorageUnlockService(EconomyManager economyManager,
            ItemSourceService itemSourceService,
            StorageCapacityService capacityService,
            StorageOperationLog operationLog,
            AppConfig config,
            UnlockCostConfig costConfig) {
        this.economyManager = economyManager;
        this.itemSourceService = itemSourceService;
        this.capacityService = capacityService;
        this.operationLog = operationLog;
        this.config = config;
        this.costConfig = costConfig;
    }

    public void reconfigure(AppConfig config, UnlockCostConfig costConfig) {
        if (config != null) {
            this.config = config;
        }
        if (costConfig != null) {
            this.costConfig = costConfig;
        }
    }

    public UnlockCostConfig costConfig() {
        return costConfig;
    }

    public List<Integer> batchOptions() {
        UnlockCostConfig costs = costConfig;
        if (!costs.batch().enabled()) {
            return List.of(1);
        }
        return costs.batch().options();
    }

    public Quote quote(PlayerStorage storage, StorageCapacity capacity, int slots) {
        if (slots <= 0) {
            return Quote.rejected(slots, "invalid_amount");
        }
        UnlockCostConfig costs = costConfig;
        if (!config.unlock().purchaseEnabled()) {
            return Quote.rejected(slots, "purchase_disabled");
        }
        if (!costs.purchasable()) {
            return Quote.rejected(slots, "no_price_defined");
        }
        int maxSlots = capacityService.maxSlots();
        if (maxSlots > 0 && capacity.effectiveSlots() + slots > maxSlots) {
            return Quote.rejected(slots, "max_slots_reached");
        }

        double currencyTotal = 0.0D;
        UnlockCostConfig.CurrencyType currencyType = null;
        String currencyId = "";
        Map<String, Integer> itemTotals = new HashMap<>();
        int alreadyPurchased = storage.purchasedSlots();

        for (int offset = 0; offset < slots; offset++) {
            int ordinal = alreadyPurchased + offset + 1;
            UnlockCostConfig.CurrencyCost currency;
            UnlockCostConfig.ItemCost item;
            double ceiling = Double.MAX_VALUE;

            UnlockCostConfig.Tier tier = costs.tierFor(ordinal);
            if (tier != null) {
                currency = tier.currency();
                item = tier.item();
            } else {
                UnlockCostConfig.Fallback fallback = costs.fallback();
                if (fallback == null) {
                    return Quote.rejected(slots, "no_price_defined");
                }
                currency = fallback.currency();
                item = fallback.item();
                ceiling = fallback.maxAmount();
            }
            if (currency == null && item == null) {
                return Quote.rejected(slots, "no_price_defined");
            }
            if (currency != null) {
                double amount = resolveCurrencyAmount(currency, ordinal);
                if (!Double.isFinite(amount) || amount < 0.0D) {
                    return Quote.rejected(slots, "price_overflow");
                }
                if (ceiling != Double.MAX_VALUE && amount > ceiling) {

                    return Quote.rejected(slots, "price_over_cap");
                }
                currencyTotal += amount;
                if (!Double.isFinite(currencyTotal)) {
                    return Quote.rejected(slots, "price_overflow");
                }
                currencyType = currency.type();
                currencyId = currency.currencyId();
            }
            if (item != null && item.amount() > 0) {
                itemTotals.merge(item.sourceToken(), item.amount(), Integer::sum);
            }
        }
        return new Quote(slots, currencyTotal, currencyType, currencyId, itemTotals, null);
    }

    private double resolveCurrencyAmount(UnlockCostConfig.CurrencyCost cost, int ordinal) {
        String expression = cost.amountExpression();
        if (expression == null || expression.isBlank()) {
            return cost.amount();
        }
        var evaluated = ExpressionEngine.evaluateNumericDetailed(expression, Map.of("count", ordinal));
        return evaluated.success() ? evaluated.value() : Double.NaN;
    }

    public PurchaseResult purchase(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            int slots,
            StorageOperationSource source) {
        Quote quote = quote(storage, capacity, slots);
        if (!quote.valid()) {
            return PurchaseResult.failed(quote, quote.rejection());
        }
        StorageUnlockEvent event = new StorageUnlockEvent(storage.playerId(), slots,
                storage.purchasedSlots(), quote.currencyTotal(), source.id());
        event.callEvent();
        if (event.isCancelled()) {
            return PurchaseResult.failed(quote, "cancelled");
        }

        boolean currencyCharged = false;
        if (quote.chargesCurrency()) {
            ActionResult removal = economyManager.remove(player,
                    quote.currencyType().providerId(), quote.currencyId(), quote.currencyTotal());
            if (removal == null || !removal.success()) {
                return PurchaseResult.failed(quote, "insufficient_currency");
            }
            currencyCharged = true;
        }

        List<InventoryItemUtil.RemovalPlan> appliedPlans = new ArrayList<>();
        for (Map.Entry<String, Integer> required : quote.itemTotals().entrySet()) {
            ItemSourceRef itemSource = ItemSourceUtil.parse(required.getKey());
            if (itemSource == null) {
                compensate(player, quote, currencyCharged, appliedPlans);
                return PurchaseResult.failed(quote, "unknown_item_source");
            }
            InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(player.getInventory(),
                    itemSourceService, itemSource, required.getValue());
            if (!plan.complete() || !InventoryItemUtil.applyRemoval(player.getInventory(), plan)) {
                compensate(player, quote, currencyCharged, appliedPlans);
                return PurchaseResult.failed(quote, "insufficient_items");
            }
            appliedPlans.add(plan);
        }

        storage.purchasedSlots(storage.purchasedSlots() + slots);
        storage.markDirty();
        operationLog.record(StorageLogEntry.raw(storage.playerId(), StorageOperationType.UNLOCK,
                null, "+" + slots + "slots", storage.purchasedSlots(), source,
                quote.chargesCurrency()
                        ? "cost=" + formatCurrency(quote.currencyTotal()) + ":" + quote.currencyType().providerId()
                        : null));
        return new PurchaseResult(slots, quote, null);
    }

    private void compensate(Player player,
            Quote quote,
            boolean currencyCharged,
            List<InventoryItemUtil.RemovalPlan> appliedPlans) {
        for (int index = appliedPlans.size() - 1; index >= 0; index--) {
            InventoryItemUtil.rollbackRemoval(player.getInventory(), appliedPlans.get(index));
        }
        if (currencyCharged && quote.chargesCurrency()) {
            economyManager.add(player, quote.currencyType().providerId(),
                    quote.currencyId(), quote.currencyTotal());
        }
    }

    private static String formatCurrency(double amount) {
        if (amount == Math.rint(amount) && Math.abs(amount) < 1.0E15D) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }
}
