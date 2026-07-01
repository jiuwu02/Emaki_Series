package emaki.jiuwu.craft.codex.advancement.packet;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;

import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;

/**
 * Manages the lifecycle of the {@link AdvancementPacketCoordinateChannel} PacketEvents
 * listener. The gateway is safe to construct even when PacketEvents is absent: it only
 * touches PacketEvents types inside {@link #register()} / {@link #shutdown()} after probing
 * that the dependency is present, mirroring {@code RecipeSyncGateway}'s isolation pattern so
 * a missing soft-dependency never triggers {@code NoClassDefFoundError}.
 *
 * <p>The listener resolves coordinates live from the {@link AdvancementRegistrar}, so it is
 * registered once on enable and unregistered once on disable; it does not need to be rebuilt
 * on reload (a reload only refreshes the registrar's advancement map, which the listener reads
 * on the next packet).
 */
public final class AdvancementPacketGateway {

    private final JavaPlugin plugin;
    private final AdvancementRegistrar registrar;
    private final boolean enabled;

    private PacketListenerCommon registeredListener;

    /**
     * @param plugin    the owning plugin (for the logger and key namespace)
     * @param registrar the advancement registrar consulted for per-node coordinates
     * @param enabled   whether coordinate injection is enabled in config
     */
    public AdvancementPacketGateway(JavaPlugin plugin, AdvancementRegistrar registrar, boolean enabled) {
        this.plugin = plugin;
        this.registrar = registrar;
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

    private boolean isPacketEventsPresent() {
        return Bukkit.getPluginManager().getPlugin("packetevents") != null
                || Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
    }
}
