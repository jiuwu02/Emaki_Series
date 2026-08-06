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

/**
 * Prices and sells additional queue length at one station.
 *
 * <h2>Per-slot pricing, summed</h2>
 * A batch of five is priced as five individual slots at their own tier prices and then summed. Multiplying one
 * unit price by the batch size would let a player buy an arbitrarily large batch at the cheapest tier, which is
 * the opposite of what a rising price curve is for.
 *
 * <h2>Fail-closed</h2>
 * A slot with no tier and no fallback is refused, never given away. A mispriced file costs an administrator a
 * startup warning; a free-capacity bug costs them their economy.
 *
 * <p><strong>Thread:</strong> every method here reads or debits a player's inventory and wallet, so all of them
 * require the buyer's owner thread.
 */
public final class StationQueueUnlockService {

    /**
     * The permission required to buy queue length.
     *
     * <p>Deliberately outside {@code emakistation.queue.*}: that prefix is already read as a numeric queue
     * length tier by {@link QueueCapacity}, so a node like {@code emakistation.queue.purchase} would be parsed
     * as a tier, fail, and be silently skipped.
     */
    public static final String PURCHASE_PERMISSION = "emakistation.purchase";

    /**
     * The price of a purchase, or the reason there is none.
     *
     * @param slots        how many slots were quoted
     * @param currencyId   the economy provider to charge, or an empty string when no currency is charged
     * @param currencyCost the total currency across every quoted slot
     * @param itemCosts    the total items across every quoted slot, keyed by item-source token
     * @param rejection    why the quote is unusable, or {@code null} when it is usable
     */
    public record Quote(int slots,
            String currencyId,
            double currencyCost,
            Map<String, Integer> itemCosts,
            String rejection) {

        /**
         * Creates a quote with a defensively copied item map.
         *
         * @param slots        the quoted slot count
         * @param currencyId   the provider to charge; {@code null} becomes an empty string
         * @param currencyCost the total currency
         * @param itemCosts    the total items; {@code null} becomes empty
         * @param rejection    the refusal reason, or {@code null}
         */
        public Quote {
            currencyId = currencyId == null ? "" : currencyId;
            itemCosts = itemCosts == null ? Map.of() : Map.copyOf(itemCosts);
        }

        /**
         * Creates a refused quote.
         *
         * @param slots     the requested slot count
         * @param rejection the refusal reason key
         * @return the refused quote
         */
        public static Quote rejected(int slots, String rejection) {
            return new Quote(slots, "", 0.0D, Map.of(), rejection);
        }

        /** {@return whether this quote may be acted on} */
        public boolean valid() {
            return rejection == null;
        }

        /** {@return whether this quote charges currency} */
        public boolean chargesCurrency() {
            return !currencyId.isEmpty() && currencyCost > 0.0D;
        }
    }

    /**
     * The outcome of a purchase attempt.
     *
     * @param unlocked  how many slots were granted; zero on failure
     * @param quote     the quote the attempt used
     * @param reasonKey why the attempt failed, or {@code null} on success
     */
    public record PurchaseResult(int unlocked, Quote quote, String reasonKey) {

        /** {@return whether slots were actually granted} */
        public boolean success() {
            return reasonKey == null && unlocked > 0;
        }

        /**
         * Creates a failed result.
         *
         * @param quote     the quote that was attempted
         * @param reasonKey the failure reason key
         * @return the failed result
         */
        public static PurchaseResult failed(Quote quote, String reasonKey) {
            return new PurchaseResult(0, quote, reasonKey);
        }
    }

    private final EconomyManager economyManager;
    private final ItemSourceService itemSourceService;
    private final Supplier<PurchaseSettings> settings;
    private final Supplier<QueueCostConfig> costs;

    /**
     * Creates the service.
     *
     * @param economyManager    CoreLib's economy manager
     * @param itemSourceService CoreLib's item-source service, used for item prices
     * @param settings          supplies the current purchase settings, re-read per call so a reload applies
     * @param costs             supplies the current price table, re-read per call so a reload applies
     */
    public StationQueueUnlockService(EconomyManager economyManager,
            ItemSourceService itemSourceService,
            Supplier<PurchaseSettings> settings,
            Supplier<QueueCostConfig> costs) {
        this.economyManager = economyManager;
        this.itemSourceService = itemSourceService;
        this.settings = settings;
        this.costs = costs;
    }

    /**
     * Prices a batch of queue slots at one station.
     *
     * @param player   the buyer
     * @param station  the station being extended
     * @param unlocks  the buyer's purchase record
     * @param slots    how many slots to quote
     * @return the quote, or a rejection explaining why there is none
     */
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
                    // Explicit refusal rather than a silent clamp: the administrator's formula left the
                    // guard rail they themselves declared, so the sale must not proceed at a made-up price.
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

    /**
     * Buys queue slots.
     *
     * <p>Order: quote, take currency, take items, then grant. A failure after the charge compensates in
     * reverse before returning, so a refused purchase never leaves the buyer out of pocket.
     *
     * <p><strong>Thread:</strong> the buyer's owner thread.
     *
     * @param player  the buyer
     * @param station the station being extended
     * @param unlocks the buyer's purchase record, mutated on success
     * @param slots   how many slots to buy
     * @return the outcome
     */
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

    /** {@return the batch sizes the queue page should offer} */
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
