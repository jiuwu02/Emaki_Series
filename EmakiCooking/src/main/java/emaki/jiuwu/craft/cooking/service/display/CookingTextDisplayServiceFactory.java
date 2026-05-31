package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CookingTextDisplayServiceFactory {

    private static final String PACKET_EVENTS_PLUGIN = "PacketEvents";

    private CookingTextDisplayServiceFactory() {
    }

    public static CookingTextDisplayService create(JavaPlugin plugin, CookingSettingsService settingsService) {
        String backend = settingsService.displayEntitiesBackend();
        if ("bukkit".equals(backend)) {
            return new BukkitCookingTextDisplayService(plugin);
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        boolean packetEventsEnabled = pluginManager.isPluginEnabled(PACKET_EVENTS_PLUGIN);
        if (packetEventsEnabled) {
            try {
                if (PacketEventsCookingTextDisplayService.isRuntimeSupported()) {
                    return new PacketEventsCookingTextDisplayService(plugin, settingsService);
                }
                plugin.getLogger().warning("PacketEvents text display backend requires Minecraft 1.19.4 or newer; using Bukkit text display backend.");
            } catch (LinkageError | RuntimeException exception) {
                plugin.getLogger().warning("PacketEvents text display backend is unavailable: " + exception.getMessage());
            }
        } else if ("packet_events".equals(backend)) {
            plugin.getLogger().warning("PacketEvents text display backend was requested but PacketEvents is not enabled; using Bukkit text display backend.");
        }
        return new BukkitCookingTextDisplayService(plugin);
    }
}
