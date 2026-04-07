package emaki.jiuwu.craft.corelib.item;

import org.bukkit.Bukkit;

@FunctionalInterface
interface PluginAvailability {

    PluginAvailability BUKKIT = pluginName -> {
        var pluginManager = Bukkit.getPluginManager();
        return pluginManager != null && pluginManager.isPluginEnabled(pluginName);
    };

    boolean isPluginEnabled(String pluginName);
}
