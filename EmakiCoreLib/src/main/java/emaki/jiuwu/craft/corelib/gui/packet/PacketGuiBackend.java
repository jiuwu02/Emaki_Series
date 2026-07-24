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
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
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

    public static boolean isRuntimeSupported() {
        return PacketEvents.getAPI()
                .getServerManager()
                .getVersion()
                .isNewerThanOrEquals(ServerVersion.V_1_19_4);
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
        debug(viewer, "packet open | " + describe(window)
                + " replaced=" + (previous == null ? "none" : previous.windowId));
        try {
            sendOpenWindow(viewer, window);
            applyTopItems(window, renderedSlots);
            sendWindowItems(viewer, window);
        } catch (RuntimeException | Error throwable) {
            windows.remove(viewer.getUniqueId(), window);
            debug(viewer, "packet open failed | " + describe(window) + " error=" + error(throwable));
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
            debug(viewer, "packet apply ignored | reason=window-or-session-mismatch "
                    + GuiDebugSupport.describeSession(session));
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
                debug(viewer, "packet apply deferred | " + describe(window));
                return;
            }
            debug(viewer, "packet apply | " + describe(window));
            sendWindowItems(viewer, window);
        } catch (RuntimeException | Error throwable) {
            debug(viewer, "packet apply failed | " + describe(window) + " error=" + error(throwable));
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
            debug(viewer, "packet close ignored | reason=window-or-session-mismatch "
                    + GuiDebugSupport.describeSession(session));
            return;
        }
        debug(viewer, "packet close | " + describe(window));
        try {
            returnCursor(viewer, window);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                    new WrapperPlayServerCloseWindow(window.windowId));
        } catch (Throwable throwable) {
            debug(viewer, "packet close failed | " + describe(window) + " error=" + error(throwable));
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
                        debug(null, "packet shutdown window close incomplete | error=" + error(throwable));
                    }
                    snapshot.forEach(this::retireWindow);
                    return null;
                });
        CompletableFuture<Void> unregisterListeners = unregisterListenersAsync();
        CompletableFuture.allOf(closeWindows, unregisterListeners).whenComplete((ignored, throwable) -> {
            windows.clear();
            if (throwable != null) {
                debug(null, "packet shutdown incomplete | error=" + error(throwable));
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
                debug(null, "packet listener unregister failed | error=" + error(exception));
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
                    debug(null, "packet Bukkit listener unregister incomplete | error=" + error(throwable));
                }
                return null;
            });
        } catch (RuntimeException | LinkageError exception) {
            debug(null, "packet Bukkit listener unregister dispatch failed | error=" + error(exception));
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
            debug(null, "packet shutdown dispatch retired | " + describe(window));
            completeWindowCleanup(viewerId, window, viewer, false, false, cleanupStarted, completion);
        };
        if (viewer != null) {
            try {
                if (executionDispatcher.runEntity(plugin, viewer, entityCleanup, retiredCleanup) != null) {
                    return completion;
                }
                debug(null, "packet shutdown dispatch rejected | " + describe(window));
            } catch (RuntimeException | LinkageError exception) {
                debug(null, "packet shutdown dispatch failed | " + describe(window)
                        + " error=" + error(exception));
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
            debug(null, "packet shutdown cleanup failed | " + describe(window)
                    + " error=" + error(throwable));
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
                    debug(viewer, "packet shutdown close handler failed | " + describe(window)
                            + " error=" + error(throwable));
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
        debug(viewer, "packet send open | " + describe(window));
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
        debug(viewer, "packet send items | " + describe(window) + " stateId=" + stateId);
    }

    private void authoritativeResync(Player viewer, PacketWindow window, String reason) {
        if (!isCurrentSession(viewer.getUniqueId(), window)) {
            windows.remove(viewer.getUniqueId(), window);
            debug(viewer, "packet resync skipped | reason=stale-window-or-session cause=" + reason
                    + " " + describe(window));
            return;
        }
        try {
            if (window.pendingReopen) {
                sendOpenWindow(viewer, window);
            }
            sendWindowItems(viewer, window);
            debug(viewer, "packet resync | cause=" + reason + " " + describe(window));
        } catch (Throwable throwable) {
            debug(viewer, "packet resync failed | cause=" + reason + " " + describe(window)
                    + " error=" + error(throwable));
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
            debug(viewer, "packet dispatch retired | phase=" + phase + " " + describe(expectedWindow));
            retireWindow(viewer.getUniqueId(), expectedWindow);
        };
        try {
            debug(viewer, "packet dispatch scheduled | phase=" + phase + " " + describe(expectedWindow));
            if (executionDispatcher.runEntity(plugin, viewer, () -> {
                try {
                    debug(viewer, "packet dispatch execute | phase=" + phase + " " + describe(expectedWindow));
                    task.run();
                } catch (Throwable throwable) {
                    debug(viewer, "packet dispatch task failed | phase=" + phase + " "
                            + describe(expectedWindow) + " error=" + error(throwable));
                    if (isCurrentSession(viewer.getUniqueId(), expectedWindow)) {
                        authoritativeResync(viewer, expectedWindow, phase + "-exception");
                    }
                }
            }, retired) != null) {
                return;
            }
            if (retiredOnce.compareAndSet(false, true)) {
                debug(viewer, "packet dispatch rejected | phase=" + phase + " " + describe(expectedWindow));
                retireWindow(viewer.getUniqueId(), expectedWindow);
            }
        } catch (RuntimeException | LinkageError exception) {
            if (retiredOnce.compareAndSet(false, true)) {
                debug(viewer, "packet dispatch failed | phase=" + phase + " " + describe(expectedWindow)
                        + " error=" + error(exception));
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
            debug(viewer, "packet click dropped | reason=stale-window-or-session "
                    + describe(expectedWindow) + " incomingWindow=" + click.windowId());
            windows.remove(viewerId, expectedWindow);
            return;
        }
        Optional<Integer> incomingState = click.stateId();
        int lastSentState = expectedWindow.lastSentStateId();
        if (incomingState.isPresent() && incomingState.get() != lastSentState) {
            debug(viewer, "packet state mismatch | " + describe(expectedWindow)
                    + " incoming=" + incomingState.get() + " expected=" + lastSentState);
            authoritativeResync(viewer, expectedWindow, "state-mismatch");
            return;
        }
        if (incomingState.isEmpty()) {
            debug(viewer, "packet state absent | compatibility=true " + describe(expectedWindow));
        }
        if (click.clickType() == WindowClickType.QUICK_CRAFT
                || click.clickType() == WindowClickType.UNKNOWN) {
            debug(viewer, "packet click rejected | reason=unsupported-type type=" + click.clickType()
                    + " slot=" + click.rawSlot() + " button=" + click.button()
                    + " " + describe(expectedWindow));
            authoritativeResync(viewer, expectedWindow, "unsupported-" + click.clickType().name().toLowerCase());
            return;
        }

        int containerTopSize = expectedWindow.topSize;
        if (!isKnownClickRange(click, containerTopSize)) {
            debug(viewer, "packet click rejected | reason=invalid-range type=" + click.clickType()
                    + " slot=" + click.rawSlot() + " button=" + click.button()
                    + " topSize=" + containerTopSize + " " + describe(expectedWindow));
            authoritativeResync(viewer, expectedWindow, "invalid-click-range");
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
            debug(viewer, "packet click handler failed | type=" + click.clickType()
                    + " slot=" + click.rawSlot() + " button=" + click.button()
                    + " " + describe(expectedWindow) + " error=" + error(throwable));
        } finally {
            boolean refreshRequested = expectedWindow.pendingSync;
            expectedWindow.finishClick();
            if (isCurrentSession(viewerId, expectedWindow)) {
                authoritativeResync(viewer, expectedWindow,
                        outcome + (refreshRequested ? "-refresh-requested" : "-authoritative"));
            } else {
                windows.remove(viewerId, expectedWindow);
                debug(viewer, "packet click final sync skipped | reason=window-or-session-replaced-or-closed "
                        + describe(expectedWindow));
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
            debug(viewer, "packet close receive dropped | reason=stale-window-or-session expected="
                    + expectedWindow.windowId + " incoming=" + windowId);
            return;
        }
        GuiSession session = expectedWindow.session;
        debug(viewer, "packet close receive | " + describe(expectedWindow));
        try {
            returnCursor(viewer, expectedWindow);
            session.handler().onClose(session, new PacketGuiCloseContext(viewer, expectedWindow));
        } catch (Throwable throwable) {
            debug(viewer, "packet close handler failed | " + describe(expectedWindow)
                    + " error=" + error(throwable));
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
            debug(viewer, "packet quit cleanup | " + describe(window));
        }
    }

    private void debug(Player viewer, String message) {
        GuiDebugSupport.log(plugin, viewer, message);
    }

    private String describe(PacketWindow window) {
        return "window=" + window.windowId
                + " state=" + window.lastSentStateId()
                + " topSize=" + window.topSize
                + " " + GuiDebugSupport.describeSession(window.session);
    }

    private static String error(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ":" + message);
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
                debug(viewer, "packet receive click | type=" + click.clickType()
                        + " slot=" + click.rawSlot() + " button=" + click.button()
                        + " incomingState=" + click.stateId().map(String::valueOf).orElse("absent")
                        + " " + describe(expectedWindow));
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
                debug(viewer, "packet receive close | " + describe(expectedWindow));
                dispatchViewerEvent(viewer, expectedWindow, "close",
                        () -> handleClose(viewer, expectedWindow, windowId));
            }
        }
    }
}
