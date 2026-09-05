package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiClickThrottle;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiDebugSupport;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionRegistry;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.SoundParser;

public final class PacketGuiBackend implements GuiBackend, Listener {

    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private final JavaPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final AtomicInteger windowIdCounter = new AtomicInteger(1);
    private final Map<UUID, PacketWindow> windows = new ConcurrentHashMap<>();
    private final ClickListener clickListener = new ClickListener();
    private final AtomicBoolean registered = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();

    public PacketGuiBackend(JavaPlugin plugin, ExecutionDispatcher executionDispatcher) {
        if (executionDispatcher == null) {
            throw new IllegalArgumentException("executionDispatcher");
        }
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        PacketEvents.getAPI().getEventManager().registerListener(clickListener);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered.set(true);
    }

    @Override
    public String name() {
        return "packet";
    }

    @Override
    public void open(GuiSession session, Map<Integer, ItemStack> renderedSlots) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        UUID viewerId = viewer.getUniqueId();
        PacketWindow previous = windows.get(viewerId);
        if (previous != null) {
            returnCursor(viewer, previous);
            windows.remove(viewerId, previous);
        }
        PacketWindow window = new PacketWindow(nextWindowId(), topSize(session), session);
        window.setCursor(viewer.getItemOnCursor());
        viewer.setItemOnCursor(null);
        windows.put(viewerId, window);
        if (previous == null) {
            debug(viewer, "common.gui.packet_open", windowFields(window));
        } else {
            debug(viewer, "common.gui.packet_open_replaced", windowFields(
                    window,
                    GuiDebugSupport.replacements("replaced_window_id", previous.windowId)
            ));
        }
        try {
            sendOpenWindow(viewer, window);
            applyTopItems(window, renderedSlots);
            sendWindowItems(viewer, window);
        } catch (RuntimeException | Error throwable) {
            if (windows.remove(viewerId, window)) {
                returnCursor(viewer, window);
            }
            debug(viewer, "common.gui.packet_open_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window)
            ));
            throw throwable;
        }
    }

    @Override
    public void applySlots(GuiSession session, Map<Integer, ItemStack> renderedSlots) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || window.session != session) {
            debug(viewer, "common.gui.packet_apply_ignored_window_or_session_mismatch",
                    GuiDebugSupport.sessionFields(session));
            return;
        }
        try {
            int desiredSize = topSize(session);
            if (desiredSize != window.topSize) {
                window.topSize = desiredSize;
                window.topItems = new ItemStack[desiredSize];
                if (window.handlingClick) {
                    window.pendingReopen = true;
                } else {
                    sendOpenWindow(viewer, window);
                }
            }
            applyTopItems(window, renderedSlots);
            if (window.handlingClick) {
                window.pendingSync = true;
                debug(viewer, "common.gui.packet_apply_deferred", windowFields(window));
                return;
            }
            debug(viewer, "common.gui.packet_apply", windowFields(window));
            sendWindowItems(viewer, window);
        } catch (RuntimeException | Error throwable) {
            debug(viewer, "common.gui.packet_apply_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window)
            ));
            throw throwable;
        }
    }

    @Override
    public void close(GuiSession session) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || window.session != session) {
            debug(viewer, "common.gui.packet_close_ignored_window_or_session_mismatch",
                    GuiDebugSupport.sessionFields(session));
            return;
        }
        debug(viewer, "common.gui.packet_close", windowFields(window));
        try {
            returnCursor(viewer, window);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                    new WrapperPlayServerCloseWindow(window.windowId));
        } catch (Throwable throwable) {
            debug(viewer, "common.gui.packet_close_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window)
            ));
        } finally {
            windows.remove(viewer.getUniqueId(), window);
        }
    }

    @Override
    public void shutdown() {
        shutdownAsync();
    }

    @Override
    public CompletionStage<Void> shutdownAsync() {
        CompletableFuture<Void> existing = shutdownFuture.get();
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Void> created = new CompletableFuture<>();
        if (!shutdownFuture.compareAndSet(null, created)) {
            return shutdownFuture.get();
        }

        Map<UUID, PacketWindow> snapshot = Map.copyOf(windows);
        List<CompletableFuture<Void>> closeFutures = new ArrayList<>(snapshot.size());
        for (Map.Entry<UUID, PacketWindow> entry : snapshot.entrySet()) {
            closeFutures.add(closeWindowDuringShutdown(entry.getKey(), entry.getValue()));
        }
        CompletableFuture<Void> closeWindows = CompletableFuture
                .allOf(closeFutures.toArray(CompletableFuture[]::new))
                .orTimeout(2L, TimeUnit.SECONDS)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        debug(null, "common.gui.packet_shutdown_window_close_incomplete",
                                GuiDebugSupport.errorFields(throwable));
                    }
                    snapshot.forEach(this::retireWindow);
                    return null;
                });
        CompletableFuture<Void> unregisterListeners = unregisterListenersAsync();
        CompletableFuture.allOf(closeWindows, unregisterListeners).whenComplete((ignored, throwable) -> {
            windows.clear();
            if (throwable != null) {
                debug(null, "common.gui.packet_shutdown_incomplete", GuiDebugSupport.errorFields(throwable));
            }
            created.complete(null);
        });
        return created;
    }

    private CompletableFuture<Void> unregisterListenersAsync() {
        if (registered.compareAndSet(true, false)) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(clickListener);
            } catch (RuntimeException | LinkageError exception) {
                debug(null, "common.gui.packet_listener_unregister_failed",
                        GuiDebugSupport.errorFields(exception));
            }
        }
        if (!plugin.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return executionDispatcher.submitGlobal(plugin, () -> {
                HandlerList.unregisterAll(this);
                return null;
            }).orTimeout(2L, TimeUnit.SECONDS).handle((ignored, throwable) -> {
                if (throwable != null) {
                    debug(null, "common.gui.packet_bukkit_listener_unregister_incomplete",
                            GuiDebugSupport.errorFields(throwable));
                }
                return null;
            });
        } catch (RuntimeException | LinkageError exception) {
            debug(null, "common.gui.packet_bukkit_listener_unregister_dispatch_failed",
                    GuiDebugSupport.errorFields(exception));
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> closeWindowDuringShutdown(UUID viewerId, PacketWindow window) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (window == null) {
            completion.complete(null);
            return completion;
        }
        Player viewer = window.session.viewer();
        AtomicBoolean cleanupStarted = new AtomicBoolean();
        Runnable entityCleanup = () -> completeWindowCleanup(
                viewerId, window, viewer, true, true, cleanupStarted, completion);
        Runnable retiredCleanup = () -> {
            debug(null, "common.gui.packet_shutdown_dispatch_retired", windowFields(window));
            completeWindowCleanup(viewerId, window, viewer, false, false, cleanupStarted, completion);
        };
        if (viewer != null) {
            try {
                if (executionDispatcher.runEntity(plugin, viewer, entityCleanup, retiredCleanup) != null) {
                    return completion;
                }
                debug(null, "common.gui.packet_shutdown_dispatch_rejected", windowFields(window));
            } catch (RuntimeException | LinkageError exception) {
                debug(null, "common.gui.packet_shutdown_dispatch_failed", GuiDebugSupport.errorFields(
                        exception,
                        windowFields(window)
                ));
            }
        }
        retiredCleanup.run();
        return completion;
    }

    private void completeWindowCleanup(UUID viewerId,
            PacketWindow window,
            Player viewer,
            boolean touchPlayer,
            boolean notifyHandler,
            AtomicBoolean cleanupStarted,
            CompletableFuture<Void> completion) {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            if (windows.remove(viewerId, window)) {
                cleanupWindow(viewerId, window, viewer, touchPlayer, notifyHandler);
            }
            completion.complete(null);
        } catch (Throwable throwable) {
            debug(null, "common.gui.packet_shutdown_cleanup_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window)
            ));
            completion.completeExceptionally(throwable);
        }
    }

    private void cleanupWindow(UUID viewerId,
            PacketWindow window,
            Player viewer,
            boolean touchPlayer,
            boolean notifyHandler) {
        GuiSession session = window.session;
        try {
            if (touchPlayer && viewer != null && viewer.isOnline()) {
                returnCursor(viewer, window);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                        new WrapperPlayServerCloseWindow(window.windowId));
            }
            if (notifyHandler) {
                try {
                    session.handler().onClose(session, new PacketGuiCloseContext(viewer, window));
                } catch (Throwable throwable) {
                    debug(viewer, "common.gui.packet_shutdown_close_handler_failed", GuiDebugSupport.errorFields(
                            throwable,
                            windowFields(window)
                    ));
                }
            }
        } finally {
            GuiSessionRegistry registry = session.registry();
            if (registry != null && viewerId != null) {
                registry.removeSession(viewerId, session);
            }
            windows.remove(viewerId, window);
        }
    }

    private int nextWindowId() {
        return windowIdCounter.getAndUpdate(current -> current >= 100 ? 1 : current + 1);
    }

    private int topSize(GuiSession session) {
        return Math.max(9, session.getInventory().getSize());
    }

    private void sendOpenWindow(Player viewer, PacketWindow window) {
        int rows = Math.max(1, Math.min(6, window.topSize / 9));
        WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(
                window.windowId,
                rows - 1,
                window.session.titleComponent()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, open);
        debug(viewer, "common.gui.packet_send_open", windowFields(window));
    }

    private void applyTopItems(PacketWindow window, Map<Integer, ItemStack> renderedSlots) {
        ItemStack[] top = new ItemStack[window.topSize];
        if (renderedSlots != null) {
            for (Map.Entry<Integer, ItemStack> entry : renderedSlots.entrySet()) {
                int slot = entry.getKey();
                if (slot >= 0 && slot < window.topSize) {
                    top[slot] = entry.getValue();
                }
            }
        }
        window.topItems = top;
    }

    private void sendWindowItems(Player viewer, PacketWindow window) {
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items =
                new ArrayList<>(window.topSize + PLAYER_INVENTORY_SLOTS);
        for (int slot = 0; slot < window.topSize; slot++) {
            items.add(PacketItems.toPacket(window.topItems[slot]));
        }
        PlayerInventory inventory = viewer.getInventory();
        for (int slot = 9; slot < 36; slot++) {
            items.add(PacketItems.toPacket(inventory.getItem(slot)));
        }
        for (int slot = 0; slot < 9; slot++) {
            items.add(PacketItems.toPacket(inventory.getItem(slot)));
        }
        int stateId = window.nextStateId();
        WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(
                window.windowId,
                stateId,
                items,
                PacketItems.toPacket(window.cursor)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        debug(viewer, "common.gui.packet_send_items", windowFields(
                window,
                GuiDebugSupport.replacements("sent_state_id", stateId)
        ));
    }

    private void authoritativeResync(Player viewer, PacketWindow window, String reason) {
        if (!isCurrentSession(viewer.getUniqueId(), window)) {
            retireWindow(viewer.getUniqueId(), window);
            debug(viewer, "common.gui.packet_resync_skipped_stale_window_or_session", windowFields(
                    window,
                    GuiDebugSupport.replacements("cause", reason)
            ));
            return;
        }
        try {
            if (window.pendingReopen) {
                sendOpenWindow(viewer, window);
            }
            sendWindowItems(viewer, window);
            debug(viewer, "common.gui.packet_resync", windowFields(
                    window,
                    GuiDebugSupport.replacements("cause", reason)
            ));
        } catch (Throwable throwable) {
            debug(viewer, "common.gui.packet_resync_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window, GuiDebugSupport.replacements("cause", reason))
            ));
        } finally {
            window.pendingSync = false;
            window.pendingReopen = false;
        }
    }

    private void returnCursor(Player viewer, PacketWindow window) {
        if (viewer == null || window == null || window.cursor == null || window.cursor.getType().isAir()) {
            return;
        }
        ItemStack returned = window.cursor.clone();
        Map<Integer, ItemStack> overflow =
                viewer.getInventory().addItem(returned);
        for (ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover);
            }
        }
        window.cursor = null;
        debug(viewer, "common.gui.packet_cursor_return", windowFields(
                window,
                GuiDebugSupport.replacements(
                        "cursor_item_type", GuiDebugSupport.itemType(returned),
                        "cursor_item_amount", GuiDebugSupport.itemAmount(returned)
                )
        ));
    }

    private void dispatchViewerEvent(Player viewer,
            PacketWindow expectedWindow,
            String phase,
            Runnable task) {
        AtomicBoolean retiredOnce = new AtomicBoolean();
        Runnable retired = () -> {
            if (!retiredOnce.compareAndSet(false, true)) {
                return;
            }
            debug(viewer, "common.gui.packet_dispatch_retired", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements("phase", phase)
            ));
            retireWindow(viewer.getUniqueId(), expectedWindow);
        };
        try {
            debug(viewer, "common.gui.packet_dispatch_scheduled", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements("phase", phase)
            ));
            if (executionDispatcher.runEntity(plugin, viewer, () -> {
                try {
                    debug(viewer, "common.gui.packet_dispatch_execute", windowFields(
                            expectedWindow,
                            GuiDebugSupport.replacements("phase", phase)
                    ));
                    task.run();
                } catch (Throwable throwable) {
                    debug(viewer, "common.gui.packet_dispatch_task_failed", GuiDebugSupport.errorFields(
                            throwable,
                            windowFields(expectedWindow, GuiDebugSupport.replacements("phase", phase))
                    ));
                    if (isCurrentSession(viewer.getUniqueId(), expectedWindow)) {
                        authoritativeResync(viewer, expectedWindow, phase + "-exception");
                    }
                }
            }, retired) != null) {
                return;
            }
            if (retiredOnce.compareAndSet(false, true)) {
                debug(viewer, "common.gui.packet_dispatch_rejected", windowFields(
                        expectedWindow,
                        GuiDebugSupport.replacements("phase", phase)
                ));
                retireWindow(viewer.getUniqueId(), expectedWindow);
            }
        } catch (RuntimeException | LinkageError exception) {
            if (retiredOnce.compareAndSet(false, true)) {
                debug(viewer, "common.gui.packet_dispatch_failed", GuiDebugSupport.errorFields(
                        exception,
                        windowFields(expectedWindow, GuiDebugSupport.replacements("phase", phase))
                ));
                retireWindow(viewer.getUniqueId(), expectedWindow);
            }
        }
    }

    private void retireWindow(UUID viewerId, PacketWindow window) {
        if (windows.remove(viewerId, window)) {
            Player viewer = window.session.viewer();
            if (viewer != null && viewer.isOnline()) {
                returnCursor(viewer, window);
            }
        }
        GuiSessionRegistry registry = window.session.registry();
        if (registry != null) {
            registry.removeSession(viewerId, window.session);
        }
    }

    private ItemStack toBukkitItem(com.github.retrooper.packetevents.protocol.item.ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemStack converted = SpigotConversionUtil.toBukkitItemStack(item);
        return converted == null || converted.getType().isAir() ? null : converted;
    }

    private Map<Integer, ItemStack> toBukkitItems(
            Optional<Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ItemStack> converted = new LinkedHashMap<>();
        for (Map.Entry<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> entry
                : items.get().entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0) {
                continue;
            }
            ItemStack item = toBukkitItem(entry.getValue());
            converted.put(entry.getKey(), item == null ? new ItemStack(Material.AIR) : item);
        }
        return Map.copyOf(converted);
    }

    private void applyPlayerInventoryChanges(Player viewer,
            int containerTopSize,
            Map<Integer, ItemStack> changedItems) {
        if (viewer == null || changedItems == null || changedItems.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : changedItems.entrySet()) {
            int playerSlot = toPlayerInventorySlot(entry.getKey(), containerTopSize);
            if (playerSlot < 0) {
                continue;
            }
            ItemStack item = entry.getValue();
            viewer.getInventory().setItem(playerSlot,
                    item == null || item.getType().isAir() ? null : item.clone());
        }
    }

    private int toPlayerInventorySlot(int rawSlot, int containerTopSize) {
        int offset = rawSlot - containerTopSize;
        if (offset < 0 || offset >= PLAYER_INVENTORY_SLOTS) {
            return -1;
        }
        return offset < 27 ? offset + 9 : offset - 27;
    }

    private void handleClick(Player viewer, PacketWindow expectedWindow, ClickSnapshot click) {
        UUID viewerId = viewer.getUniqueId();
        if (!isCurrentSession(viewerId, expectedWindow)) {
            debug(viewer, "common.gui.packet_click_dropped_stale_window_or_session", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements("incoming_window_id", click.windowId())
            ));
            retireWindow(viewerId, expectedWindow);
            return;
        }
        Optional<Integer> incomingState = click.stateId();
        int lastSentState = expectedWindow.lastSentStateId();
        if (incomingState.isPresent() && incomingState.get() != lastSentState) {
            debug(viewer, "common.gui.packet_state_mismatch", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements(
                            "incoming_state_id", incomingState.get(),
                            "expected_state_id", lastSentState
                    )
            ));
            authoritativeResync(viewer, expectedWindow, "state-mismatch");
            return;
        }
        if (incomingState.isEmpty()) {
            debug(viewer, "common.gui.packet_state_absent_compatibility", windowFields(expectedWindow));
        }
        if (click.clickType() == WindowClickType.QUICK_CRAFT
                || click.clickType() == WindowClickType.UNKNOWN) {
            debug(viewer, "common.gui.packet_click_rejected_unsupported_type", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements(
                            "click_type", click.clickType(),
                            "raw_slot", click.rawSlot(),
                            "button", click.button()
                    )
            ));
            authoritativeResync(viewer, expectedWindow, "unsupported-" + click.clickType().name().toLowerCase(Locale.ROOT));
            return;
        }

        int containerTopSize = expectedWindow.topSize;
        if (!isKnownClickRange(click, containerTopSize)) {
            debug(viewer, "common.gui.packet_click_rejected_invalid_range", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements(
                            "click_type", click.clickType(),
                            "raw_slot", click.rawSlot(),
                            "button", click.button()
                    )
            ));
            authoritativeResync(viewer, expectedWindow, "invalid-click-range");
            return;
        }

        if (!GuiClickThrottle.allow(expectedWindow.session)) {
            debug(viewer, "common.gui.packet_click_rejected_throttled", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements(
                            "click_type", click.clickType(),
                            "raw_slot", click.rawSlot(),
                            "interval_ms", GuiClickThrottle.intervalMs()
                    )
            ));
            authoritativeResync(viewer, expectedWindow, "throttled");
            return;
        }

        boolean top = click.rawSlot() < containerTopSize;
        PacketGuiClickContext context = new PacketGuiClickContext(
                viewer, expectedWindow, click, containerTopSize, top);
        expectedWindow.beginClick();
        String outcome = "handled";
        boolean handlerSucceeded = false;
        try {
            if (top) {
                GuiTemplate.ResolvedSlot slot = expectedWindow.session.template().resolvedSlotAt(click.rawSlot());
                playClickSound(expectedWindow.session, slot, context.clickType());
                expectedWindow.session.handler().onSlotClick(expectedWindow.session, context, slot);
            } else {
                expectedWindow.session.handler().onPlayerInventoryClick(expectedWindow.session, context);
            }
            handlerSucceeded = true;
        } catch (Throwable throwable) {
            outcome = "handler-exception";
            debug(viewer, "common.gui.packet_click_handler_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(
                            expectedWindow,
                            GuiDebugSupport.replacements(
                                    "click_type", click.clickType(),
                                    "raw_slot", click.rawSlot(),
                                    "button", click.button()
                            )
                    )
            ));
        } finally {
            boolean refreshRequested = expectedWindow.pendingSync;
            if (handlerSucceeded && !context.isCancelled()) {
                if (!top) {
                    applyPlayerInventoryChanges(viewer, containerTopSize, click.changedItems());
                }
                if (!context.cursorChanged()) {
                    expectedWindow.setCursor(click.carriedItem());
                }
            }
            expectedWindow.finishClick();
            if (isCurrentSession(viewerId, expectedWindow)) {
                authoritativeResync(viewer, expectedWindow,
                        outcome + (refreshRequested ? "-refresh-requested" : "-authoritative"));
            } else {
                windows.remove(viewerId, expectedWindow);
                debug(viewer, "common.gui.packet_click_final_sync_skipped_window_or_session_replaced_or_closed",
                        windowFields(expectedWindow));
            }
        }
    }

    private boolean isKnownClickRange(ClickSnapshot click, int containerTopSize) {
        if (click == null || containerTopSize <= 0
                || click.rawSlot() < 0
                || click.rawSlot() >= containerTopSize + PLAYER_INVENTORY_SLOTS) {
            return false;
        }
        return switch (click.clickType()) {
            case PICKUP, QUICK_MOVE, THROW -> click.button() == 0 || click.button() == 1;
            case SWAP -> (click.button() >= 0 && click.button() < 9) || click.button() == 40;
            case CLONE -> click.button() == 2;
            case PICKUP_ALL -> click.button() == 0;
            case QUICK_CRAFT, UNKNOWN -> false;
        };
    }

    private void handleClose(Player viewer, PacketWindow expectedWindow, int windowId) {
        UUID viewerId = viewer.getUniqueId();
        if (expectedWindow.windowId != windowId || !isCurrentSession(viewerId, expectedWindow)) {
            debug(viewer, "common.gui.packet_close_receive_dropped_stale_window_or_session", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements("incoming_window_id", windowId)
            ));
            if (expectedWindow.windowId == windowId) {
                retireWindow(viewerId, expectedWindow);
            }
            return;
        }
        GuiSession session = expectedWindow.session;
        debug(viewer, "common.gui.packet_receive_close", windowFields(
                expectedWindow,
                GuiDebugSupport.replacements(
                        "cursor_item_type", GuiDebugSupport.itemType(expectedWindow.cursor),
                        "cursor_item_amount", GuiDebugSupport.itemAmount(expectedWindow.cursor)
                )
        ));
        try {

            returnCursor(viewer, expectedWindow);
            session.handler().onClose(session, new PacketGuiCloseContext(viewer, expectedWindow));
        } catch (Throwable throwable) {
            debug(viewer, "common.gui.packet_close_handler_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(expectedWindow)
            ));
        } finally {
            GuiSessionRegistry registry = session.registry();
            if (registry != null) {
                registry.removeSession(viewerId, session);
            }
            windows.remove(viewerId, expectedWindow);
        }
    }

    private boolean isCurrentWindow(UUID viewerId, PacketWindow window) {
        return viewerId != null && window != null && windows.get(viewerId) == window;
    }

    private boolean isCurrentSession(UUID viewerId, PacketWindow window) {
        if (!isCurrentWindow(viewerId, window)) {
            return false;
        }
        GuiSessionRegistry registry = window.session.registry();
        return registry != null && registry.activeSession(viewerId) == window.session;
    }

    private void playClickSound(GuiSession session,
            GuiTemplate.ResolvedSlot slot,
            GuiClickType clickType) {
        if (session == null || slot == null) {
            return;
        }
        SoundParser.SoundDefinition sound = slot.definition().soundFor(clickType);
        if (sound == null) {
            return;
        }
        var resolved = SoundParser.resolve(sound);
        if (resolved != null) {
            session.viewer().playSound(session.viewer().getLocation(), resolved, sound.volume(), sound.pitch());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player viewer = event.getPlayer();
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window != null) {
            returnCursor(viewer, window);
            windows.remove(viewer.getUniqueId(), window);
            debug(viewer, "common.gui.packet_quit_cleanup", windowFields(window));
        }
    }

    private void debug(Player viewer, String langKey, Map<String, ?> replacements) {
        GuiDebugSupport.log(plugin, viewer, langKey, replacements);
    }

    private Map<String, Object> windowFields(PacketWindow window) {
        return windowFields(window, Map.of());
    }

    private Map<String, Object> windowFields(PacketWindow window, Map<String, ?> fields) {
        return GuiDebugSupport.windowFields(
                window.windowId,
                window.lastSentStateId(),
                window.topSize,
                window.session,
                fields
        );
    }

    static record ClickSnapshot(
            int windowId,
            Optional<Integer> stateId,
            int rawSlot,
            int button,
            WindowClickType clickType,
            ItemStack carriedItem,
            Map<Integer, ItemStack> changedItems) {

        ClickSnapshot {
            stateId = stateId == null ? Optional.empty() : stateId;
            clickType = clickType == null ? WindowClickType.UNKNOWN : clickType;
            carriedItem = carriedItem == null ? null : carriedItem.clone();
            if (changedItems == null || changedItems.isEmpty()) {
                changedItems = Map.of();
            } else {
                Map<Integer, ItemStack> copy = new LinkedHashMap<>();
                changedItems.forEach((slot, item) -> copy.put(slot, item == null ? new ItemStack(Material.AIR) : item.clone()));
                changedItems = Map.copyOf(copy);
            }
        }
    }

    static final class PacketWindow {

        private final int windowId;
        private final GuiSession session;
        private int topSize;
        private ItemStack[] topItems;
        private ItemStack cursor;
        private final AtomicInteger stateId = new AtomicInteger();
        private volatile int lastSentStateId;
        private boolean handlingClick;
        private boolean pendingSync;
        private boolean pendingReopen;

        PacketWindow(int windowId, int topSize, GuiSession session) {
            this.windowId = windowId;
            this.topSize = topSize;
            this.session = session;
            this.topItems = new ItemStack[topSize];
        }

        int nextStateId() {
            int next = stateId.updateAndGet(current -> current == Integer.MAX_VALUE ? 0 : current + 1);
            lastSentStateId = next;
            return next;
        }

        int lastSentStateId() {
            return lastSentStateId;
        }

        void beginClick() {
            handlingClick = true;
            pendingSync = false;
            pendingReopen = false;
        }

        void finishClick() {
            handlingClick = false;
        }

        int topSize() {
            return topSize;
        }

        ItemStack topItem(int slot) {
            return slot >= 0 && slot < topSize ? topItems[slot] : null;
        }

        ItemStack cursor() {
            return cursor;
        }

        void setCursor(ItemStack cursor) {
            this.cursor = cursor == null || cursor.getType().isAir() ? null : cursor.clone();
        }

        GuiSession session() {
            return session;
        }
    }

    private final class ClickListener extends PacketListenerAbstract {

        private ClickListener() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                Object playerObject = event.getPlayer();
                if (!(playerObject instanceof Player viewer)) {
                    return;
                }
                UUID viewerId = viewer.getUniqueId();
                PacketWindow expectedWindow = windows.get(viewerId);
                if (expectedWindow == null) {
                    return;
                }
                WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
                ClickSnapshot click = new ClickSnapshot(
                        packet.getWindowId(),
                        packet.getStateId(),
                        packet.getSlot(),
                        packet.getButton(),
                        packet.getWindowClickType(),
                        toBukkitItem(packet.getCarriedItemStack()),
                        toBukkitItems(packet.getSlots())
                );
                if (click.windowId() != expectedWindow.windowId || windows.get(viewerId) != expectedWindow) {
                    return;
                }
                event.setCancelled(true);
                Map<String, Object> clickFields = GuiDebugSupport.replacements(
                        "click_type", click.clickType(),
                        "raw_slot", click.rawSlot(),
                        "button", click.button(),
                        "carried_item_type", GuiDebugSupport.itemType(click.carriedItem()),
                        "carried_item_amount", GuiDebugSupport.itemAmount(click.carriedItem()),
                        "changed_item_count", click.changedItems().size()
                );
                if (click.stateId().isPresent()) {
                    clickFields.put("incoming_state_id", click.stateId().get());
                    debug(viewer, "common.gui.packet_receive_click",
                            windowFields(expectedWindow, clickFields));
                } else {
                    debug(viewer, "common.gui.packet_receive_click_state_absent",
                            windowFields(expectedWindow, clickFields));
                }
                dispatchViewerEvent(viewer, expectedWindow, "click",
                        () -> handleClick(viewer, expectedWindow, click));
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
                Object playerObject = event.getPlayer();
                if (!(playerObject instanceof Player viewer)) {
                    return;
                }
                UUID viewerId = viewer.getUniqueId();
                PacketWindow expectedWindow = windows.get(viewerId);
                if (expectedWindow == null) {
                    return;
                }
                WrapperPlayClientCloseWindow packet = new WrapperPlayClientCloseWindow(event);
                int windowId = packet.getWindowId();
                if (windowId != expectedWindow.windowId || windows.get(viewerId) != expectedWindow) {
                    return;
                }
                debug(viewer, "common.gui.packet_receive_close", windowFields(
                        expectedWindow,
                        GuiDebugSupport.replacements(
                                "cursor_item_type", GuiDebugSupport.itemType(expectedWindow.cursor),
                                "cursor_item_amount", GuiDebugSupport.itemAmount(expectedWindow.cursor)
                        )
                ));
                dispatchViewerEvent(viewer, expectedWindow, "close",
                        () -> handleClose(viewer, expectedWindow, windowId));
            }
        }
    }
}
