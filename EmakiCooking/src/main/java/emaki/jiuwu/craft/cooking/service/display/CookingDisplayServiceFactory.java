package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CookingDisplayServiceFactory {

    private static final String PACKET_EVENTS_PLUGIN = "PacketEvents";

    private CookingDisplayServiceFactory() {
    }

    public static CookingDisplayService create(JavaPlugin plugin, CookingSettingsService settingsService) {
        String backend = settingsService.displayEntitiesBackend();
        if ("bukkit".equals(backend)) {
            return new BukkitCookingDisplayService(plugin);
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        boolean packetEventsEnabled = pluginManager.isPluginEnabled(PACKET_EVENTS_PLUGIN);
        if (packetEventsEnabled) {
            try {
                if (PacketEventsCookingDisplayService.isRuntimeSupported()) {
                    return new PacketEventsCookingDisplayService(plugin, settingsService);
                }
                plugin.getLogger().warning("PacketEvents display backend requires Minecraft 1.19.4 or newer; using Bukkit display backend.");
            } catch (LinkageError | RuntimeException exception) {
                plugin.getLogger().warning("PacketEvents display backend is unavailable: " + exception.getMessage());
            }
        } else if ("packet_events".equals(backend)) {
            plugin.getLogger().warning("PacketEvents display backend was requested but PacketEvents is not enabled; using Bukkit display backend.");
        }
        return new BukkitCookingDisplayService(plugin);
    }
}
