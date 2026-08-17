package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.unlock.UnlockService;
import emaki.jiuwu.craft.station.config.PurchaseSettings;
import emaki.jiuwu.craft.station.config.QueueCostConfig;
import emaki.jiuwu.craft.station.definition.StationDefinition;

/**
 * Station queue unlock service - delegates to the unified unlock framework.
 * <p>
 * Exposes the CoreLib {@link UnlockService.Quote} and {@link UnlockService.UnlockResult}
 * types directly.
 */
public final class StationQueueUnlockService {

    public static final String PURCHASE_PERMISSION = "emakistation.purchase";

    private final UnlockService unlockService;
    private final Supplier<PurchaseSettings> settings;
    private final Supplier<QueueCostConfig> costs;

    public StationQueueUnlockService(EconomyManager economyManager,
            ItemSourceService itemSourceService,
            Supplier<PurchaseSettings> settings,
            Supplier<QueueCostConfig> costs) {
        this.unlockService = new UnlockService(economyManager, itemSourceService);
        this.settings = settings;
        this.costs = costs;
    }

    public UnlockService.Quote quote(Player player, StationDefinition station, QueueUnlocks unlocks, int slots) {
        StationQueueUnlockTarget target = new StationQueueUnlockTarget(
                station, unlocks, settings.get(), costs.get()
        );
        return unlockService.quote(target, player, slots);
    }

    public UnlockService.UnlockResult purchase(Player player,
            StationDefinition station,
            QueueUnlocks unlocks,
            int slots) {
        StationQueueUnlockTarget target = new StationQueueUnlockTarget(
                station, unlocks, settings.get(), costs.get()
        );
        return unlockService.execute(target, player, slots);
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

}
