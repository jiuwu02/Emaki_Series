package emaki.jiuwu.craft.storage.service;

import java.util.Map;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.unlock.UnlockSlotCost;
import emaki.jiuwu.craft.corelib.unlock.UnlockTarget;
import emaki.jiuwu.craft.storage.api.event.StorageUnlockEvent;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.config.UnlockCostConfig;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

public final class StorageUnlockTarget implements UnlockTarget {

    private final PlayerStorage storage;
    private final StorageCapacity capacity;
    private final StorageCapacityService capacityService;
    private final AppConfig config;
    private final UnlockCostConfig costConfig;
    private final StorageOperationSource source;

    public StorageUnlockTarget(PlayerStorage storage,
            StorageCapacity capacity,
            StorageCapacityService capacityService,
            AppConfig config,
            UnlockCostConfig costConfig,
            StorageOperationSource source) {
        this.storage = storage;
        this.capacity = capacity;
        this.capacityService = capacityService;
        this.config = config;
        this.costConfig = costConfig;
        this.source = source;
    }

    @Override
    public @Nullable String validate(Player player, int slots) {
        if (!config.unlock().purchaseEnabled()) {
            return "purchase_disabled";
        }
        if (!costConfig.purchasable()) {
            return "no_price_defined";
        }
        int maxSlots = capacityService.maxSlots();
        if (maxSlots > 0 && capacity.effectiveSlots() + slots > maxSlots) {
            return "max_slots_reached";
        }
        return null;
    }

    @Override
    public int currentCount() {
        return storage.purchasedSlots();
    }

    @Override
    public @Nullable UnlockSlotCost costAt(int ordinal) {
        UnlockCostConfig.Tier tier = costConfig.tierFor(ordinal);
        double ceiling = Double.MAX_VALUE;
        UnlockCostConfig.CurrencyCost currency;
        UnlockCostConfig.ItemCost item;

        if (tier != null) {
            currency = tier.currency();
            item = tier.item();
        } else {
            UnlockCostConfig.Fallback fallback = costConfig.fallback();
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
        String currencyId = "";
        if (currency != null) {
            currencyAmount = resolveCurrencyAmount(currency, ordinal);
            providerId = currency.type().providerId();
            currencyId = currency.currencyId();
        }

        String itemToken = "";
        int itemAmount = 0;
        if (item != null && item.amount() > 0) {
            itemToken = item.sourceToken();
            itemAmount = item.amount();
        }

        return new UnlockSlotCost(providerId, currencyId, currencyAmount, ceiling, itemToken, itemAmount);
    }

    @Override
    public boolean notifyUnlock(Player player, int slots, double currencyTotal) {
        StorageUnlockEvent event = new StorageUnlockEvent(
                storage.playerId(),
                slots,
                storage.purchasedSlots(),
                currencyTotal,
                source.id()
        );
        event.callEvent();
        return !event.isCancelled();
    }

    @Override
    public void commit(int slots, double currencyTotal, String currencyProviderId) {
        storage.purchasedSlots(storage.purchasedSlots() + slots);
        storage.markDirty();
    }

    private double resolveCurrencyAmount(UnlockCostConfig.CurrencyCost cost, int ordinal) {
        String expression = cost.amountExpression();
        if (expression == null || expression.isBlank()) {
            return cost.amount();
        }
        var evaluated = ExpressionEngine.evaluateNumericDetailed(expression, Map.of("count", ordinal));
        return evaluated.success() ? evaluated.value() : Double.NaN;
    }
}
