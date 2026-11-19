package emaki.jiuwu.craft.kits.papi;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.log.LogTag;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;

public final class KitsPapiSupport {

    private static KitsPlaceholderExpansion expansion;

    private KitsPapiSupport() {
    }

    public static void register(EmakiKitsPlugin plugin) {
        if (expansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        expansion = new KitsPlaceholderExpansion(plugin);
        expansion.register();
        plugin.messageService().info(LogTag.INTEGRATION, "console.papi_registered");
    }

    public static void unregister() {
        if (expansion == null) {
            return;
        }
        expansion.unregister();
        expansion = null;
    }
}
