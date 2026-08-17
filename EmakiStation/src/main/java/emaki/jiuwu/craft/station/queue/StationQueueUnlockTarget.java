package emaki.jiuwu.craft.station.queue;

import java.util.Map;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.unlock.UnlockSlotCost;
import emaki.jiuwu.craft.corelib.unlock.UnlockTarget;
import emaki.jiuwu.craft.station.config.PurchaseSettings;
import emaki.jiuwu.craft.station.config.QueueCostConfig;
import emaki.jiuwu.craft.station.definition.StationDefinition;

/**
 * UnlockTarget implementation for Station queue slots.
 * <p>
 * Handles validation, cost resolution, and persistence for purchasing additional queue slots.
 */
public final class StationQueueUnlockTarget implements UnlockTarget {

    public static final String PURCHASE_PERMISSION = "emakistation.purchase";

    private final StationDefinition station;
    private final QueueUnlocks unlocks;
    private final PurchaseSettings settings;
    private final QueueCostConfig costConfig;

    public StationQueueUnlockTarget(StationDefinition station,
            QueueUnlocks unlocks,
            PurchaseSettings settings,
            QueueCostConfig costConfig) {
        this.station = station;
        this.unlocks = unlocks;
        this.settings = settings;
        this.costConfig = costConfig;
    }

    @Override
    public @Nullable String validate(Player player, int slots) {
        if (player == null || station == null || unlocks == null) {
            return "bad_request";
        }
        if (settings == null || !settings.enabled()) {
            return "purchase_disabled";
        }
        if (!player.hasPermission(PURCHASE_PERMISSION)) {
            return "no_permission";
        }
        if (!station.allowPurchase()) {
            return "station_purchase_disabled";
        }
        if (costConfig == null || !costConfig.purchasable()) {
            return "no_price_defined";
        }
        int alreadyPurchased = unlocks.purchased(station.id());
        if (QueueCapacity.purchaseHeadroom(player, station, alreadyPurchased) < slots) {
            return "max_length_reached";
        }
        return null;
    }

    @Override
    public int currentCount() {
        return unlocks.purchased(station.id());
    }

    @Override
    public @Nullable UnlockSlotCost costAt(int ordinal) {
        QueueCostConfig.Tier tier = costConfig.tierFor(ordinal);
        double ceiling = Double.MAX_VALUE;
        QueueCostConfig.CurrencyCost currency;
        QueueCostConfig.ItemCost item;

        if (tier != null) {
            currency = tier.currency();
            item = tier.item();
        } else {
            QueueCostConfig.Fallback fallback = costConfig.fallback();
            if (fallback == null) {
                return null;
            }
            currency = fallback.currency();
            item = fallback.item();
            ceiling = fallback.maxAmount();
        }

        if (currency == null && item == null) {
            return null;
        }

        double currencyAmount = 0.0D;
        String providerId = "";
        if (currency != null) {
            currencyAmount = resolveCurrencyAmount(currency, ordinal);
            providerId = currency.providerId();
        }

        String itemToken = "";
        int itemAmount = 0;
        if (item != null && item.amount() > 0) {
            itemToken = item.sourceToken();
            itemAmount = item.amount();
        }

        return new UnlockSlotCost(providerId, "", currencyAmount, ceiling, itemToken, itemAmount);
    }

    @Override
    public boolean notifyUnlock(Player player, int slots, double currencyTotal) {
        // Station queue unlock does not fire pre-unlock events
        return true;
    }

    @Override
    public void commit(int slots, double currencyTotal, String currencyProviderId) {
        unlocks.addPurchased(station.id(), slots);
    }

    private double resolveCurrencyAmount(QueueCostConfig.CurrencyCost cost, int ordinal) {
        String expression = cost.amountExpression();
        if (expression == null || expression.isBlank()) {
            return cost.amount();
        }
        var evaluated = ExpressionEngine.evaluateNumericDetailed(expression, Map.of("count", ordinal));
        return evaluated.success() ? evaluated.value() : Double.NaN;
    }
}
