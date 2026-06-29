package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

public final class GuiService implements Listener, GuiSessionRegistry {

    private final JavaPlugin plugin;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final AsyncGuiRenderer asyncGuiRenderer;
    private final GuiBackend backend;

    public GuiService(JavaPlugin plugin,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor) {
        this(plugin, asyncTaskScheduler, performanceMonitor, new BukkitGuiBackend());
    }

    public GuiService(JavaPlugin plugin,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor,
            GuiBackend backend) {
        this.plugin = plugin;
        this.asyncGuiRenderer = new AsyncGuiRenderer(
                asyncTaskScheduler,
                performanceMonitor
        );
        this.backend = backend == null ? new BukkitGuiBackend() : backend;
    }

    public GuiSession open(GuiOpenRequest request) {
        if (request == null || request.viewer() == null || request.template() == null) {
            return null;
        }
        GuiSession existing = sessions.remove(request.viewer().getUniqueId());
        if (existing != null) {
            existing.backend().close(existing);
        }
        GuiSession session = newSession(request);
        sessions.put(request.viewer().getUniqueId(), session);
        session.open();
        return session;
    }

    public CompletableFuture<GuiSession> openAsync(GuiOpenRequest request) {
        if (request == null || request.viewer() == null || request.template() == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<GuiSession> future = new CompletableFuture<>();
        FoliaSchedulerAdapter.runEntityTask(plugin, request.viewer(), () -> {
            UUID viewerId = request.viewer().getUniqueId();
            GuiSession existing = sessions.remove(viewerId);
            if (existing != null) {
                existing.backend().close(existing);
            }
            GuiSession session = newSession(request);
            sessions.put(viewerId, session);
            asyncGuiRenderer.prepare(session)
                    .whenComplete((renderedSlots, throwable) -> FoliaSchedulerAdapter.runEntityTask(plugin, request.viewer(), () -> {
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
                    }));
        });
        return future;
    }

    private GuiSession newSession(GuiOpenRequest request) {
        return new GuiSession(
                request.owner(),
                request.viewer(),
                request.template(),
                request.replacements(),
                request.itemFactory(),
                request.renderer(),
                request.handler(),
                resolveBackend(),
                this
        );
    }

    /**
     * Resolves the concrete backend to bind to a new session.
     *
     * <p>{@code coreLib.guiBackend()} hands every {@link GuiService} a
     * {@link RegistryBackedGuiBackend} proxy. We unwrap it here (rather than in
     * the constructor) so the backend is resolved at open time — that lets an
     * optional backend plugin register after CoreLib has enabled. Each session
     * then keeps the resolved backend for its whole lifetime, which is also why
     * {@link #isBukkitBacked(GuiSession)} can still {@code instanceof}-test the
     * real backend. This unwrap is an internal CoreLib detail; the public
     * {@code GuiService} constructor signature is unchanged.</p>
     */
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
        GuiSession session = getSession(playerId);
        if (session != null) {
            session.backend().close(session);
        }
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
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiSession session)) {
            return;
        }
        if (!isManagedSession(session) || !isBukkitBacked(session)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        GuiClickContext click = new BukkitGuiClickContext(event);
        if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
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
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiSession session)) {
            return;
        }
        if (!isManagedSession(session) || !isBukkitBacked(session)) {
            return;
        }
        event.setCancelled(true);
        session.handler().onDrag(session, new BukkitGuiDragContext(event));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiSession session)) {
            return;
        }
        if (!isManagedSession(session) || !isBukkitBacked(session)) {
            return;
        }
        sessions.remove(event.getPlayer().getUniqueId(), session);
        session.handler().onClose(session, new BukkitGuiCloseContext(event));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.getPlayer() != null) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
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
}
