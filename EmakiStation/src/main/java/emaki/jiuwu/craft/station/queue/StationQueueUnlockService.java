package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.station.config.PurchaseSettings;
import emaki.jiuwu.craft.station.config.QueueCostConfig;
import emaki.jiuwu.craft.station.definition.StationDefinition;

public final class StationQueueUnlockService {

    public static final String PURCHASE_PERMISSION = "emakistation.purchase";

    public record Quote(int slots,
            String currencyId,
            double currencyCost,
            Map<String, Integer> itemCosts,
            String rejection) {

        public Quote {
            currencyId = currencyId == null ? "" : currencyId;
            itemCosts = itemCosts == null ? Map.of() : Map.copyOf(itemCosts);
        }

        public static Quote rejected(int slots, String rejection) {
            return new Quote(slots, "", 0.0D, Map.of(), rejection);
        }

        public boolean valid() {
            return rejection == null;
        }

        public boolean chargesCurrency() {
            return !currencyId.isEmpty() && currencyCost > 0.0D;
        }
    }

    public record PurchaseResult(int unlocked, Quote quote, String reasonKey) {

        public boolean success() {
            return reasonKey == null && unlocked > 0;
        }

        public static PurchaseResult failed(Quote quote, String reasonKey) {
            return new PurchaseResult(0, quote, reasonKey);
        }
    }

    private final EconomyManager economyManager;
    private final ItemSourceService itemSourceService;
    private final Supplier<PurchaseSettings> settings;
    private final Supplier<QueueCostConfig> costs;

    public StationQueueUnlockService(EconomyManager economyManager,
            ItemSourceService itemSourceService,
            Supplier<PurchaseSettings> settings,
            Supplier<QueueCostConfig> costs) {
        this.economyManager = economyManager;
        this.itemSourceService = itemSourceService;
        this.settings = settings;
        this.costs = costs;
    }

    public Quote quote(Player player, StationDefinition station, QueueUnlocks unlocks, int slots) {
        if (player == null || station == null || unlocks == null) {
            return Quote.rejected(slots, "bad_request");
        }
        if (slots <= 0) {
            return Quote.rejected(slots, "invalid_amount");
        }
        PurchaseSettings purchase = settings.get();
        if (purchase == null || !purchase.enabled()) {
            return Quote.rejected(slots, "purchase_disabled");
        }
        if (!player.hasPermission(PURCHASE_PERMISSION)) {
            return Quote.rejected(slots, "no_permission");
        }
        if (!station.allowPurchase()) {
            return Quote.rejected(slots, "station_purchase_disabled");
        }
        QueueCostConfig table = costs.get();
        if (table == null || !table.purchasable()) {
            return Quote.rejected(slots, "no_price_defined");
        }
        int alreadyPurchased = unlocks.purchased(station.id());
        if (QueueCapacity.purchaseHeadroom(player, station, alreadyPurchased) < slots) {
            return Quote.rejected(slots, "max_length_reached");
        }
        double currencyTotal = 0.0D;
        String currencyId = "";
        Map<String, Integer> itemTotals = new LinkedHashMap<>();
        for (int offset = 0; offset < slots; offset++) {
            int ordinal = alreadyPurchased + offset + 1;
            QueueCostConfig.CurrencyCost currency;
            QueueCostConfig.ItemCost item;
            double ceiling = Double.MAX_VALUE;
            QueueCostConfig.Tier tier = table.tierFor(ordinal);
            if (tier != null) {
                currency = tier.currency();
                item = tier.item();
            } else {
                QueueCostConfig.Fallback fallback = table.fallback();
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
                currencyId = currency.providerId();
            }
            if (item != null && item.amount() > 0) {
                itemTotals.merge(item.sourceToken(), item.amount(), Integer::sum);
            }
        }
        return new Quote(slots, currencyId, currencyTotal, itemTotals, null);
    }

    public PurchaseResult purchase(Player player,
            StationDefinition station,
            QueueUnlocks unlocks,
            int slots) {
        Quote quote = quote(player, station, unlocks, slots);
        if (!quote.valid()) {
            return PurchaseResult.failed(quote, quote.rejection());
        }
        boolean currencyCharged = false;
        if (quote.chargesCurrency()) {
            ActionResult removal = economyManager.remove(player, quote.currencyId(), "",
                    quote.currencyCost());
            if (removal == null || !removal.success()) {
                return PurchaseResult.failed(quote, "insufficient_currency");
            }
            currencyCharged = true;
        }
        List<InventoryItemUtil.RemovalPlan> applied = new ArrayList<>();
        for (Map.Entry<String, Integer> required : quote.itemCosts().entrySet()) {
            ItemSourceRef source = ItemSourceUtil.parse(required.getKey());
            if (source == null) {
                compensate(player, quote, currencyCharged, applied);
                return PurchaseResult.failed(quote, "unknown_item_source");
            }
            InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(player.getInventory(),
                    itemSourceService, source, required.getValue());
            if (plan == null || !plan.complete()
                    || !InventoryItemUtil.applyRemoval(player.getInventory(), plan)) {
                compensate(player, quote, currencyCharged, applied);
                return PurchaseResult.failed(quote, "insufficient_items");
            }
            applied.add(plan);
        }
        unlocks.addPurchased(station.id(), slots);
        return new PurchaseResult(slots, quote, null);
    }

    public List<Integer> batchOptions() {
        QueueCostConfig table = costs.get();
        if (table == null || !table.batch().enabled()) {
            return List.of(1);
        }
        List<Integer> options = new ArrayList<>();
        options.add(1);
        for (Integer option : table.batch().options()) {
            if (option != null && option > 1 && !options.contains(option)) {
                options.add(option);
            }
        }
        return List.copyOf(options);
    }

    private double resolveCurrencyAmount(QueueCostConfig.CurrencyCost cost, int ordinal) {
        String expression = cost.amountExpression();
        if (expression == null || expression.isBlank()) {
            return cost.amount();
        }
        var evaluated = ExpressionEngine.evaluateNumericDetailed(expression, Map.of("count", ordinal));
        return evaluated.success() ? evaluated.value() : Double.NaN;
    }

    private void compensate(Player player,
            Quote quote,
            boolean currencyCharged,
            List<InventoryItemUtil.RemovalPlan> applied) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            InventoryItemUtil.rollbackRemoval(player.getInventory(), applied.get(index));
        }
        applied.clear();
        if (currencyCharged && quote.chargesCurrency()) {
            economyManager.add(player, quote.currencyId(), "", quote.currencyCost());
        }
    }
}
