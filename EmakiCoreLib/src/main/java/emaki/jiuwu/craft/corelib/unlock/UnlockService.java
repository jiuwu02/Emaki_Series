package emaki.jiuwu.craft.corelib.unlock;

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

/**
 * Generic service for the unlock flow: quote → event → charge → commit.
 * <p>
 * Modules provide an {@link UnlockTarget} implementation that handles domain-specific
 * validation, cost resolution, event firing, and persistence. This service handles the
 * shared payment and rollback logic.
 */
public final class UnlockService {

    /**
     * Aggregated quote for unlocking one or more slots.
     */
    public record Quote(
            int slots,
            String currencyProviderId,
            String currencyId,
            double currencyTotal,
            Map<String, Integer> itemTotals,
            String rejection
    ) {

        public Quote {
            currencyProviderId = currencyProviderId == null ? "" : currencyProviderId;
            currencyId = currencyId == null ? "" : currencyId;
            itemTotals = itemTotals == null ? Map.of() : Map.copyOf(itemTotals);
        }

        public static Quote rejected(int slots, String rejection) {
            return new Quote(slots, "", "", 0.0D, Map.of(), rejection);
        }

        public boolean valid() {
            return rejection == null;
        }

        public boolean chargesCurrency() {
            return !currencyProviderId.isEmpty() && currencyTotal > 0.0D;
        }
    }

    /**
     * Result of an unlock attempt.
     */
    public record UnlockResult(int unlocked, Quote quote, String reasonKey) {

        public boolean success() {
            return unlocked > 0 && reasonKey == null;
        }

        public static UnlockResult failed(Quote quote, String reasonKey) {
            return new UnlockResult(0, quote, reasonKey);
        }
    }

    private final EconomyManager economyManager;
    private final ItemSourceService itemSourceService;

    public UnlockService(EconomyManager economyManager, ItemSourceService itemSourceService) {
        this.economyManager = economyManager;
        this.itemSourceService = itemSourceService;
    }

    /**
     * Computes the cost quote for unlocking the given number of slots via the target.
     *
     * @param target the unlock target providing validation and cost data
     * @param player the player requesting the unlock
     * @param slots  the number of slots to unlock
     * @return a quote (valid or rejected)
     */
    public Quote quote(UnlockTarget target, Player player, int slots) {
        if (slots <= 0) {
            return Quote.rejected(slots, "invalid_amount");
        }
        String validationError = target.validate(player, slots);
        if (validationError != null) {
            return Quote.rejected(slots, validationError);
        }

        int alreadyUnlocked = target.currentCount();
        double currencyTotal = 0.0D;
        String currencyProviderId = "";
        String currencyId = "";
        Map<String, Integer> itemTotals = new LinkedHashMap<>();

        for (int offset = 0; offset < slots; offset++) {
            int ordinal = alreadyUnlocked + offset + 1;
            UnlockSlotCost cost = target.costAt(ordinal);
            if (cost == null) {
                return Quote.rejected(slots, "no_price_defined");
            }
            if (cost.chargesCurrency()) {
                double amount = cost.currencyAmount();
                if (!Double.isFinite(amount) || amount < 0.0D) {
                    return Quote.rejected(slots, "price_overflow");
                }
                double ceiling = cost.currencyCeiling();
                if (ceiling != Double.MAX_VALUE && amount > ceiling) {
                    return Quote.rejected(slots, "price_over_cap");
                }
                currencyTotal += amount;
                if (!Double.isFinite(currencyTotal)) {
                    return Quote.rejected(slots, "price_overflow");
                }
                currencyProviderId = cost.currencyProviderId();
                currencyId = cost.currencyId();
            }
            if (cost.chargesItems()) {
                itemTotals.merge(cost.itemSourceToken(), cost.itemAmount(), Integer::sum);
            }
        }

        return new Quote(slots, currencyProviderId, currencyId, currencyTotal, itemTotals, null);
    }

    /**
     * Executes the full unlock flow: validate → quote → event → charge → commit.
     *
     * @param target the unlock target
     * @param player the player performing the unlock
     * @param slots  the number of slots to unlock
     * @return the result of the unlock attempt
     */
    public UnlockResult execute(UnlockTarget target, Player player, int slots) {
        Quote quote = quote(target, player, slots);
        if (!quote.valid()) {
            return UnlockResult.failed(quote, quote.rejection());
        }

        if (!target.notifyUnlock(player, slots, quote.currencyTotal())) {
            return UnlockResult.failed(quote, "cancelled");
        }

        boolean currencyCharged = false;
        if (quote.chargesCurrency()) {
            ActionResult removal = economyManager.remove(player,
                    quote.currencyProviderId(), quote.currencyId(), quote.currencyTotal());
            if (removal == null || !removal.success()) {
                return UnlockResult.failed(quote, "insufficient_currency");
            }
            currencyCharged = true;
        }

        List<InventoryItemUtil.RemovalPlan> appliedPlans = new ArrayList<>();
        for (Map.Entry<String, Integer> required : quote.itemTotals().entrySet()) {
            ItemSourceRef itemSource = ItemSourceUtil.parse(required.getKey());
            if (itemSource == null) {
                compensate(player, quote, currencyCharged, appliedPlans);
                return UnlockResult.failed(quote, "unknown_item_source");
            }
            InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                    player.getInventory(), itemSourceService, itemSource, required.getValue());
            if (plan == null || !plan.complete()
                    || !InventoryItemUtil.applyRemoval(player.getInventory(), plan)) {
                compensate(player, quote, currencyCharged, appliedPlans);
                return UnlockResult.failed(quote, "insufficient_items");
            }
            appliedPlans.add(plan);
        }

        target.commit(slots, quote.currencyTotal(), quote.currencyProviderId());
        return new UnlockResult(slots, quote, null);
    }

    private void compensate(Player player,
            Quote quote,
            boolean currencyCharged,
            List<InventoryItemUtil.RemovalPlan> appliedPlans) {
        for (int index = appliedPlans.size() - 1; index >= 0; index--) {
            InventoryItemUtil.rollbackRemoval(player.getInventory(), appliedPlans.get(index));
        }
        if (currencyCharged && quote.chargesCurrency()) {
            economyManager.add(player, quote.currencyProviderId(),
                    quote.currencyId(), quote.currencyTotal());
        }
    }
}
