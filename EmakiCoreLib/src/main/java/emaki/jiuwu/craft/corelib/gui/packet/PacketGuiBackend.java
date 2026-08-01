package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
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

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiClickThrottle;
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
    public void open(GuiSession session, Map<Integer, org.bukkit.inventory.ItemStack> renderedSlots) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        PacketWindow window = new PacketWindow(nextWindowId(), topSize(session), session);
        PacketWindow previous = windows.put(viewer.getUniqueId(), window);
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
            windows.remove(viewer.getUniqueId(), window);
            debug(viewer, "common.gui.packet_open_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window)
            ));
            throw throwable;
        }
    }

    @Override
    public void applySlots(GuiSession session, Map<Integer, org.bukkit.inventory.ItemStack> renderedSlots) {
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
                window.topItems = new org.bukkit.inventory.ItemStack[desiredSize];
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

    private void applyTopItems(PacketWindow window, Map<Integer, org.bukkit.inventory.ItemStack> renderedSlots) {
        org.bukkit.inventory.ItemStack[] top = new org.bukkit.inventory.ItemStack[window.topSize];
        if (renderedSlots != null) {
            for (Map.Entry<Integer, org.bukkit.inventory.ItemStack> entry : renderedSlots.entrySet()) {
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
            windows.remove(viewer.getUniqueId(), window);
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
        if (window.cursor == null || window.cursor.getType().isAir()) {
            return;
        }
        Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                viewer.getInventory().addItem(window.cursor.clone());
        for (org.bukkit.inventory.ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover);
            }
        }
        window.cursor = null;
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
        windows.remove(viewerId, window);
        GuiSessionRegistry registry = window.session.registry();
        if (registry != null) {
            registry.removeSession(viewerId, window.session);
        }
    }

    private void handleClick(Player viewer, PacketWindow expectedWindow, ClickSnapshot click) {
        UUID viewerId = viewer.getUniqueId();
        if (!isCurrentSession(viewerId, expectedWindow)) {
            debug(viewer, "common.gui.packet_click_dropped_stale_window_or_session", windowFields(
                    expectedWindow,
                    GuiDebugSupport.replacements("incoming_window_id", click.windowId())
            ));
            windows.remove(viewerId, expectedWindow);
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
            authoritativeResync(viewer, expectedWindow, "unsupported-" + click.clickType().name().toLowerCase(java.util.Locale.ROOT));
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
        try {
            if (top) {
                GuiTemplate.ResolvedSlot slot = expectedWindow.session.template().resolvedSlotAt(click.rawSlot());
                playClickSound(expectedWindow.session, slot, context.clickType());
                expectedWindow.session.handler().onSlotClick(expectedWindow.session, context, slot);
            } else {
                expectedWindow.session.handler().onPlayerInventoryClick(expectedWindow.session, context);
            }
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
            return;
        }
        GuiSession session = expectedWindow.session;
        debug(viewer, "common.gui.packet_close_receive", windowFields(expectedWindow));
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
            emaki.jiuwu.craft.corelib.gui.GuiClickType clickType) {
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
        PacketWindow window = windows.remove(viewer.getUniqueId());
        if (window != null) {
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
            WindowClickType clickType) {

        ClickSnapshot {
            stateId = stateId == null ? Optional.empty() : stateId;
            clickType = clickType == null ? WindowClickType.UNKNOWN : clickType;
        }
    }

    static final class PacketWindow {

        private final int windowId;
        private final GuiSession session;
        private int topSize;
        private org.bukkit.inventory.ItemStack[] topItems;
        private org.bukkit.inventory.ItemStack cursor;
        private final AtomicInteger stateId = new AtomicInteger();
        private volatile int lastSentStateId;
        private boolean handlingClick;
        private boolean pendingSync;
        private boolean pendingReopen;

        PacketWindow(int windowId, int topSize, GuiSession session) {
            this.windowId = windowId;
            this.topSize = topSize;
            this.session = session;
            this.topItems = new org.bukkit.inventory.ItemStack[topSize];
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

        org.bukkit.inventory.ItemStack topItem(int slot) {
            return slot >= 0 && slot < topSize ? topItems[slot] : null;
        }

        org.bukkit.inventory.ItemStack cursor() {
            return cursor;
        }

        void setCursor(org.bukkit.inventory.ItemStack cursor) {
            this.cursor = cursor;
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
                        packet.getWindowClickType()
                );
                if (click.windowId() != expectedWindow.windowId || windows.get(viewerId) != expectedWindow) {
                    return;
                }
                event.setCancelled(true);
                Map<String, Object> clickFields = GuiDebugSupport.replacements(
                        "click_type", click.clickType(),
                        "raw_slot", click.rawSlot(),
                        "button", click.button()
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
                debug(viewer, "common.gui.packet_receive_close", windowFields(expectedWindow));
                dispatchViewerEvent(viewer, expectedWindow, "close",
                        () -> handleClose(viewer, expectedWindow, windowId));
            }
        }
    }
}
