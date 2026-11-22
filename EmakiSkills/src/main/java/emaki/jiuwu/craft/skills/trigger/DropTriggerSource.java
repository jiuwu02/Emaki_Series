package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.trigger.TriggerDispatcher;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;
import emaki.jiuwu.craft.corelib.trigger.TriggerSource;

public final class DropTriggerSource implements TriggerSource {

    @Override
    public String id() {
        return "drop";
    }

    @Override
    public void register(JavaPlugin plugin, TriggerDispatcher dispatcher) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
            public void onDrop(PlayerDropItemEvent event) {
                dispatch(event);
            }

            private void dispatch(PlayerDropItemEvent event) {
                Player player = event.getPlayer();

                TriggerInvocation invocation = new TriggerInvocation(
                        player,
                        "drop_q",
                        event,
                        player.isSneaking(),
                        false,
                        System.currentTimeMillis()
                );
                dispatcher.dispatch(invocation);

                if (invocation.cancelOriginalAction()) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }
}
