package emaki.jiuwu.craft.attribute.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;

public final class PluginIntegrationListener implements Listener {

    private final EmakiAttributePlugin plugin;

    public PluginIntegrationListener(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("MythicMobs".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensureMythicBridge();
            if (plugin.mythicBridge() != null) {
                plugin.mythicBridge().resyncActiveMobs();
            }
        }
        if ("PlaceholderAPI".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensurePlaceholderExpansion();
        }
        if ("MMOItems".equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.ensureMmoItemsBridge();
        }
    }
}
