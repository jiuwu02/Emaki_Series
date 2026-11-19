package emaki.jiuwu.craft.dungeon.instance;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class EventFire {

    private EventFire() {
    }

    public static void fire(Event event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
            return;
        }
        EmakiCoreLibApi.requireService(ExecutionDispatcher.class)
                .runGlobal(JavaPlugin.getProvidingPlugin(EventFire.class),
                        () -> Bukkit.getPluginManager().callEvent(event));
    }
}
