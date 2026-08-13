package emaki.jiuwu.craft.item.listener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.listener.InventoryRefreshClassifier.ClickContext;
import emaki.jiuwu.craft.item.listener.InventoryRefreshClassifier.ClickedArea;
import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;
import emaki.jiuwu.craft.item.service.ItemRefreshBatch;
import emaki.jiuwu.craft.item.service.ItemRefreshResult;

public final class ItemUpdateListener implements Listener {

    private final EmakiItemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final InventoryRefreshClassifier classifier = new InventoryRefreshClassifier();
    private final ConcurrentHashMap<UUID, PendingRefresh> pendingRefresh = new ConcurrentHashMap<>();
    private final AtomicLong batchSequence = new AtomicLong();

    public ItemUpdateListener(EmakiItemPlugin plugin,
                              EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        accept(event.getPlayer(), "join", InventoryRefreshClassifier.Result.full(RefreshFullReason.JOIN), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        accept(event.getPlayer(), "held_change", InventoryRefreshClassifier.Result.local(
                Set.of(event.getPreviousSlot(), event.getNewSlot()), true), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickedArea clickedArea = clickedArea(event);
        int playerSlot = -1;
        if (clickedArea == ClickedArea.PLAYER) {
            try {
                int converted = event.getView().convertSlot(event.getRawSlot());
                playerSlot = converted == event.getSlot() ? converted : -1;
            } catch (RuntimeException ignored) {
                // Keep the invalid sentinel; the classifier will conservatively request a full refresh.
            }
        }
        accept(player, "inventory_click", classifier.classifyClick(new ClickContext(
                event.getAction(),
                event.getClick(),
                clickedArea,
                event.getRawSlot(),
                playerSlot,
                event.getHotbarButton()
        )), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        LinkedHashSet<Integer> dirtySlots = new LinkedHashSet<>();
        boolean conversionFailed = false;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                continue;
            }
            try {
                int converted = event.getView().convertSlot(rawSlot);
                if (converted < 0 || converted > InventoryRefreshClassifier.LAST_PLAYER_SLOT) {
                    conversionFailed = true;
                    break;
                }
                dirtySlots.add(converted);
            } catch (RuntimeException ignored) {
                conversionFailed = true;
                break;
            }
        }
        accept(player, "inventory_drag", classifier.classifyDrag(dirtySlots, conversionFailed), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            accept(player, "pickup", InventoryRefreshClassifier.Result.full(RefreshFullReason.PICKUP), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand == EquipmentSlot.HAND) {
            accept(event.getPlayer(), "interact", InventoryRefreshClassifier.Result.local(Set.of(), true), true);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            accept(event.getPlayer(), "interact", InventoryRefreshClassifier.Result.local(
                    Set.of(InventoryRefreshClassifier.OFF_HAND_SLOT), true), false);
        } else {
            accept(event.getPlayer(), "interact",
                    InventoryRefreshClassifier.Result.full(RefreshFullReason.UNSUPPORTED_CONTEXT), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pendingRefresh.remove(event.getPlayer().getUniqueId());
        plugin.setService().clearCachedState(event.getPlayer().getUniqueId());
    }

    private ClickedArea clickedArea(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return ClickedArea.OUTSIDE;
        }
        if (clicked instanceof PlayerInventory) {
            return ClickedArea.PLAYER;
        }
        if (clicked == event.getView().getTopInventory()) {
            return ClickedArea.TOP;
        }
        return ClickedArea.UNKNOWN;
    }

    private void accept(Player player,
                        String trigger,
                        InventoryRefreshClassifier.Result classification,
                        boolean includeHeldSlot) {
        plugin.refreshMetrics().recordEvent(classification);
        if (player == null || classification == null || classification.scope() == RefreshScope.SKIP) {
            return;
        }
        enqueue(player, trigger, classification, includeHeldSlot);
    }

    private void enqueue(Player player,
                         String trigger,
                         InventoryRefreshClassifier.Result classification,
                         boolean includeHeldSlot) {
        UUID playerId = player.getUniqueId();
        AtomicReference<PendingRefresh> scheduled = new AtomicReference<>();
        pendingRefresh.compute(playerId, (ignored, current) -> {
            if (current == null) {
                PendingRefresh created = new PendingRefresh(batchSequence.incrementAndGet());
                created.merge(trigger, classification, includeHeldSlot);
                scheduled.set(created);
                return created;
            }
            current.merge(trigger, classification, includeHeldSlot);
            return current;
        });
        PendingRefresh pending = scheduled.get();
        if (pending == null) {
            plugin.refreshMetrics().recordCoalesced();
            return;
        }
        plugin.refreshMetrics().recordBatchCreated();
        TaskToken task;
        try {
            task = scheduling.runEntityLater(
                    plugin,
                    player,
                    () -> executePending(player, playerId, pending),
                    () -> rejectPending(player, playerId, pending, "owner_retired"),
                    1L
            );
        } catch (RuntimeException | LinkageError failure) {
            rejectPending(player, playerId, pending,
                    failure.getClass().getSimpleName() + ": " + Texts.toStringSafe(failure.getMessage()));
            return;
        }
        if (task == TaskToken.UNAVAILABLE) {
            rejectPending(player, playerId, pending, "scheduler_rejected");
        }
    }

    private void rejectPending(Player player,
                               UUID playerId,
                               PendingRefresh pending,
                               String reason) {
        if (pending == null || !pendingRefresh.remove(playerId, pending)) {
            return;
        }
        plugin.refreshMetrics().recordBatchRejected();
        debugRejected(player, pending, reason);
    }

    private void executePending(Player player, UUID playerId, PendingRefresh expected) {
        PendingSnapshot snapshot = drainPending(playerId, expected);
        if (snapshot == null || player == null || !player.isOnline()) {
            return;
        }
        LinkedHashSet<Integer> dirtySlots = new LinkedHashSet<>(snapshot.dirtySlots());
        if (snapshot.includeHeldSlot()) {
            dirtySlots.add(player.getInventory().getHeldItemSlot());
        }
        ItemRefreshResult result = refresh(player, snapshot, dirtySlots);
        plugin.refreshMetrics().recordResult(result);
        debugCompleted(player, snapshot, result);
    }

    private PendingSnapshot drainPending(UUID playerId, PendingRefresh expected) {
        AtomicReference<PendingSnapshot> snapshot = new AtomicReference<>();
        pendingRefresh.compute(playerId, (ignored, current) -> {
            if (current == null || current != expected) {
                return current;
            }
            snapshot.set(current.snapshot());
            return null;
        });
        return snapshot.get();
    }

    private ItemRefreshResult refresh(Player player, PendingSnapshot pending, Set<Integer> dirtySlots) {
        ItemRefreshBatch refreshBatch = plugin.setService().createRefreshBatch(player);
        ItemRefreshResult updateResult = plugin.updateService().updatePlayerItemsDetailed(
                player,
                pending.triggers(),
                dirtySlots,
                pending.forceFull(),
                pending.fullReasons(),
                refreshBatch
        );
        if (updateResult.conflicts() > 0) {
            plugin.setService().invalidateCachedState(player.getUniqueId());
        }
        ItemRefreshResult setResult = plugin.setService().refreshListenerScopeDetailed(
                player,
                pending.triggers(),
                dirtySlots,
                pending.forceFull(),
                pending.contributionDirty(),
                pending.fullReasons(),
                refreshBatch
        );
        ItemRefreshResult result = updateResult.combine(setResult);
        if (result.changed() > 0) {
            plugin.scheduleAttributeEquipmentSync(player);
        }
        return result;
    }

    private void debugCompleted(Player player, PendingSnapshot pending, ItemRefreshResult result) {
        DebugLogger debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        boolean owner = player != null && scheduling.ownsEntity(player);
        debugLogger.log("set", player, "set.refresh_completed", Map.ofEntries(
                Map.entry("batch", pending.batchId()),
                Map.entry("requested", result.requestedScope()),
                Map.entry("update", result.actualUpdateScope()),
                Map.entry("set", result.actualSetScope()),
                Map.entry("reasons", result.fullReasons()),
                Map.entry("triggers", pending.triggers()),
                Map.entry("effective", Texts.toStringSafe(result.effectiveTrigger())),
                Map.entry("dirty_slots", pending.dirtySlots()),
                Map.entry("contribution_dirty", pending.contributionDirty()),
                Map.entry("cache_hit", result.cacheHit()),
                Map.entry("cache_valid", result.cacheValid()),
                Map.entry("scanned_update_slots", result.updateScannedSlots()),
                Map.entry("scanned_set_slots", result.setScannedSlots()),
                Map.entry("scanned", result.scannedSlots()),
                Map.entry("changed", result.changed()),
                Map.entry("conflicts", result.conflicts()),
                Map.entry("ledger_decodes", result.ledgerDecodes()),
                Map.entry("set_compiles", result.setCompiles()),
                Map.entry("elapsed_us", result.elapsedNanos() / 1_000L),
                Map.entry("global_owner", scheduling.ownsGlobal()),
                Map.entry("owner", owner),
                Map.entry("thread", Thread.currentThread().getName())
        ));
    }

    private void debugRejected(Player player, PendingRefresh pending, String reason) {
        DebugLogger debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        PendingSnapshot snapshot = pending.snapshot();
        debugLogger.log("set", player, "set.refresh_rejected", Map.of(
                "batch", pending.batchId(),
                "reason", Texts.toStringSafe(reason),
                "requested", pending.scope(),
                "triggers", snapshot.triggers(),
                "dirty_slots", snapshot.dirtySlots(),
                "contribution_dirty", snapshot.contributionDirty(),
                "thread", Thread.currentThread().getName()
        ));
    }

    private static final class PendingRefresh {

        private final long batchId;
        private final LinkedHashSet<String> triggers = new LinkedHashSet<>();
        private final LinkedHashSet<Integer> dirtySlots = new LinkedHashSet<>();
        private final LinkedHashSet<RefreshFullReason> fullReasons = new LinkedHashSet<>();
        private boolean forceFull;
        private boolean contributionDirty;
        private boolean includeHeldSlot;

        private PendingRefresh(long batchId) {
            this.batchId = batchId;
        }

        private void merge(String trigger,
                           InventoryRefreshClassifier.Result classification,
                           boolean includeHeldSlot) {
            String normalizedTrigger = Texts.toStringSafe(trigger);
            if (Texts.isNotBlank(normalizedTrigger)) {
                triggers.add(normalizedTrigger);
            }
            forceFull |= classification.scope() == RefreshScope.FULL;
            contributionDirty |= classification.contributionDirty();
            this.includeHeldSlot |= includeHeldSlot;
            dirtySlots.addAll(classification.dirtySlots());
            fullReasons.addAll(classification.fullReasons());
        }

        private PendingSnapshot snapshot() {
            return new PendingSnapshot(
                    batchId,
                    List.copyOf(triggers),
                    Set.copyOf(dirtySlots),
                    forceFull,
                    contributionDirty,
                    includeHeldSlot,
                    Set.copyOf(fullReasons)
            );
        }

        private long batchId() {
            return batchId;
        }

        private RefreshScope scope() {
            return forceFull ? RefreshScope.FULL : dirtySlots.isEmpty() && !includeHeldSlot
                    ? RefreshScope.SKIP : RefreshScope.LOCAL;
        }
    }

    private record PendingSnapshot(
            long batchId,
            List<String> triggers,
            Set<Integer> dirtySlots,
            boolean forceFull,
            boolean contributionDirty,
            boolean includeHeldSlot,
            Set<RefreshFullReason> fullReasons) {
    }
}
