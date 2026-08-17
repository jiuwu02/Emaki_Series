package emaki.jiuwu.craft.storage.service;

import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.unlock.UnlockService;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.config.UnlockCostConfig;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

/**
 * Storage unlock service - delegates to the unified unlock framework.
 * <p>
 * Exposes the CoreLib {@link UnlockService.Quote} and {@link UnlockService.UnlockResult}
 * types directly.
 */
public final class StorageUnlockService {

    private final UnlockService unlockService;
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
        this.unlockService = new UnlockService(economyManager, itemSourceService);
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

    public UnlockService.Quote quote(PlayerStorage storage, StorageCapacity capacity, int slots) {
        StorageUnlockTarget target = new StorageUnlockTarget(
                storage, capacity, capacityService, config, costConfig, StorageOperationSource.GUI
        );
        return unlockService.quote(target, null, slots);
    }

    public UnlockService.UnlockResult purchase(PlayerStorage storage,
            Player player,
            StorageCapacity capacity,
            int slots,
            StorageOperationSource source) {
        StorageUnlockTarget target = new StorageUnlockTarget(
                storage, capacity, capacityService, config, costConfig, source
        );
        UnlockService.UnlockResult result = unlockService.execute(target, player, slots);

        if (result.success()) {
            UnlockService.Quote q = result.quote();
            operationLog.record(StorageLogEntry.raw(
                    storage.playerId(),
                    StorageOperationType.UNLOCK,
                    null,
                    "+" + slots + "slots",
                    storage.purchasedSlots(),
                    source,
                    q.chargesCurrency()
                            ? "cost=" + formatCurrency(q.currencyTotal()) + ":" + q.currencyProviderId()
                            : null
            ));
        }

        return result;
    }

    private static String formatCurrency(double amount) {
        if (amount == Math.rint(amount) && Math.abs(amount) < 1.0E15D) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }
}
