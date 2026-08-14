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

/**
 * The single periodic task that settles due entries and refreshes open sessions.
 *
 * <p><strong>One timer for the whole server, not one per queue.</strong> Queue count grows with players
 * times stations, so per-queue tasks would multiply scheduler entries without bound. A single sweep over the
 * cached players costs the same regardless of how many stations each of them uses.
 *
 * <p>On Folia the timer itself runs on the global scheduler, so any per-player work is dispatched to that
 * player's entity scheduler before touching their inventory, warehouse, or GUI.
 *
 * <p>Only entries belonging to <em>online</em> players are settled. An offline player's entry can become due
 * while they are away, but delivering it would need warehouse mutations that EmakiStorage refuses for absent
 * owners, so settlement waits for their return. This is why "offline progress" means the clock keeps running,
 * not that outputs arrive unattended.
 */
public final class QueueTicker {

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final QueueService queueService;
    private final StationCraftService craftService;
    private final Supplier<StationRegistry> registrySupplier;
    private final Runnable sessionRefresh;

    private TaskToken handle;

    /**
     * Creates the ticker.
     *
     * @param plugin           the scheduling owner
     * @param dispatcher       CoreLib's execution dispatcher
     * @param queueService     the queue cache
     * @param craftService     the settlement orchestrator
     * @param registrySupplier supplies the current resolved registry
     * @param sessionRefresh   refreshes open GUI sessions; may be a no-op
     */
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

    /**
     * Starts the timer, replacing any previous one.
     *
     * @param periodTicks how often to sweep
     */
    public void start(long periodTicks) {
        stop();
        long period = Math.max(10L, periodTicks);
        handle = dispatcher.runGlobalTimer(plugin, this::tick, period, period);
    }

    /** Cancels the timer. Idempotent. */
    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    /** {@return whether the timer is currently scheduled} */
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
                            // The player left before settlement ran. Their entry stays queued and will be
                            // picked up on their next join, so nothing is lost by skipping it here.
                        });
            }
        }
        if (sessionRefresh != null) {
            sessionRefresh.run();
        }
    }
}
