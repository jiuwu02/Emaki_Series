package emaki.jiuwu.craft.station.queue;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;

public final class QueueTicker {

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final QueueService queueService;
    private final StationCraftService craftService;
    private final Supplier<StationRegistry> registrySupplier;
    private final Runnable sessionRefresh;

    private TaskToken handle;

    public QueueTicker(Plugin plugin,
            ExecutionDispatcher dispatcher,
            QueueService queueService,
            StationCraftService craftService,
            Supplier<StationRegistry> registrySupplier,
            Runnable sessionRefresh) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.queueService = queueService;
        this.craftService = craftService;
        this.registrySupplier = registrySupplier;
        this.sessionRefresh = sessionRefresh;
    }

    public void start(long periodTicks) {
        stop();
        long period = Math.max(10L, periodTicks);
        handle = dispatcher.runGlobalTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    public boolean running() {
        return handle != null && !handle.cancelled();
    }

    private void tick() {
        StationRegistry registry = registrySupplier.get();
        long now = System.currentTimeMillis();
        for (PlayerQueues queues : queueService.allCached()) {
            UUID playerId = queues.playerId();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            for (CraftQueue queue : List.copyOf(queues.all())) {
                StationDefinition station = registry.station(queue.stationId());
                if (station == null) {
                    continue;
                }
                QueueEntry head = queue.promoteHead(station.progressMode(), now);
                if (head == null || !head.due(station.progressMode(), now)) {
                    continue;
                }
                dispatcher.runEntity(plugin, player,
                        () -> craftService.settleAsync(player, station, queue, head), () -> {

                        });
            }
        }
        if (sessionRefresh != null) {
            sessionRefresh.run();
        }
    }
}
