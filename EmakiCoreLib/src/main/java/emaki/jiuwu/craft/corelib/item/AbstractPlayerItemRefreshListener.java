package emaki.jiuwu.craft.corelib.item;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;

@SuppressWarnings("removal")
public abstract class AbstractPlayerItemRefreshListener implements Listener {

    private static final int OFF_HAND_SLOT = 40;
    private static final int LAST_PLAYER_SLOT = 40;

    private final JavaPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<UUID, PendingRefresh> scheduledRefreshes = new ConcurrentHashMap<>();

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
            scheduleRefresh(player, classifyClick(event));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player, classifyDrag(event));
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
        PendingRefresh pending = scheduledRefreshes.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            pending.cancel();
        }
    }

    protected final void scheduleRefresh(Player player) {
        scheduleRefresh(player, SlotScope.fullScope());
    }

    private void scheduleRefresh(Player player, SlotScope scope) {
        if (player == null || refreshService() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        AtomicReference<PendingRefresh> created = new AtomicReference<>();
        PendingRefresh pending = scheduledRefreshes.compute(playerId, (ignored, current) -> {
            if (current != null) {
                current.merge(scope);
                return current;
            }
            PendingRefresh fresh = new PendingRefresh();
            fresh.merge(scope);
            created.set(fresh);
            return fresh;
        });
        if (created.get() == null) {
            return;
        }
        TaskToken task;
        try {
            task = executionDispatcher.runEntity(plugin, player, () -> {
                scheduledRefreshes.remove(playerId, pending);
                if (!player.isOnline()) {
                    return;
                }
                PlayerItemRefreshService refreshService = refreshService();
                if (refreshService != null) {
                    refreshService.refreshPlayerSlots(player, pending.slots(), pending.full());
                }
            }, () -> scheduledRefreshes.remove(playerId, pending));
        } catch (RuntimeException | Error throwable) {
            scheduledRefreshes.remove(playerId, pending);
            throw throwable;
        }
        if (task == null) {
            scheduledRefreshes.remove(playerId, pending);
            return;
        }
        pending.bind(task);
    }

    private SlotScope classifyClick(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        ClickType click = event.getClick();
        if (action == null || click == null) {
            return SlotScope.fullScope();
        }
        ClickedArea area = clickedArea(event);
        int playerSlot = -1;
        if (area == ClickedArea.PLAYER) {
            try {
                int converted = event.getView().convertSlot(event.getRawSlot());
                playerSlot = converted == event.getSlot() ? converted : -1;
            } catch (RuntimeException ignored) {
                playerSlot = -1;
            }
        }
        if (action == InventoryAction.NOTHING
                || action == InventoryAction.CLONE_STACK
                || action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR) {
            return SlotScope.noSlots();
        }
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            return SlotScope.fullScope();
        }
        if (click == ClickType.DOUBLE_CLICK
                || action == InventoryAction.UNKNOWN
                || click == ClickType.UNKNOWN
                || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            return SlotScope.fullScope();
        }
        boolean hotbarClick = click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND;
        if (hotbarClick != (action == InventoryAction.HOTBAR_SWAP)) {
            return SlotScope.fullScope();
        }
        boolean shiftClick = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!shiftClick || area != ClickedArea.PLAYER) {
                return SlotScope.fullScope();
            }
            return localSlot(playerSlot);
        }
        if (shiftClick) {
            return SlotScope.fullScope();
        }
        if (hotbarClick) {
            Set<Integer> dirtySlots = new LinkedHashSet<>();
            if (area == ClickedArea.PLAYER) {
                if (playerSlot < 0 || playerSlot > LAST_PLAYER_SLOT) {
                    return SlotScope.fullScope();
                }
                dirtySlots.add(playerSlot);
            } else if (area != ClickedArea.TOP) {
                return SlotScope.fullScope();
            }
            if (click == ClickType.NUMBER_KEY) {
                int hotbarButton = event.getHotbarButton();
                if (hotbarButton < 0 || hotbarButton > 8) {
                    return SlotScope.fullScope();
                }
                dirtySlots.add(hotbarButton);
            } else {
                dirtySlots.add(OFF_HAND_SLOT);
            }
            return SlotScope.localScope(dirtySlots);
        }
        if (area == ClickedArea.PLAYER) {
            return localSlot(playerSlot);
        }
        if (area == ClickedArea.TOP || area == ClickedArea.OUTSIDE) {
            return SlotScope.noSlots();
        }
        return SlotScope.fullScope();
    }

    private SlotScope classifyDrag(InventoryDragEvent event) {
        Set<Integer> dirtySlots = new LinkedHashSet<>();
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                continue;
            }
            try {
                int converted = event.getView().convertSlot(rawSlot);
                if (converted < 0 || converted > LAST_PLAYER_SLOT) {
                    return SlotScope.fullScope();
                }
                dirtySlots.add(converted);
            } catch (RuntimeException ignored) {
                return SlotScope.fullScope();
            }
        }
        return SlotScope.localScope(dirtySlots);
    }

    private SlotScope localSlot(int slot) {
        if (slot < 0 || slot > LAST_PLAYER_SLOT) {
            return SlotScope.fullScope();
        }
        return SlotScope.localScope(Set.of(slot));
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

    private enum ClickedArea {
        PLAYER,
        TOP,
        OUTSIDE,
        UNKNOWN
    }

    private record SlotScope(boolean full, Set<Integer> slots) {

        private static SlotScope fullScope() {
            return new SlotScope(true, Set.of());
        }

        private static SlotScope noSlots() {
            return new SlotScope(false, Set.of());
        }

        private static SlotScope localScope(Set<Integer> slots) {
            return new SlotScope(false, slots == null || slots.isEmpty() ? Set.of() : Set.copyOf(slots));
        }
    }

    private static final class PendingRefresh {

        private final Set<Integer> slots = new LinkedHashSet<>();
        private volatile boolean full;
        private volatile boolean cancelled;
        private volatile TaskToken delegate;

        private void merge(SlotScope scope) {
            if (scope == null) {
                return;
            }
            if (scope.full()) {
                full = true;
                return;
            }
            slots.addAll(scope.slots());
        }

        private Set<Integer> slots() {
            return slots.isEmpty() ? Set.of() : Set.copyOf(slots);
        }

        private boolean full() {
            return full;
        }

        private void cancel() {
            cancelled = true;
            TaskToken current = delegate;
            if (current != null) {
                current.cancel();
            }
        }

        private void bind(TaskToken handle) {
            delegate = handle;
            if (cancelled) {
                handle.cancel();
            }
        }
    }
}
