package emaki.jiuwu.craft.item.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemUpdateListener implements Listener {

    private static final int OFF_HAND_SLOT = 40;

    private final EmakiItemPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final ConcurrentHashMap<UUID, PendingRefresh> pendingRefresh = new ConcurrentHashMap<>();
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
        delayedFull(event.getPlayer(), "join", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        delayedLocal(event.getPlayer(), "held_change",
                Set.of(event.getPreviousSlot(), event.getNewSlot()), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            delayedFull(player, "inventory_click", true);
            return;
        }
        if (event.getClick() == ClickType.UNKNOWN
                || event.getAction() == InventoryAction.UNKNOWN) {
            delayedFull(player, "inventory_click", true);
            return;
        }
        boolean hotbarClick = event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND;
        if ((hotbarClick && event.getAction() != InventoryAction.HOTBAR_SWAP)
                || (!hotbarClick && event.getAction() == InventoryAction.HOTBAR_SWAP)) {
            delayedFull(player, "inventory_click", true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked instanceof PlayerInventory) {
            Set<Integer> dirtySlots = new HashSet<>();
            if (!addValidSlot(player, dirtySlots, event.getSlot())) {
                delayedFull(player, "inventory_click", true);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                if (!addValidSlot(player, dirtySlots, event.getHotbarButton())) {
                    delayedFull(player, "inventory_click", true);
                    return;
                }
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                dirtySlots.add(OFF_HAND_SLOT);
            }
            delayedLocal(player, "inventory_click", dirtySlots, touchesContribution(player, dirtySlots));
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            delayedFull(player, "inventory_click", true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot < 0 || hotbarSlot > 8) {
                delayedFull(player, "inventory_click", true);
                return;
            }
            delayedLocal(player, "inventory_click", Set.of(hotbarSlot),
                    hotbarSlot == player.getInventory().getHeldItemSlot());
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            delayedLocal(player, "inventory_click", Set.of(OFF_HAND_SLOT), true);
            return;
        }
        if (knownNonInventoryAction(event.getAction())) {
            debugSkip(player, "inventory_click");
            return;
        }
        delayedFull(player, "inventory_click", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getBottomInventory() instanceof PlayerInventory)) {
            delayedFull(player, "inventory_drag", true);
            return;
        }
        Set<Integer> dirtySlots = new HashSet<>();
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getNewItems().keySet()) {
            if (rawSlot < topSize) {
                continue;
            }
            int converted = event.getView().convertSlot(rawSlot);
            if (!addValidSlot(player, dirtySlots, converted)) {
                delayedFull(player, "inventory_drag", true);
                return;
            }
        }
        if (dirtySlots.isEmpty()) {
            debugSkip(player, "inventory_drag");
            return;
        }
        delayedLocal(player, "inventory_drag", dirtySlots, touchesContribution(player, dirtySlots));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            delayedFull(player, "pickup", false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand == EquipmentSlot.HAND) {
            int heldSlot = event.getPlayer().getInventory().getHeldItemSlot();
            delayedLocal(event.getPlayer(), "interact", Set.of(heldSlot), true);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            delayedLocal(event.getPlayer(), "interact", Set.of(OFF_HAND_SLOT), true);
        } else {
            delayedFull(event.getPlayer(), "interact", true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRefresh.remove(event.getPlayer().getUniqueId());
        plugin.setService().clearCachedState(event.getPlayer().getUniqueId());
    }

    private void delayedFull(Player player, String trigger, boolean contributionDirty) {
        delayed(player, trigger, true, Set.of(), contributionDirty);
    }

    private void delayedLocal(Player player, String trigger, Set<Integer> dirtySlots, boolean contributionDirty) {
        delayed(player, trigger, false, dirtySlots, contributionDirty);
    }

    private void delayed(Player player,
            String trigger,
            boolean forceFull,
            Set<Integer> dirtySlots,
            boolean contributionDirty) {
        if (player == null) {
            return;
        }
        long refreshId = refreshSequence.incrementAndGet();
        UUID playerId = player.getUniqueId();
        AtomicReference<PendingRefresh> scheduled = new AtomicReference<>();
        pendingRefresh.compute(playerId, (ignored, current) -> {
            if (current == null) {
                PendingRefresh created = new PendingRefresh(trigger, forceFull, dirtySlots, contributionDirty);
                scheduled.set(created);
                return created;
            }
            current.merge(forceFull, dirtySlots, contributionDirty);
            return current;
        });
        PendingRefresh pending = scheduled.get();
        if (pending == null) {
            debugRefresh(player, refreshId, trigger, "coalesced", "coalesced", -1);
            return;
        }

        debugRefresh(player, refreshId, trigger, "enqueued", pending.scope(), -1);
        TaskHandle task = executionDispatcher.runEntity(
                plugin,
                player,
                () -> {
                    PendingSnapshot snapshot = drainPending(playerId, pending);
                    if (snapshot == null) {
                        debugRefresh(player, refreshId, trigger, "skip", "skip", -1);
                        return;
                    }
                    if (!player.isOnline()) {
                        debugRefresh(player, refreshId, snapshot.trigger(), "offline", snapshot.scope(), -1);
                        return;
                    }
                    debugRefresh(player, refreshId, snapshot.trigger(), "executing", snapshot.scope(), -1);
                    int changed = refresh(player, snapshot);
                    debugRefresh(player, refreshId, snapshot.trigger(), "completed", snapshot.scope(), changed);
                },
                () -> {
                    pendingRefresh.remove(playerId, pending);
                    debugRefresh(player, refreshId, trigger, "retired", pending.scope(), -1);
                }
        );
        if (task == null) {
            pendingRefresh.remove(playerId, pending);
            debugRefresh(player, refreshId, trigger, "rejected", pending.scope(), -1);
        }
    }

    private PendingSnapshot drainPending(UUID playerId, PendingRefresh expected) {
        AtomicReference<PendingSnapshot> snapshot = new AtomicReference<>();
        pendingRefresh.compute(playerId, (ignored, current) -> {
            if (current != expected) {
                return current;
            }
            snapshot.set(current.snapshot());
            return null;
        });
        return snapshot.get();
    }

    private int refresh(Player player, PendingSnapshot pending) {
        int changed = pending.forceFull()
                ? plugin.updateService().updatePlayerItems(player, pending.trigger())
                : plugin.updateService().updatePlayerItems(player, pending.trigger(), pending.dirtySlots());
        changed += plugin.setService().refreshListenerScope(
                player,
                pending.trigger(),
                pending.dirtySlots(),
                pending.forceFull(),
                pending.contributionDirty()
        );
        if (changed > 0) {
            plugin.scheduleAttributeEquipmentSync(player);
        }
        return changed;
    }

    private boolean addValidSlot(Player player, Set<Integer> dirtySlots, int slot) {
        if (player == null || dirtySlots == null || slot < 0 || slot >= player.getInventory().getSize()) {
            return false;
        }
        dirtySlots.add(slot);
        return true;
    }

    private boolean touchesContribution(Player player, Set<Integer> dirtySlots) {
        if (player == null || dirtySlots == null || dirtySlots.isEmpty()) {
            return false;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        return dirtySlots.contains(heldSlot)
                || dirtySlots.contains(OFF_HAND_SLOT)
                || dirtySlots.contains(36)
                || dirtySlots.contains(37)
                || dirtySlots.contains(38)
                || dirtySlots.contains(39);
    }

    private boolean knownNonInventoryAction(InventoryAction action) {
        return switch (action) {
            case NOTHING,
                    PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                    PLACE_ALL, PLACE_SOME, PLACE_ONE,
                    SWAP_WITH_CURSOR,
                    DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                    DROP_ALL_SLOT, DROP_ONE_SLOT,
                    CLONE_STACK -> true;
            default -> false;
        };
    }

    private void debugSkip(Player player, String trigger) {
        debugRefresh(player, refreshSequence.incrementAndGet(), trigger, "skip", "skip", 0);
    }

    private void debugRefresh(Player player,
            long refreshId,
            String trigger,
            String stage,
            String scope,
            int changed) {
        DebugLogger debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        boolean owner = player != null && threadOwnership.isEntityOwned(player);
        debugLogger.logRaw("set", player, "[DEBUG:SET_REFRESH] id=" + refreshId
                + " stage=" + Texts.toStringSafe(stage)
                + " scope=" + Texts.toStringSafe(scope)
                + " trigger=" + Texts.toStringSafe(trigger)
                + " changed=" + changed
                + " global_owner=" + threadOwnership.isGlobalOwned()
                + " owner=" + owner
                + " thread=" + Thread.currentThread().getName());
    }

    private static final class PendingRefresh {

        private final String trigger;
        private boolean forceFull;
        private boolean contributionDirty;
        private final Set<Integer> dirtySlots = new HashSet<>();

        private PendingRefresh(String trigger,
                boolean forceFull,
                Set<Integer> dirtySlots,
                boolean contributionDirty) {
            this.trigger = Texts.toStringSafe(trigger);
            merge(forceFull, dirtySlots, contributionDirty);
        }

        private void merge(boolean forceFull, Set<Integer> dirtySlots, boolean contributionDirty) {
            this.forceFull |= forceFull;
            this.contributionDirty |= contributionDirty;
            if (dirtySlots != null) {
                this.dirtySlots.addAll(dirtySlots);
            }
        }

        private PendingSnapshot snapshot() {
            return new PendingSnapshot(trigger, forceFull, Set.copyOf(dirtySlots), contributionDirty);
        }

        private String scope() {
            return forceFull ? "full" : dirtySlots.isEmpty() ? "skip" : "local";
        }
    }

    private record PendingSnapshot(
            String trigger,
            boolean forceFull,
            Set<Integer> dirtySlots,
            boolean contributionDirty) {

        private String scope() {
            return forceFull ? "full" : dirtySlots.isEmpty() ? "skip" : "local";
        }
    }
}
