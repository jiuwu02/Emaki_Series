package emaki.jiuwu.craft.codex.advancement.packet;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;

import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

/**
 * Manages the lifecycle of the {@link AdvancementPacketCoordinateChannel} PacketEvents
 * listener and the {@link AdvancementResyncService}. The gateway is safe to construct
 * even when PacketEvents is absent: it only touches PacketEvents types inside
 * {@link #register()} / {@link #shutdown()} / {@link #resyncAll()} after probing that the
 * dependency is present, so a missing soft-dependency never triggers
 * {@code NoClassDefFoundError}.
 *
 * <p>The coordinate listener resolves coordinates live from the {@link AdvancementRegistrar},
 * so it is registered once on enable and unregistered once on disable; it does not need to be
 * rebuilt on reload (a reload only refreshes the registrar's advancement map, which the
 * listener reads on the next packet).
 *
 * <p>Advancement resync is independent of the coordinate-injection toggle: even when
 * coordinates are disabled, {@link #resyncAll()} still re-pushes advancements to online
 * players after a reload as long as PacketEvents is present.
 */
public final class AdvancementPacketGateway {

    private final JavaPlugin plugin;
    private final AdvancementRegistrar registrar;
    private final ItemSourceService itemSourceService;
    private final boolean enabled;

    private PacketListenerCommon registeredListener;
    // Instantiated lazily (references PacketEvents types) only when PacketEvents is present.
    private AdvancementResyncService resyncService;

    /**
     * @param plugin            the owning plugin (for the logger and key namespace)
     * @param registrar         the advancement registrar consulted for per-node coordinates and resync
     * @param itemSourceService corelib service used by resync to resolve advancement icons
     * @param enabled           whether coordinate injection is enabled in config
     */
    public AdvancementPacketGateway(JavaPlugin plugin,
            AdvancementRegistrar registrar,
            ItemSourceService itemSourceService,
            boolean enabled) {
        this.plugin = plugin;
        this.registrar = registrar;
        this.itemSourceService = itemSourceService;
        this.enabled = enabled;
    }

    /**
     * Registers the coordinate injection listener when enabled and PacketEvents is available.
     *
     * @return {@code true} when the listener was registered (coordinate injection is active)
     */
    public boolean register() {
        if (!enabled || registeredListener != null || !isPacketEventsPresent()) {
            return false;
        }
        try {
            String namespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
            AdvancementPacketCoordinateChannel listener =
                    new AdvancementPacketCoordinateChannel(registrar, namespace, plugin.getLogger());
            registeredListener = PacketEvents.getAPI().getEventManager().registerListener(listener);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Codex] Advancement coordinate channel unavailable, skipped: "
                    + throwable.getMessage());
            registeredListener = null;
            return false;
        }
    }

    /** Unregisters the listener if it was registered. Safe to call when never registered. */
    public void shutdown() {
        if (registeredListener == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(registeredListener);
        } catch (Throwable ignored) {
            // best-effort cleanup on disable
        } finally {
            registeredListener = null;
        }
    }

    /** {@return whether the coordinate channel is enabled in config and currently active} */
    public boolean isActive() {
        return registeredListener != null;
    }

    /** {@return whether PacketEvents is installed, so advancement resync can push packets} */
    public boolean canResync() {
        return isPacketEventsPresent();
    }

    /**
     * Re-pushes all registered EmakiCodex advancements to online players so the client
     * advancement screen refreshes without a relog. No-op (returns -1) when PacketEvents
     * is absent, letting the caller fall back to telling admins that players must reconnect.
     *
     * @return the number of players re-synced, or {@code -1} when PacketEvents is unavailable
     */
    public int resyncAll() {
        if (!isPacketEventsPresent()) {
            return -1;
        }
        try {
            return resyncService().resyncAll();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Codex] Advancement resync skipped: " + throwable.getMessage());
            return -1;
        }
    }

    /**
     * Re-pushes all registered EmakiCodex advancements to a single player so their client
     * advancement screen shows the runtime tree immediately on join, without waiting for a
     * relog or reload. No-op when PacketEvents is absent (the client still receives the
     * vanilla auto-sent tree, just without coordinate injection).
     *
     * @param player the target player
     * @return {@code true} when the packet was sent
     */
    public boolean resync(org.bukkit.entity.Player player) {
        if (player == null || !isPacketEventsPresent()) {
            return false;
        }
        try {
            return resyncService().resync(player);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Codex] Advancement resync skipped for "
                    + player.getName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private AdvancementResyncService resyncService() {
        if (resyncService == null) {
            resyncService = new AdvancementResyncService(plugin, registrar, itemSourceService);
        }
        return resyncService;
    }

    private boolean isPacketEventsPresent() {
        return Bukkit.getPluginManager().getPlugin("packetevents") != null
                || Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
    }
}
