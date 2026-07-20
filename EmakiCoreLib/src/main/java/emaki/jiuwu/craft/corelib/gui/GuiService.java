package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

public final class GuiService implements Listener, GuiSessionRegistry {

    private final JavaPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final AsyncGuiRenderer asyncGuiRenderer;
    private final GuiBackend backend;
    private final ConfiguredItemService configuredItemService;

    public GuiService(JavaPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor) {
        this(plugin, executionDispatcher, asyncTaskScheduler, performanceMonitor, new BukkitGuiBackend());
    }

    public GuiService(JavaPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor,
            GuiBackend backend) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.executionDispatcher = java.util.Objects.requireNonNull(executionDispatcher, "executionDispatcher");
        this.asyncGuiRenderer = new AsyncGuiRenderer(
                asyncTaskScheduler,
                performanceMonitor
        );
        this.backend = backend == null ? new BukkitGuiBackend() : backend;
        this.configuredItemService = this.backend.configuredItemService();
    }

    public GuiSession open(GuiOpenRequest request) {
        if (request == null || request.viewer() == null || request.template() == null) {
            return null;
        }
        UUID viewerId = request.viewer().getUniqueId();
        close(viewerId);
        GuiSession session = newSession(request);
        sessions.put(viewerId, session);
        session.open();
        return session;
    }

    public CompletableFuture<GuiSession> openAsync(GuiOpenRequest request) {
        if (request == null || request.viewer() == null || request.template() == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<GuiSession> future = new CompletableFuture<>();
        org.bukkit.plugin.Plugin owner = request.owner() == null ? plugin : request.owner();
        Runnable outerRetired = () -> future.completeExceptionally(new RejectedExecutionException(
                "GUI viewer retired before open scheduling completed."));
        try {
            if (executionDispatcher.runEntity(owner, request.viewer(), () -> {
                UUID viewerId = request.viewer().getUniqueId();
                close(viewerId);
                GuiSession session = newSession(request);
                sessions.put(viewerId, session);
                asyncGuiRenderer.prepare(session).whenComplete((renderedSlots, throwable) -> {
                    Runnable innerRetired = () -> {
                        sessions.remove(viewerId, session);
                        future.completeExceptionally(new RejectedExecutionException(
                                "GUI viewer retired before rendered slots could be applied."));
                    };
                    try {
                        if (executionDispatcher.runEntity(owner, request.viewer(), () -> {
                            if (throwable != null) {
                                sessions.remove(viewerId, session);
                                future.completeExceptionally(throwable);
                                return;
                            }
                            if (sessions.get(viewerId) != session) {
                                future.complete(null);
                                return;
                            }
                            session.backend().open(session, renderedSlots);
                            future.complete(session);
                        }, innerRetired) == null) {
                            innerRetired.run();
                        }
                    } catch (Throwable dispatchFailure) {
                        sessions.remove(viewerId, session);
                        future.completeExceptionally(dispatchFailure);
                    }
                });
            }, outerRetired) == null) {
                outerRetired.run();
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private GuiSession newSession(GuiOpenRequest request) {
        return new GuiSession(
                request.owner(),
                request.viewer(),
                request.template(),
                request.replacements(),
                request.itemFactory(),
                configuredItemService,
                request.renderer(),
                request.handler(),
                resolveBackend(),
                this
        );
    }













    private GuiBackend resolveBackend() {
        if (backend instanceof RegistryBackedGuiBackend proxy) {
            return proxy.resolveActive();
        }
        return backend;
    }

    public GuiSession getSession(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    public void close(UUID playerId) {
        closeSession(playerId, getSession(playerId));
    }

    public CompletableFuture<Void> closeAsync(UUID playerId) {
        return closeAsync(playerId, getSession(playerId));
    }

    public void closeAll() {
        Map<UUID, GuiSession> snapshot = Map.copyOf(sessions);
        snapshot.forEach(this::closeSession);
    }

    public CompletableFuture<Void> closeAllAsync() {
        Map<UUID, GuiSession> snapshot = Map.copyOf(sessions);
        List<CompletableFuture<Void>> closes = new ArrayList<>(snapshot.size());
        snapshot.forEach((viewerId, session) -> closes.add(closeAsync(viewerId, session)));
        return CompletableFuture.allOf(closes.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> closeAsync(UUID viewerId, GuiSession session) {
        if (viewerId == null || session == null || session.viewer() == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            Runnable retired = () -> future.completeExceptionally(new RejectedExecutionException(
                    "GUI viewer retired before close could complete: " + viewerId));
            if (executionDispatcher.runEntity(
                    session.owner() == null ? plugin : session.owner(),
                    session.viewer(),
                    () -> {
                        try {
                            closeSession(viewerId, session);
                            future.complete(null);
                        } catch (Throwable throwable) {
                            future.completeExceptionally(throwable);
                        }
                    }, retired) == null) {
                retired.run();
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void closeSession(UUID viewerId, GuiSession session) {
        if (viewerId == null || session == null || sessions.get(viewerId) != session) {
            return;
        }
        session.backend().close(session);
        if (sessions.get(viewerId) != session) {
            return;
        }
        session.handler().onClose(session, new SessionGuiCloseContext(session));
        sessions.remove(viewerId, session);
    }

    @Override
    public GuiSession activeSession(UUID viewerId) {
        return viewerId == null ? null : sessions.get(viewerId);
    }

    @Override
    public void removeSession(UUID viewerId, GuiSession session) {
        if (viewerId != null && session != null) {
            sessions.remove(viewerId, session);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        GuiSession session = resolveSession(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory());
        if (session == null || !isBukkitBacked(session)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        GuiClickContext click = new BukkitGuiClickContext(event);
        if (event.getClickedInventory() == session.getInventory()) {
            event.setCancelled(true);
            GuiTemplate.ResolvedSlot slot = session.template().resolvedSlotAt(event.getRawSlot());
            playClickSound(session, slot, GuiClickType.from(event));
            session.handler().onSlotClick(session, click, slot);
            return;
        }
        session.handler().onPlayerInventoryClick(session, click);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        GuiSession session = resolveSession(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory());
        if (session == null || !isBukkitBacked(session)) {
            return;
        }
        event.setCancelled(true);
        session.handler().onDrag(session, new BukkitGuiDragContext(event));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        GuiSession session = resolveSession(event.getPlayer().getUniqueId(), event.getInventory());
        if (session == null || !isBukkitBacked(session)) {
            return;
        }
        session.handler().onClose(session, new BukkitGuiCloseContext(event));
        sessions.remove(event.getPlayer().getUniqueId(), session);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            close(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.getPlayer() != null) {
            close(event.getPlayer().getUniqueId());
        }
    }

    private GuiSession resolveSession(UUID viewerId, Inventory inventory) {
        GuiSession session = viewerId == null ? null : sessions.get(viewerId);
        if (matchesInventory(session, inventory) && isManagedSession(session)) {
            return session;
        }
        if (inventory != null && inventory.getHolder() instanceof GuiSession holderSession
                && matchesInventory(holderSession, inventory) && isManagedSession(holderSession)) {
            return holderSession;
        }
        return null;
    }

    private boolean matchesInventory(GuiSession session, Inventory inventory) {
        return session != null && inventory != null && session.getInventory() == inventory;
    }

    private boolean isManagedSession(GuiSession session) {
        if (session == null || session.owner() != plugin || session.viewer() == null) {
            return false;
        }
        return sessions.get(session.viewer().getUniqueId()) == session;
    }

    private boolean isBukkitBacked(GuiSession session) {
        return session.backend() instanceof BukkitGuiBackend;
    }

    private void playClickSound(GuiSession session, GuiTemplate.ResolvedSlot slot, GuiClickType clickType) {
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

    private static final class SessionGuiCloseContext implements GuiCloseContext {

        private final GuiSession session;

        private SessionGuiCloseContext(GuiSession session) {
            this.session = session;
        }

        @Override
        public org.bukkit.entity.Player player() {
            return session.viewer();
        }

        @Override
        public int topInventorySize() {
            Inventory inventory = session.getInventory();
            return inventory == null ? 0 : inventory.getSize();
        }

        @Override
        public org.bukkit.inventory.ItemStack topInventoryItem(int slot) {
            Inventory inventory = session.getInventory();
            return inventory == null || slot < 0 || slot >= inventory.getSize() ? null : inventory.getItem(slot);
        }
    }
}
