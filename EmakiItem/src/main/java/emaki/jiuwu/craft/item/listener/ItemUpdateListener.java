package emaki.jiuwu.craft.item.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemUpdateListener implements Listener {

    private final EmakiItemPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();
    private final AtomicLong refreshSequence = new AtomicLong();

    public ItemUpdateListener(EmakiItemPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        delayed(event.getPlayer(), "join");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        delayed(event.getPlayer(), "held_change");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            delayed(player, "inventory_click");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            delayed(player, "inventory_drag");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            delayed(player, "pickup");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        delayed(event.getPlayer(), "interact");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRefresh.remove(event.getPlayer().getUniqueId());
        plugin.setService().clearCachedState(event.getPlayer().getUniqueId());
    }

    private void delayed(Player player, String trigger) {
        if (player == null) {
            return;
        }
        long refreshId = refreshSequence.incrementAndGet();
        UUID playerId = player.getUniqueId();
        if (!pendingRefresh.add(playerId)) {
            debugRefresh(player, refreshId, trigger, "coalesced", -1);
            return;
        }
        debugRefresh(player, refreshId, trigger, "enqueued", -1);
        TaskHandle task = executionDispatcher.runEntity(
                plugin,
                player,
                () -> {
                    pendingRefresh.remove(playerId);
                    if (!player.isOnline()) {
                        debugRefresh(player, refreshId, trigger, "offline", -1);
                        return;
                    }
                    debugRefresh(player, refreshId, trigger, "executing", -1);
                    int changed = refresh(player, trigger);
                    debugRefresh(player, refreshId, trigger, "completed", changed);
                },
                () -> {
                    pendingRefresh.remove(playerId);
                    debugRefresh(player, refreshId, trigger, "retired", -1);
                }
        );
        if (task == null) {
            pendingRefresh.remove(playerId);
            debugRefresh(player, refreshId, trigger, "rejected", -1);
        }
    }

    private int refresh(Player player, String trigger) {
        int changed = plugin.updateService().updatePlayerItems(player, trigger);
        changed += plugin.setService().refreshEquippedSets(player, trigger);
        if (changed > 0) {
            plugin.scheduleAttributeEquipmentSync(player);
        }
        return changed;
    }

    private void debugRefresh(Player player, long refreshId, String trigger, String stage, int changed) {
        DebugLogger debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        boolean owner = player != null && threadOwnership.isEntityOwned(player);
        debugLogger.logRaw("set", player, "[DEBUG:SET_REFRESH] id=" + refreshId
                + " stage=" + Texts.toStringSafe(stage)
                + " trigger=" + Texts.toStringSafe(trigger)
                + " changed=" + changed
                + " global_owner=" + threadOwnership.isGlobalOwned()
                + " owner=" + owner
                + " thread=" + Thread.currentThread().getName());
    }
}
