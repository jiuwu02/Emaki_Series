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
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

public final class GuiService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final AsyncGuiRenderer asyncGuiRenderer;

    public GuiService(JavaPlugin plugin) {
        this(
                plugin,
                resolveAsyncTaskScheduler(),
                resolvePerformanceMonitor()
        );
    }

    public GuiService(JavaPlugin plugin,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor) {
        this.plugin = plugin;
        this.asyncGuiRenderer = new AsyncGuiRenderer(
                asyncTaskScheduler,
                performanceMonitor
        );
    }

    private static AsyncTaskScheduler resolveAsyncTaskScheduler() {
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibPlugin.getInstance();
        return coreLibPlugin == null ? null : coreLibPlugin.asyncTaskScheduler();
    }

    private static PerformanceMonitor resolvePerformanceMonitor() {
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibPlugin.getInstance();
        return coreLibPlugin == null ? null : coreLibPlugin.performanceMonitor();
    }

    public GuiSession open(GuiOpenRequest request) {
        if (request == null || request.viewer() == null || request.template() == null) {
            return null;
        }
        GuiSession existing = sessions.remove(request.viewer().getUniqueId());
        if (existing != null) {
            existing.viewer().closeInventory();
        }
        GuiSession session = new GuiSession(
                request.owner(),
                request.viewer(),
                request.template(),
                request.replacements(),
                request.itemFactory(),
                request.renderer(),
                request.handler()
        );
        sessions.put(request.viewer().getUniqueId(), session);
        session.open();
        return session;
    }

    public CompletableFuture<GuiSession> openAsync(GuiOpenRequest request) {
        if (request == null || request.viewer() == null || request.template() == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<GuiSession> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            GuiSession existing = sessions.remove(request.viewer().getUniqueId());
            if (existing != null) {
                existing.viewer().closeInventory();
            }
            GuiSession session = new GuiSession(
                    request.owner(),
                    request.viewer(),
                    request.template(),
                    request.replacements(),
                    request.itemFactory(),
                    request.renderer(),
                    request.handler()
            );
            sessions.put(request.viewer().getUniqueId(), session);
            asyncGuiRenderer.prepare(session)
                    .whenComplete((renderedSlots, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            sessions.remove(request.viewer().getUniqueId());
                            future.completeExceptionally(throwable);
                            return;
                        }
                        session.applyRenderedSlots(renderedSlots);
                        request.viewer().openInventory(session.getInventory());
                        future.complete(session);
                    }));
        });
        return future;
    }

    public GuiSession getSession(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    public void close(UUID playerId) {
        GuiSession session = getSession(playerId);
        if (session != null) {
            session.viewer().closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiSession session)) {
            return;
        }
        if (!isManagedSession(session)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);
            GuiTemplate.ResolvedSlot slot = session.template().resolvedSlotAt(event.getRawSlot());
            playClickSound(session, slot, GuiClickType.from(event));
            session.handler().onSlotClick(session, event, slot);
            return;
        }
        session.handler().onPlayerInventoryClick(session, event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiSession session)) {
            return;
        }
        if (!isManagedSession(session)) {
            return;
        }
        event.setCancelled(true);
        session.handler().onDrag(session, event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiSession session)) {
            return;
        }
        if (!isManagedSession(session)) {
            return;
        }
        sessions.remove(event.getPlayer().getUniqueId());
        session.handler().onClose(session, event);
    }

    private boolean isManagedSession(GuiSession session) {
        if (session == null || session.owner() != plugin || session.viewer() == null) {
            return false;
        }
        return sessions.get(session.viewer().getUniqueId()) == session;
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
