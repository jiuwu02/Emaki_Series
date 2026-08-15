package emaki.jiuwu.craft.corelib.item;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;

public abstract class AbstractPlayerItemRefreshListener implements Listener {

    private final JavaPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<UUID, TaskToken> scheduledRefreshes = new ConcurrentHashMap<>();

    protected AbstractPlayerItemRefreshListener(JavaPlugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.executionDispatcher = Objects.requireNonNull(executionDispatcher, "executionDispatcher");
    }

    protected abstract PlayerItemRefreshService refreshService();

    @EventHandler(priority = EventPriority.MONITOR)
    public final void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onDrop(PlayerDropItemEvent event) {
        PlayerItemRefreshService refreshService = refreshService();
        if (refreshService != null) {
            refreshService.refreshDroppedItem(event.getItemDrop());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerItemRefreshService refreshService = refreshService();
        if (refreshService != null) {
            refreshService.refreshDroppedItem(event.getItem());
        }
        scheduleRefresh(player);
    }

    @EventHandler
    public final void onQuit(PlayerQuitEvent event) {
        TaskToken task = scheduledRefreshes.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    protected final void scheduleRefresh(Player player) {
        if (player == null || refreshService() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        RefreshSlot slot = new RefreshSlot();

        if (scheduledRefreshes.putIfAbsent(playerId, slot) != null) {
            return;
        }
        TaskToken task;
        try {
            task = executionDispatcher.runEntity(plugin, player, () -> {

                scheduledRefreshes.remove(playerId, slot);
                if (!player.isOnline()) {
                    return;
                }
                PlayerItemRefreshService refreshService = refreshService();
                if (refreshService != null) {
                    refreshService.refreshPlayerInventory(player);
                }
            }, () -> scheduledRefreshes.remove(playerId, slot));
        } catch (RuntimeException | Error throwable) {
            scheduledRefreshes.remove(playerId, slot);
            throw throwable;
        }
        if (task == null) {
            scheduledRefreshes.remove(playerId, slot);
            return;
        }
        slot.bind(task);
    }

    private static final class RefreshSlot implements TaskToken {

        private volatile TaskToken delegate;
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
            TaskToken current = delegate;
            if (current != null) {
                current.cancel();
            }
        }

        @Override
        public boolean cancelled() {
            TaskToken current = delegate;
            return current == null ? cancelled : current.cancelled();
        }

        void bind(TaskToken handle) {
            delegate = handle;
            if (cancelled) {
                handle.cancel();
            }
        }
    }
}
